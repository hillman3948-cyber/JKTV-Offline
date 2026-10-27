const fs=require('fs'),path=require('path'),assert=require('assert'),{execFileSync}=require('child_process');
const {chromium}=require('C:/Users/hillm/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const root=process.cwd(),out=path.join(root,'audio-stage-validation'),adb='C:/Users/hillm/AppData/Local/Android/Sdk/platform-tools/adb.exe';
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const cases=[],errors=[];
(async()=>{
  let session;
  for(let n=0;n<120;n++){
    try{session=JSON.parse(execFileSync(adb,['-s','emulator-5580','shell','run-as','com.jktv.offline.cast','cat','files/audio-qa-session.json'],{encoding:'utf8',stdio:['ignore','pipe','ignore']}));break;}catch(e){await sleep(500);}
  }
  assert(session,'Android instrumented server did not start');
  execFileSync(adb,['-s','emulator-5580','forward','tcp:18383','tcp:'+session.port]);
  const origin='http://127.0.0.1:18383',query='?pin='+session.pin;
  const frame=async()=>{const r=await fetch(origin+'/api/stage'+query);assert(r.ok);return r.json();};
  const control=async cmd=>fetch(origin+'/api/control'+query+'&cmd='+cmd);
  const check=name=>{cases.push(name);console.log('PASS '+name);};
  const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
  const context=await browser.newContext({viewport:{width:1920,height:1080},userAgent:'Mozilla/5.0 (SMART-TV; Linux; Tizen 7.0) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/4.0 TV Safari/537.36'});
  await context.addInitScript(()=>{
    window.Worker=undefined; // Exercise the compatibility transport; the separate performance suite tests WebSocket.
    const original=AudioContext.prototype.createGain;
    AudioContext.prototype.createGain=function(){const gain=original.call(this),a=this.createAnalyser();a.fftSize=1024;gain.connect(a);window.qaAnalyser=a;return gain;};
  });
  const p1=await context.newPage();p1.on('pageerror',e=>errors.push(e.message));
  const rms=()=>p1.evaluate(()=>{const a=window.qaAnalyser,b=new Float32Array(a.fftSize);a.getFloatTimeDomainData(b);return Math.sqrt(b.reduce((n,v)=>n+v*v,0)/b.length);});
  try{
    await p1.goto(origin+'/'+query);await p1.getByText('Show Karaoke Stage',{exact:true}).click();
    await p1.waitForFunction(()=>window.JktvStage.state().audio.active,{},{timeout:12000});
    await sleep(2000); // Allow initial latency calibration and clock slewing to settle.
    let f=await frame();assert(f.qa.muted);assert(f.qa.nonzero>10);assert(await rms()>.0001);
    check('Real FluidSynth PCM reaches Web Audio while Android output is muted');
    const samples=await p1.evaluate(async()=>{
      const result=[];for(let i=0;i<40;i++){const s=window.JktvStage.state();result.push({position:s.renderPosition,host:s.position,latency:s.audio.latency,queue:s.audio.queued,revision:s.frame.transportRevision});await new Promise(r=>setTimeout(r,20));}return result;
    });
    const maximumDrift=Math.max(...samples.map(s=>Math.abs(s.position-s.host)));
    fs.writeFileSync(path.join(out,'audio-clock-samples.json'),JSON.stringify({maximumDrift,samples},null,2));
    assert(maximumDrift<250,'Audio playout/host display drift: '+maximumDrift);
    assert(samples.every((s,i)=>!i||s.revision!==samples[i-1].revision||s.position>=samples[i-1].position-2),'Wipe playhead moved backwards');
    fs.writeFileSync(path.join(out,'audio-clock-samples.json'),JSON.stringify({maximumDrift,samples},null,2));
    check('Remote lyric playout stays monotonic and within 250 ms of compensated host timeline');
    const t=f.rawPosition;await control('style');
    await p1.waitForFunction(()=>window.JktvStage.state().settings.lyricFontFamily==='SERIF');
    f=await frame();assert(f.qa.muted);assert(f.rawPosition>=t);check('Settings apply during remote audio without restarting playback');
    await control('pause');await p1.waitForFunction(()=>!window.JktvStage.state().frame.playing);await sleep(250);
    const paused=(await frame()).rawPosition;await sleep(350);assert.equal((await frame()).rawPosition,paused);check('Pause preserves the single host timeline');
    await control('play');await p1.waitForFunction(()=>window.JktvStage.state().frame.playing);await sleep(250);
    assert((await frame()).rawPosition>paused);await control('seek');
    await p1.waitForFunction(()=>window.JktvStage.state().frame.rawPosition>=12000);
    assert((await frame()).rawPosition<14000);check('Resume and seek update remote PCM and lyrics without restarting the song');
    const p2=await context.newPage();p2.on('pageerror',e=>errors.push(e.message));await p2.goto(origin+'/'+query);
    await p2.getByText('Show Karaoke Stage',{exact:true}).click();
    await p2.waitForFunction(()=>window.JktvStage.state().audio.active,{},{timeout:12000});
    assert(!(await p1.evaluate(()=>window.JktvStage.state().audio.active)));assert((await frame()).qa.muted);
    check('Second Stage takes exclusive audio ownership; first Stage stays visual-only');
    await p2.close();await sleep(1000);assert(!(await frame()).qa.muted);check('Closing active Stage restores host audio');
    await p1.locator('.stage-audio-control').click();await p1.waitForFunction(()=>window.JktvStage.state().audio.active);
    await context.setOffline(true);await sleep(3700);assert(!(await frame()).qa.muted);check('Network loss expires remote audio and restores host');
    await context.setOffline(false);await p1.waitForFunction(()=>window.JktvStage.state().audio.active,{},{timeout:12000});
    assert((await frame()).qa.muted);assert.equal(await p1.evaluate(()=>window.JktvStage.state().settings.lyricFontFamily),'SERIF');
    check('Reconnect restores latest styles, playback and exclusive audio without page reload');
    await p1.screenshot({path:path.join(out,'android-pcm-stage.png')});
    await p1.close();await sleep(900);
    const unsupported=await browser.newContext();await unsupported.addInitScript(()=>{window.AudioContext=undefined;window.webkitAudioContext=undefined;});
    const p3=await unsupported.newPage();await p3.goto(origin+'/'+query);await p3.getByText('Show Karaoke Stage',{exact:true}).click();await sleep(500);
    assert(!(await frame()).qa.muted);assert(await p3.locator('.stage-audio-control').isVisible());check('Unsupported audio keeps host audible and Stage visible');
    await unsupported.close();
    const fallback=await browser.newContext();await fallback.addInitScript(()=>{window.Worker=undefined;});
    const p4=await fallback.newPage();p4.on('pageerror',e=>errors.push(e.message));await p4.goto(origin+'/'+query);
    await p4.getByText('Show Karaoke Stage',{exact:true}).click();await p4.waitForFunction(()=>window.JktvStage.state().audio.active);
    await sleep(1500);assert((await frame()).qa.muted);assert(!(await p4.evaluate(()=>window.JktvStage.state().performance.socket)));
    check('Browsers without Worker use authenticated HTTP PCM fallback');await fallback.close();
    assert.deepEqual(errors,[]);
    fs.writeFileSync(path.join(out,'device-browser-results.json'),JSON.stringify({passed:cases.length,cases,errors,scope:'Actual Android API 37 emulator JNI/AudioTrack/HTTP fallback and desktop Chrome with Samsung UA. No physical Samsung TV or speaker latency measurement.'},null,2));
  }catch(e){fs.writeFileSync(path.join(out,'device-browser-failure.json'),JSON.stringify({error:String(e),cases,errors,frame:await frame(),stage:await p1.evaluate(()=>window.JktvStage?.state()).catch(()=>null)},null,2));throw e;}
  finally{await control('done').catch(()=>{});await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
