import com.jktv.offline.audio.FluidMidiControls;
import com.jktv.offline.audio.PcmOutputGuard;
import com.jktv.offline.playback.*;
import com.sun.jna.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.sound.midi.*;

/** Desktop real-synthesis check of the production control bridge and parser.
 * Does not exercise Android's loader, AudioTrack, hardware routing or network output.
 */
public class NativeMidiAudioQa {
    static NativeLibrary lib;
    static Pointer settings, synth;
    static FluidMidiControls controls;
    static long handle;
    static int call(String name, Object... values) { return lib.getFunction(name).invokeInt(values); }
    static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static float[] render(int frames) {
        float[] left = new float[frames], right = new float[frames];
        float[] pcm = new float[frames * 2];
        require(call("fluid_synth_write_float", synth, frames, left, 0, 1, right, 0, 1) == 0, "render failed");
        for(int i=0; i<frames; i++) { pcm[2*i]=left[i]; pcm[2*i+1]=right[i]; }
        return pcm;
    }
    static double rms(float[] pcm) {
        double sum = 0; for (float v : pcm) sum += v*v; return Math.sqrt(sum/pcm.length);
    }
    static double frequency(float[] pcm) {
        int crossings = 0;
        for(int i = 2; i < pcm.length; i += 2) if(pcm[i-2] <= 0 && pcm[i] > 0) crossings++;
        return crossings * 44100.0 / (pcm.length/2);
    }
    static int controller(int cc) {
        int[] value = new int[1]; call("fluid_synth_get_cc", synth, 0, cc, value); return value[0];
    }
    static void note(int channel, int key, int velocity) { call("fluid_synth_noteon", synth, channel, key, velocity); }
    static void nativeControls() {
        controls.reset(handle);
        note(0,60,110); render(4096);
        double reference = rms(render(22050)); require(reference > 0.0001, "no reference tone");
        controls.control(handle,0,11,12); render(8192);
        double expressed = rms(render(22050));
        require(expressed < reference * 0.2, "expression not applied");
        controls.control(handle,0,11,127); controls.control(handle,0,7,0); render(8192);
        require(rms(render(22050)) < reference * 0.01, "channel volume mute not applied");
        controls.reset(handle);
        controls.control(handle,0,64,127); note(0,60,110); render(4096);
        call("fluid_synth_noteoff",synth,0,60);
        double sustained = rms(render(22050)); require(sustained > 0.0001, "sustain not applied");
        controls.silence(handle,0); render(4096);
        require(rms(render(22050)) < 0.000001, "held voice survives all-sounds-off");
        controls.reset(handle); note(0,60,110); render(4096);
        double base = frequency(render(44100));
        controls.pitchBend(handle,0,16383); render(4096);
        double bent = frequency(render(44100));
        require(bent/base > 1.10 && bent/base < 1.15, "pitch bend is incorrect: base="+base+", bent="+bent);
        controls.channelPressure(handle,0,75); controls.keyPressure(handle,0,60,80);
        controls.control(handle,0,7,12); controls.reset(handle);
        int[] pitch = new int[1]; call("fluid_synth_get_pitch_bend",synth,0,pitch);
        require(pitch[0] == 8192 && controller(7) == 100 && controller(11) == 127 && controller(64) == 0,
            "reset does not restore song controller defaults");
        System.out.println("PASS native volume, expression, sustain, pitch bend, pressure dispatch, held-note silence and reset");
    }
    static void event(Track track, long tick, int command, int channel, int a, int b) throws Exception {
        ShortMessage m = new ShortMessage(); m.setMessage(command,channel,a,b); track.add(new MidiEvent(m,tick));
    }
    static File arrangement(Path dir, String name, int channels, int voices, int volume) throws Exception {
        Sequence song = new Sequence(Sequence.PPQ,480); Track track = song.createTrack();
        for(int ch=0; ch<channels; ch++) {
            event(track,0,0xb0,ch,7,volume); event(track,0,0xc0,ch,0,0);
            event(track,0,0xb0,ch,64,127);
            for(int key=48; key<48+voices; key++) event(track,0,0x90,ch,key,120);
            event(track,480,0xb0,ch,11,24);
            event(track,960,0xe0,ch,0,96);
            for(int key=48; key<48+voices; key++) event(track,1440,0x80,ch,key,0);
            event(track,1920,0xb0,ch,64,0);
            event(track,2400,0xb0,ch,120,0);
        }
        File file = dir.resolve(name+".mid").toFile(); MidiSystem.write(song,1,file); return file;
    }
    static String arrangementCheck(File file, Path out) throws Exception {
        MidiTimeline timeline = KaraokeMidiParser.INSTANCE.parse(file);
        int newEvents = 0;
        for(SequencerMidiEvent e : timeline.getMidiEvents())
            if(e.getType() == MidiEventType.CONTROL_CHANGE || e.getType() == MidiEventType.PITCH_BEND) newEvents++;
        require(newEvents > 0, "fixture did not exercise restored MIDI instructions");
        double[] peak = new double[2]; long[] overload = new long[2];
        for(int mode=0; mode<2; mode++) {
            controls.reset(handle);
            lib.getFunction("fluid_synth_set_gain").invokeVoid(new Object[]{synth,mode==0 ? 0.85f : 0.17f});
            PcmOutputGuard guard = new PcmOutputGuard();
            int cursor=0; ByteArrayOutputStream wav = new ByteArrayOutputStream();
            List<SequencerMidiEvent> events=timeline.getMidiEvents();
            for(int frame=0; frame<3*44100; frame+=256) {
                long us=frame*1000000L/44100;
                while(cursor<events.size() && events.get(cursor).getTimeUs()<=us) {
                    SequencerMidiEvent e=events.get(cursor++); int ch=e.getChannel();
                    switch(e.getType()) {
                        case NOTE_ON: note(ch,e.getData1(),e.getData2()); break;
                        case NOTE_OFF: call("fluid_synth_noteoff",synth,ch,e.getData1()); break;
                        case PROGRAM_CHANGE: call("fluid_synth_program_change",synth,ch,e.getData1()); break;
                        case CONTROL_CHANGE: if(mode==1) controls.control(handle,ch,e.getData1(),e.getData2()); break;
                        case PITCH_BEND: if(mode==1) controls.pitchBend(handle,ch,e.getData1()|(e.getData2()<<7)); break;
                        default: break;
                    }
                }
                float[] pcm=render(256);
                for(float v:pcm) { peak[mode]=Math.max(peak[mode],Math.abs(v)); if(Math.abs(v)>1) overload[mode]++; }
                if(mode==1) pcm=guard.process(pcm,1L);
                for(float v:pcm) {
                    require(Float.isFinite(v),"non-finite output");
                    if(mode==1) require(Math.abs(v)<=PcmOutputGuard.CEILING+0.000001,"unsafe output");
                    int n=Math.round(Math.max(-1,Math.min(1,v))*32767); wav.write(n&255); wav.write((n>>8)&255);
                }
            }
            byte[] pcm=wav.toByteArray();
            try(DataOutputStream w=new DataOutputStream(Files.newOutputStream(out.resolve(file.getName()+(mode==0?"-before.wav":"-after.wav"))))) {
                w.writeBytes("RIFF"); w.writeInt(Integer.reverseBytes(pcm.length+36)); w.writeBytes("WAVEfmt ");
                w.writeInt(Integer.reverseBytes(16)); w.writeShort(Short.reverseBytes((short)1)); w.writeShort(Short.reverseBytes((short)2));
                w.writeInt(Integer.reverseBytes(44100)); w.writeInt(Integer.reverseBytes(44100*4));
                w.writeShort(Short.reverseBytes((short)4)); w.writeShort(Short.reverseBytes((short)16));
                w.writeBytes("data"); w.writeInt(Integer.reverseBytes(pcm.length)); w.write(pcm);
            }
        }
        System.out.println("PASS "+file.getName()+" original peak="+peak[0]+" corrected raw peak="+peak[1]);
        return String.format(Locale.ROOT,"{\"song\":\"%s\",\"restoredControlEvents\":%d,\"oldRawPeak\":%.6f,\"newRawPeak\":%.6f,\"oldOverloadSamples\":%d,\"newOverloadSamplesBeforeGuard\":%d}",file.getName(),newEvents,peak[0],peak[1],overload[0],overload[1]);
    }
    public static void main(String[] args) throws Exception {
        lib=NativeLibrary.getInstance(args[0]); controls=new FluidMidiControls(lib);
        settings=lib.getFunction("new_fluid_settings").invokePointer(new Object[]{});
        call("fluid_settings_setnum",settings,"synth.sample-rate",44100.0);
        call("fluid_settings_setint",settings,"synth.reverb.active",0);
        call("fluid_settings_setint",settings,"synth.chorus.active",0);
        synth=lib.getFunction("new_fluid_synth").invokePointer(new Object[]{settings});
        require(synth!=null,"no synth"); handle=Pointer.nativeValue(synth);
        try {
            require(call("fluid_synth_sfload",synth,args[1],1)>=0,"SoundFont load failed");
            nativeControls();
            Path out=Paths.get(args[2]); Files.createDirectories(out);
            List<String> results=new ArrayList<>();
            results.add(arrangementCheck(arrangement(out,"sparse-voice",1,1,80),out));
            results.add(arrangementCheck(arrangement(out,"sustained-chord",1,8,70),out));
            results.add(arrangementCheck(arrangement(out,"dense-arrangement",3,24,60),out));
            Files.writeString(out.resolve("native-midi-results.json"),"{\"scope\":\"Desktop FluidSynth 2.5.2 with production parser/control bridge and procedural sine SF2; not Android hardware verification\",\"nativeControlAssertions\":\"passed\",\"songs\":["+String.join(",",results)+"]}");
        } finally {
            lib.getFunction("delete_fluid_synth").invokeVoid(new Object[]{synth});
            lib.getFunction("delete_fluid_settings").invokeVoid(new Object[]{settings});
        }
    }
}
