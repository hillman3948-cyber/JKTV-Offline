/* Real Android engine/server with three guest browser contexts and a bounded-jitter TCP proxy. */
const fs=require('fs'),path=require('path'),assert=require('assert'),net=require('net'),{execFileSync}=require('child_process');
const {chromium}=require('C:/Users/hillm/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const root=process.cwd(),out=path.join(root,'performance-validation'),adb='C:/Users/hillm/AppData/Local/Android/Sdk/platform-tools/adb.exe';
const adbAsync=(...args)=>new Promise((resolve,reject)=>require('child_process').execFile(adb,['-s','emulator-5580',...args],{encoding:'utf8'},(error,out)=>error?reject(error):resolve(out)));
const nativeFetch=globalThis.fetch;globalThis.fetch=(url,options={})=>nativeFetch(url,{signal:AbortSignal.timeout(8000),...options});
const sleep=ms=>new Promise(r=>setTimeout(r,ms)),cases=[],errors=[],samples=[];
const shell=(...args)=>execFileSync(adb,['-s','emulator-5580',...args],{encoding:'utf8',stdio:['ignore','pipe','ignore']});
let jitter=false,holdMs=180,forceDrop=false;const connections=new Set();
const proxy=net.createServer(client=>{
  const host=net.connect(18383,'127.0.0.1');let ws=false,count=0,timer=0,queue=[];
  const pair={client,host};connections.add(pair);
  client.on('data',b=>{if(b.toString().includes('GET /stage-stream'))ws=true;if(!forceDrop)host.write(b);});
  host.on('data',b=>{
    if(forceDrop)return;
    if(ws&&(timer||(jitter&&++count%13===0))){queue.push(b);if(!timer)timer=setTimeout(()=>{timer=0;if(!client.destroyed)client.write(Buffer.concat(queue));queue=[];},holdMs);}
    else client.write(b);
  });
  const close=()=>{clearTimeout(timer);connections.delete(pair);client.destroy();host.destroy();};
  client.on('close',close);host.on('close',close);client.on('error',close);host.on('error',close);
});
(async()=>{
  let session;
  for(let i=0;i<150;i++){try{session=JSON.parse(shell('shell','run-as','com.jktv.offline.cast','cat','files/audio-qa-session.json'));break;}catch(e){await sleep(500);}}
  assert(session,'Instrumented host did not start');shell('forward','tcp:18383','tcp:'+session.port);
  await new Promise(r=>proxy.listen(18484,'127.0.0.1',r));
  const origin='http://127.0.0.1:18484',direct='http://127.0.0.1:18383',query='?pin='+session.pin;
  const frame=async()=>{const r=await fetch(direct+'/api/stage'+query);return r.json();};
  const cmd=async action=>fetch(direct+'/api/control'+query+'&cmd='+action+'&requestId='+Date.now()+Math.random());
  const check=name=>{cases.push(name);console.log('PASS '+name);};
  const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
  const context=await browser.newContext({viewport:{width:1920,height:1080}});
  await context.addInitScript(()=>{const original=AudioContext.prototype.createGain;AudioContext.prototype.createGain=function(){const g=original.call(this),a=this.createAnalyser();a.fftSize=1024;g.connect(a);window.qaAnalyser=a;return g;};});
  const stage=await context.newPage(),requests=[];
  stage.on('pageerror',e=>errors.push(e.message));stage.on('request',r=>requests.push({url:r.url(),at:Date.now()}));
  const state=()=>stage.evaluate(()=>window.JktvStage.state());
  const rms=()=>stage.evaluate(()=>{const a=window.qaAnalyser,d=new Float32Array(a.fftSize);a.getFloatTimeDomainData(d);return Math.sqrt(d.reduce((n,v)=>n+v*v,0)/d.length);});
  const guests=[];
  try {
    await stage.goto(origin+'/'+query);await stage.getByText('Show Karaoke Stage',{exact:true}).click();
    await stage.waitForFunction(()=>window.JktvStage.state().audio.active&&window.JktvStage.state().performance.socket,null,{timeout:15000});await sleep(1200);
    assert((await frame()).qa.muted);assert(await rms()>.0001);check('Persistent Worker WebSocket carries actual synthesized music exclusively to Stage');
    const guestUrl=(await state()).settings.stageGuestJoinUrl,guestQuery=new URL(guestUrl).search;
    assert(guestUrl.includes('view=remote')&&!guestUrl.includes('pin='));
    await stage.locator('#guestQrImage').waitFor({state:'visible'});await stage.waitForFunction(()=>document.querySelector('#guestQrImage').naturalWidth>0);
    const qr=await stage.locator('#guestQr').boundingBox(),lyrics=await stage.locator('#lyrics').boundingBox(),title=await stage.locator('#stageTitle').boundingBox();
    assert(qr.y+qr.height<=lyrics.y);assert(title.x+title.width<qr.x);await stage.screenshot({path:path.join(out,'tv-guest-qr.png')});
    check('Compact offline QR occupies a separate top-right area and encodes a guest remote link');
    for(let i=0;i<3;i++){
      const c=await browser.newContext({viewport:{width:390,height:844}}),p=await c.newPage();guests.push({context:c,page:p});
      p.on('pageerror',e=>errors.push(e.message));await p.goto(origin+'/'+guestQuery);
      await p.waitForFunction(()=>window.JktvStage.state().mode==='remote');
      assert(await p.locator('#tvStage').isHidden());assert.equal(await p.evaluate(()=>window.JKTV_PAIRING_PIN),'');
      await p.locator('#singerInput').fill('QA Guest '+i);await p.locator('#searchBox').fill('Song '+(100+i));
      await p.waitForFunction(()=>document.querySelectorAll('#resultsList .song').length>0);
      await p.locator('#resultsList button').first().click();
    }
    await sleep(500);assert((await frame()).qa.reservations>=3);assert((await state()).audio.active);
    assert.equal((await fetch(direct+'/api/stage-audio'+guestQuery+'&action=claim&client=guest_bad')).status,403);
    check('Three guest phones search a 50,000-song Room catalog and queue songs without taking audio ownership');
    const beforeDup=(await frame()).qa.reservations,id='dedupe-'+Date.now();
    await Promise.all([1,2].map(()=>fetch(direct+'/api/reserve'+guestQuery+'&songId=200&singer=Duplicate&requestId='+id)));
    assert.equal((await frame()).qa.reservations,beforeDup+1);check('Repeated reservation request IDs execute once');
    const voiceBefore=(await state()).audio.stats;
    await cmd('qr_off');await stage.waitForFunction(()=>document.querySelector('#guestQr').hidden);
    await cmd('qr_on');await stage.waitForFunction(()=>!document.querySelector('#guestQr').hidden);
    await cmd('style');await stage.waitForFunction(()=>window.JktvStage.state().settings.lyricFontFamily==='SERIF');
    assert.equal((await state()).audio.stats.transportFlushes,voiceBefore.transportFlushes);check('QR Settings and style changes preserve audio buffers and fullscreen Stage');
    await cmd('slow_frames');const beforeSlow=(await state()).audio.stats;await sleep(3000);await cmd('fast_frames');
    assert.equal((await state()).audio.stats.underruns,beforeSlow.underruns);check('450 ms Stage snapshot delays do not block the audio stream');
    const trafficAt=Date.now();await sleep(2500);
    assert.equal(requests.filter(r=>r.at>trafficAt&&/\/api\/(status|search)|\/stage-audio-pcm/.test(r.url)).length,0);
    check('TV Stage sends no background remote search/status polls or repeated audio HTTP requests');
    jitter=true;await sleep(5000);jitter=false;
    const jitterStats=(await state()).audio.stats;assert.equal(jitterStats.missingBlocks,0);assert.equal(jitterStats.underruns,beforeSlow.underruns);check('Repeated 180 ms TCP delivery stalls preserve all PCM samples without underruns');
    await adbAsync('shell','input','keyevent','223');await sleep(4000);assert((await frame()).playing&&((await state()).audio.active));
    const services=await adbAsync('shell','dumpsys','activity','services','com.jktv.offline.cast');assert(services.includes('PlaybackKeepAliveService'));await adbAsync('shell','input','keyevent','224');
    check('Foreground playback and Wi-Fi session remain alive with the emulated phone screen locked');
    const cdp=await context.newCDPSession(stage);await cdp.send('HeapProfiler.collectGarbage');const heapStart=await cdp.send('Runtime.getHeapUsage');
    const nativeStart=await adbAsync('shell','dumpsys','meminfo','com.jktv.offline.cast');fs.writeFileSync(path.join(out,'native-memory-start.txt'),nativeStart);
    const seconds=Number(process.env.JKTV_SOAK_SECONDS||120),started=Date.now(),beforeSoak=(await state()).audio.stats;
    let lastProgress=0,searches=0;
    while(Date.now()-started<seconds*1000){
      for(let i=0;i<guests.length;i++)await guests[i].page.locator('#searchBox').fill('Song '+(100+(searches+i)%900));searches+=3;
      if(searches%30===0)await stage.evaluate(()=>{const end=performance.now()+80;while(performance.now()<end){Math.sqrt(Math.random());}});
      const s=await state();samples.push({elapsed:(Date.now()-started)/1000,audio:s.audio,performance:s.performance,position:s.renderPosition,host:s.position});
      assert(s.audio.active);assert(s.audio.sources<50&&s.audio.segments<600);assert(s.audio.queued<1.5);
      if(Date.now()-lastProgress>30000){lastProgress=Date.now();console.log('SOAK '+Math.round((Date.now()-started)/1000)+'/'+seconds+' seconds; underruns='+s.audio.stats.underruns+', buffers='+s.audio.sources);}
      await sleep(1000);
    }
    const afterSoak=(await state()).audio.stats;assert.equal(afterSoak.missingBlocks,beforeSoak.missingBlocks);assert.equal(afterSoak.underruns,beforeSoak.underruns);
    await cdp.send('HeapProfiler.collectGarbage');const heapEnd=await cdp.send('Runtime.getHeapUsage');assert(heapEnd.usedSize-heapStart.usedSize<20*1024*1024);
    fs.writeFileSync(path.join(out,'native-memory-end.txt'),await adbAsync('shell','dumpsys','meminfo','com.jktv.offline.cast'));
    check(seconds+' seconds of real playback with guest searches, visualizer and bounded memory');
    forceDrop=true;for(const pair of [...connections]){pair.client.destroy();pair.host.destroy();}await sleep(3700);assert(!(await frame()).qa.muted);
    forceDrop=false;await stage.waitForFunction(()=>window.JktvStage.state().audio.active&&window.JktvStage.state().performance.socket,null,{timeout:15000});
    assert((await frame()).qa.muted);assert.equal((await state()).mode,'stage');check('A longer interruption restores host audio, then reconnects Stage without re-pairing or song restart');
    await stage.evaluate(()=>window.JktvStage.selectMode('remote'));await sleep(800);assert(!(await frame()).qa.muted);check('Leaving Stage releases audio and restores the host');
    assert.deepEqual(errors,[]);
    fs.writeFileSync(path.join(out,'performance-results.json'),JSON.stringify({passed:cases.length,cases,errors,heapStart,heapEnd,samples,httpRequests:requests.length,scope:'Android emulator, actual synth/server/Room database, desktop Chrome, three independent guest contexts, controlled TCP jitter. Not physical Samsung hardware.'},null,2));
  }catch(error){fs.writeFileSync(path.join(out,'performance-failure.json'),JSON.stringify({error:String(error),cases,errors,samples,state:await state().catch(()=>null)},null,2));await stage.screenshot({path:path.join(out,'performance-failure.png')}).catch(()=>{});throw error;}
  finally {await cmd('done').catch(()=>{});await browser.close();for(const pair of [...connections]){pair.client.destroy();pair.host.destroy();}proxy.close();}
})().catch(e=>{console.error(e);proxy.close();process.exitCode=1;});
