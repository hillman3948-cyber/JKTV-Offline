/* Authenticated PCM from the host's single synth. No MIDI player or independent transport here. */
(function(){
  'use strict';
  class StageAudio {
    constructor(pin,stage,link){
      this.link=link;this.stats={buffers:0,underruns:0,missingBlocks:0,transportFlushes:0};this.target=.32;
      if(link){link.onpcm=blocks=>{this.enqueue(blocks);this.prepare();this.updateLink();};link.onaudio=(a,start,end)=>this.audioReply(a,start,end);}
      this.query='?pin='+encodeURIComponent(pin);this.client='stage_'+Math.random().toString(36).slice(2)+Date.now().toString(36);
      this.generation=0;this.sources=[];this.segments=[];this.after=0;this.latency=160;this.active=false;this.wanted=false;
      this.button=document.createElement('button');this.button.className='stage-audio-control';this.button.hidden=true;
      this.button.textContent='Play sound on this screen';this.button.onclick=()=>{this.stop();this.enable(true);};stage.append(this.button);
    }
    state(){return {active:this.active,status:this.status||'host',latency:this.latency,queued:this.context?Math.max(0,this.next-this.context.currentTime):0,sources:this.sources.length,segments:this.segments.length,stats:this.stats,target:this.target};}
    frame(f){
      if(this.session&&this.session!==f.session)this.acceptedRevision=undefined;
      this.latest=f;this.session=f.session;this.alignRevision(f.transportRevision);
    }
    alignRevision(revision){
      if(this.acceptedRevision!==undefined&&revision<this.acceptedRevision)return false;
      if(this.acceptedRevision!==undefined&&revision>this.acceptedRevision){this.flush();this.lastRender=undefined;this.stats.transportFlushes++;}
      this.acceptedRevision=revision;return true;
    }
    flush(){this.sources.forEach(s=>{try{s.stop();s.disconnect();}catch(e){}});this.sources=[];this.segments=[];this.next=0;}
    updateLink(){if(this.link)this.link.update({token:this.token||'',after:this.after,latency:Math.round(this.latency),queued:this.context?Math.max(0,this.next-this.context.currentTime):0});}
    audioReply(reply,start,end){
      if(!this.wanted)return;
      if(reply.state==='active')this.lease(reply.leaseUntil,reply.serverTime,start,end);
      else if(reply.state==='revoked')this.fail('Another screen is playing sound','revoked');
      else if(reply.state==='expired')this.reconnect();
      this.serverSample={time:reply.serverTime,at:end};
    }
    reconnect(){
      this.stop();this.status='reconnecting';const generation=this.generation;
      this.retryTimer=setTimeout(()=>{if(generation===this.generation)this.enable(false);},500);
    }
    async prepare(){
      if(this.active||this.committing||!this.wanted||!this.sources.length||performance.now()<(this.retryCommit||0))return;
      this.committing=true;const generation=this.generation,start=performance.now();
      try{
        const reply=await this.request('commit');if(generation!==this.generation)return;
        if(reply.state==='active')this.lease(reply.leaseUntil,reply.serverTime,start,performance.now());
        else if(reply.state==='expired')this.reconnect();
        else if(reply.state==='revoked')this.fail('Another screen is playing sound','revoked');
        else this.retryCommit=performance.now()+150;
      }catch(e){this.retryCommit=performance.now()+250;}finally{this.committing=false;}
    }
    async request(action,extra){
      const q=this.query+'&action='+action+'&client='+this.client+'&token='+encodeURIComponent(this.token||'')+'&latency='+Math.round(this.latency)+(extra||'');
      const response=await this.fetch('/api/stage-audio'+q);if(!response.ok)throw Error('Audio connection unavailable');return response.json();
    }
    fetch(url){return window.JktvFetch(url,{cache:'no-store'},1100);}
    enable(gesture){
      if(this.wanted)return;
      if(this.status==='revoked'&&!gesture)return;
      this.wanted=true;const generation=++this.generation;
      const Audio=window.AudioContext||window.webkitAudioContext;
      if(!Audio){this.fail('Audio unavailable here — sound stays on phone');return;}
      try{
        if(!this.context){this.context=new Audio({sampleRate:44100});this.gain=this.context.createGain();this.gain.gain.value=0;this.gain.connect(this.context.destination);}
        // Called in the Stage button's gesture, before any network awaits.
        const resume=this.context.resume();
        Promise.race([Promise.resolve(resume),new Promise((_,reject)=>setTimeout(()=>reject(Error('Tap to enable sound on this screen')),1800))]).then(async()=>{
          if(generation!==this.generation)return;
          if(this.context.state!=='running')throw Error('Tap to enable sound on this screen');
          const reply=await this.request('claim','&gesture='+(gesture?'1':'0'));
          if(generation!==this.generation)return;
          if(reply.state==='revoked'){this.fail('Another screen is playing sound','revoked');return;}
          this.token=reply.token;this.after=0;this.flush();this.updateLink();this.status=reply.state;this.button.hidden=true;
          if(reply.state==='active')this.lease(reply.leaseUntil,reply.serverTime,performance.now(),performance.now());
          this.loop(generation);
        }).catch(e=>{if(generation===this.generation)this.fail(e.message);});
      }catch(e){this.fail('Tap to enable sound on this screen');}
    }
    lease(until,server,start,end){
      // Silence before the server restores the host, even if networking or the JS event loop stalls.
      const seconds=Math.max(0,(until-server-(end-start)-350)/1000),now=this.context.currentTime;
      this.deadline=now+seconds;this.gain.gain.cancelScheduledValues(now);this.gain.gain.setValueAtTime(seconds>0?1:0,now);
      if(seconds>0){this.gain.gain.setValueAtTime(1,Math.max(now,this.deadline-.012));this.gain.gain.linearRampToValueAtTime(0,this.deadline);}
      this.active=seconds>0;this.status='active';this.button.hidden=true;
    }
    packet(bytes){this.enqueue(window.JktvPcm.decode(bytes));}
    enqueue(blocks){
      if(!this.context||!this.wanted)return;
      const pending=[];
      blocks.forEach(b=>{
        if(b.seq<=this.after)return;
        if(this.after&&b.seq>this.after+1)this.stats.missingBlocks+=b.seq-this.after-1;
        this.after=b.seq;if(!this.alignRevision(b.revision))return;
        if(pending.length&&pending[pending.length-1].revision!==b.revision)pending.length=0;
        pending.push(b);
      });
      if(!pending.length)return;
      const ctx=this.context,now=ctx.currentTime;
      let start=0;
      while(start<pending.length){
        let end=start+1;
        while(end<pending.length&&pending[end].revision===pending[start].revision&&pending[end].playing===pending[start].playing&&pending[end].tempo===pending[start].tempo)end++;
        const group=pending.slice(start,end),frames=group.reduce((n,b)=>n+b.frames,0),duration=frames/44100;
        if(!this.next||this.next<now+.008){
          if(this.next&&this.active){this.stats.underruns++;this.target=Math.min(.8,this.target+.08);}
          this.next=now+Math.max(.04,this.target-duration);
        }
        // Tiny clock-rate correction preserves every sample; ordinary queue growth never flushes sound.
        const rate=Math.max(.998,Math.min(1.002,1+(this.next-now-this.target)*.004));
        const buffer=ctx.createBuffer(2,frames,44100),left=buffer.getChannelData(0),right=buffer.getChannelData(1);
        let offset=0;
        group.forEach(b=>{
          left.set(b.left,offset);right.set(b.right,offset);
          const at=this.next+offset/44100/rate,previous=this.segments[this.segments.length-1];
          let position=b.position;
          if(previous&&b.playing&&previous.playing&&Math.abs(previous.end-at)<.001){
            const expected=previous.position+(previous.end-previous.at)*1000*previous.tempo;
            if(Math.abs(position-expected)<50)position=expected+Math.max(-.4,Math.min(.4,position-expected));
          }
          this.segments.push({at,end:at+b.frames/44100/rate,position,tempo:b.tempo*rate,playing:b.playing,capture:b.capture});
          offset+=b.frames;
        });
        const source=ctx.createBufferSource();source.buffer=buffer;source.playbackRate.value=rate;source.connect(this.gain);source.start(this.next);
        this.sources.push(source);this.stats.buffers++;source.onended=()=>{const i=this.sources.indexOf(source);if(i>=0)this.sources.splice(i,1);source.disconnect();};
        this.next+=duration/rate;start=end;
      }
      this.segments=this.segments.filter(s=>s.end>now-1);
      const last=this.segments[this.segments.length-1];
      if(last&&this.serverSample){
        const delay=this.serverSample.time+performance.now()-this.serverSample.at-last.capture+(last.at-now)*1000+this.outputLatency()*1000;
        this.latency=Math.max(60,Math.min(2000,this.latency*.8+delay*.2));
      }
    }
    async loop(generation){
      while(this.wanted&&generation===this.generation){
        try{
          if(this.context.state!=='running'){this.fail('Tap to enable sound on this screen');return;}
          this.updateLink();
          if(this.link&&this.link.connected){await new Promise(r=>setTimeout(r,80));continue;}
          const start=performance.now(),r=await this.fetch('/stage-audio-pcm'+this.query+'&client='+this.client+'&token='+this.token+'&after='+(this.next-this.context.currentTime>1.2?Number.MAX_SAFE_INTEGER:this.after)+'&latency='+Math.round(this.latency));
          if(generation!==this.generation)return;
          if(!r.ok){const reply=await r.json();this.stop();if(reply.state==='revoked')this.fail('Another screen is playing sound','revoked');else this.reconnect();return;}
          const bytes=await r.arrayBuffer(),end=performance.now();if(generation!==this.generation)return;
          const server=Number(r.headers.get('X-JKTV-Server-Time'));
          this.serverSample={time:server,at:end};this.packet(bytes);
          this.audioReply({state:r.headers.get('X-JKTV-Audio-State'),leaseUntil:Number(r.headers.get('X-JKTV-Lease-Until')),serverTime:server},start,end);
          await this.prepare();this.updateLink();
          if(this.active)this.button.hidden=true;
        }catch(e){
          if(generation!==this.generation)return;
          this.status='reconnecting';this.button.hidden=false;this.button.textContent='Reconnecting audio… tap to retry';
          if(this.context.currentTime>this.deadline){this.active=false;}
        }
        await new Promise(r=>setTimeout(r,50));
      }
    }
    outputLatency(){return this.context.outputLatency||this.context.baseLatency||.04;}
    position(frame,fallback){
      if(!frame.playing||!this.active||!this.context||this.context.currentTime>=this.deadline){this.lastRender=undefined;return fallback;}
      const time=this.context.currentTime-this.outputLatency();
      const segment=this.segments.find(s=>time>=s.at&&time<s.end);
      let target;
      if(segment)target=segment.position+(segment.playing?(time-segment.at)*1000*segment.tempo:0)+(frame.lyricOffset||0);
      else {
        // A buffer underrun pauses audible content. Hold its lyric endpoint instead of alternating clocks.
        const previous=this.segments.filter(s=>s.end<=time).pop();
        target=previous?previous.position+(previous.playing?(previous.end-previous.at)*1000*previous.tempo:0)+(frame.lyricOffset||0):this.lastRender===undefined?fallback:this.lastRender;
      }
      target=Math.max(0,Math.min(frame.duration,target));
      this.lastRender=this.lastRender===undefined?target:Math.max(this.lastRender,target);
      return this.lastRender;
    }
    fail(message,status){this.stop();this.status=status||'host';this.button.textContent=message;this.button.hidden=false;}
    stop(){
      clearTimeout(this.retryTimer);this.retryCommit=0;this.committing=false;
      this.wanted=false;this.generation++;this.active=false;this.lastRender=undefined;
      if(this.gain){const now=this.context.currentTime;this.gain.gain.cancelScheduledValues(now);this.gain.gain.setValueAtTime(0,now);}
      this.flush();
      if(this.token){
        const url='/api/stage-audio'+this.query+'&action=release&client='+this.client+'&token='+this.token;
        fetch(url,{method:'POST',keepalive:true}).catch(()=>{});this.token='';
      }
      this.updateLink();
    }
  }
  window.JktvStageAudio=StageAudio;
})();
