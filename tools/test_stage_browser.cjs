/* Browser acceptance matrix against the canonical Kotlin-produced host snapshots. */
const fs=require('fs'),path=require('path'),http=require('http'),assert=require('assert');
const {performance}=require('perf_hooks');
const {chromium}=require('C:/Users/hillm/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const root=path.resolve(__dirname,'..'),out=path.join(root,'stage-sync-validation');
const fixtures=path.join(process.env.TEMP,'jktv-stage-fixtures');
const base=JSON.parse(fs.readFileSync(path.join(fixtures,'host.json'),'utf8'));
const cases=JSON.parse(fs.readFileSync(path.join(fixtures,'settings-cases.json'),'utf8'));
const source=fs.readFileSync(path.join(root,'app/src/main/java/com/jktv/offline/remote/WifiRemoteServer.kt'),'utf8');
const match=source.match(/private fun buildWebRemoteHtml[\s\S]*?= """([\s\S]*?)""".trimIndent\(\)/);
assert(match,'Phone HTML template not found');
const html=match[1].replaceAll("$",'$').replaceAll('$defaultPin','1234').replaceAll('$initialView','').replaceAll('$guestKey','');
let latest=structuredClone(base),version=1,online=true,videoBuffer=Buffer.alloc(0),mediaRequests=0,tickRequests=0;
const clone=()=>JSON.parse(JSON.stringify(latest));
const screenshotFiles=[],results=[],errors=[];
fs.mkdirSync(out,{recursive:true});
const server=http.createServer((req,res)=>{
  const u=new URL(req.url,'http://localhost');
  if(u.pathname==='/'){res.setHeader('Content-Type','text/html');return res.end(html);}
  if(['/stage-client.js','/stage-audio.js','/stage-compat.js','/stage-remote.js','/stage-link.js','/stage-worker.js','/stage-pcm.js','/stage.css'].includes(u.pathname)){
    res.setHeader('Content-Type',u.pathname.endsWith('js')?'application/javascript':'text/css');
    return res.end(fs.readFileSync(path.join(root,'app/src/main/assets/stage',u.pathname.slice(1))));
  }
  if(u.pathname==='/stage-font'){
    const family=u.searchParams.get('family'),font=family==='SERIF'?'georgia.ttf':family==='MONOSPACE'?'cour.ttf':'arial.ttf';
    res.setHeader('Content-Type','font/ttf');return res.end(fs.readFileSync('C:/Windows/Fonts/'+font));
  }
  if(u.pathname==='/api/stage-audio'){res.setHeader('Content-Type','application/json');return res.end(JSON.stringify({state:'revoked'}));}
  if(u.pathname==='/api/stage'){
    tickRequests++;
    if(!online){res.statusCode=503;return res.end('{}');}
    const f=clone();f.settingsRevision='case-'+version;f.sentTime=performance.now();f.sampleTime=f.sentTime-.3;
    if(u.searchParams.get('settingsRevision')===f.settingsRevision)delete f.settings;
    if(u.searchParams.get('revision')===f.revision)delete f.lyrics;
    res.setHeader('Content-Type','application/json');return res.end(JSON.stringify(f));
  }
  if(u.pathname==='/stage-media'){
    mediaRequests++;
    let buffer,mime;
    if(latest.settings.backgroundIsImage){
      buffer=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a5xkAAAAASUVORK5CYII=','base64');mime='image/png';
    }else{buffer=videoBuffer;mime='video/webm';}
    const range=req.headers.range?.match(/bytes=(\d+)-(\d*)/);
    const start=range?Number(range[1]):0,end=range?Math.min(Number(range[2])||buffer.length-1,buffer.length-1):buffer.length-1;
    res.statusCode=range?206:200;res.setHeader('Content-Type',mime);res.setHeader('Accept-Ranges','bytes');
    res.setHeader('Content-Length',end-start+1);if(range)res.setHeader('Content-Range','bytes '+start+'-'+end+'/'+buffer.length);
    return res.end(buffer.subarray(start,end+1));
  }
  if(u.pathname==='/api/status'){res.setHeader('Content-Type','application/json');return res.end(JSON.stringify({ok:true,queue:[]}));}
  if(u.pathname==='/api/search'){res.setHeader('Content-Type','application/json');return res.end(JSON.stringify({ok:true,songs:[]}));}
  res.statusCode=404;res.end();
});
(async()=>{
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const origin='http://127.0.0.1:'+server.address().port;
  const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
  const page=await browser.newPage({viewport:{width:1920,height:1080}});
  page.on('pageerror',e=>errors.push(e.message));
  try{
    videoBuffer=Buffer.from(await page.evaluate(async()=>{
      const c=document.createElement('canvas');c.width=320;c.height=180;
      const ctx=c.getContext('2d');ctx.fillStyle='#102543';ctx.fillRect(0,0,320,180);
      const stream=c.captureStream(0),track=stream.getVideoTracks()[0],recorder=new MediaRecorder(stream,{mimeType:'video/webm;codecs=vp8'});
      const chunks=[];recorder.ondataavailable=e=>chunks.push(e.data);
      const done=new Promise(resolve=>recorder.onstop=async()=>resolve(Array.from(new Uint8Array(await new Blob(chunks,{type:'video/webm'}).arrayBuffer()))));
      const started=new Promise(resolve=>recorder.onstart=resolve);recorder.start(100);await started;
      for(let i=0;i<12;i++){ctx.fillStyle=i%2?'#102543':'#123347';ctx.fillRect(0,0,320,180);track.requestFrame();await new Promise(r=>setTimeout(r,100));}
      recorder.stop();return done;
    }));
    assert(videoBuffer.length>300 && videoBuffer.subarray(0,4).toString('hex')==='1a45dfa3','Invalid generated WebM fixture');
    fs.writeFileSync(path.join(out,'background-fixture.webm'),videoBuffer);
    await page.goto(origin+'/?pin=1234');
    await page.getByText('Show Karaoke Stage',{exact:true}).click();
    await page.waitForFunction(()=>window.JktvStage?.state().settings!==null);
    await page.evaluate(()=>document.fonts.ready);
    const checkpoint=async(name)=>{results.push(name);};
    await checkpoint('Chooser opens Stage with host settings');
    const apply=async(next)=>{
      latest=structuredClone(next);version++;
      await page.waitForFunction(v=>window.JktvStage.state().settingsRevision==='case-'+v,version);
      await page.evaluate(()=>document.fonts.ready);
      await page.waitForFunction(()=>document.querySelector('#stageCanvas').dataset.style===window.JktvStage.state().settings.visualizerStyle);
      await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
    };
    for(const item of cases){
      await apply(item.frame);
      const state=await page.evaluate(()=>window.JktvStage.state());
      assert.deepStrictEqual(state.settings,item.frame.settings,item.name+' differs from host');
      assert.strictEqual(state.mode,'stage');
      assert(await page.locator('#remoteView').isHidden());
      if(item.name==='lyricsEnabled')assert.strictEqual(await page.locator('#lyrics').isHidden(),!item.frame.settings.lyricsEnabled);
      if(item.name==='songInfoEnabled')assert.strictEqual(await page.locator('#stageTitle').isHidden(),!item.frame.settings.songInfoEnabled);
      if(item.name==='singerInfoEnabled')assert.strictEqual(await page.locator('#stageSinger').isHidden(),!item.frame.settings.singerInfoEnabled);
      if(item.name==='countdownEnabled')assert.strictEqual(await page.locator('#countdown').isHidden(),!item.frame.settings.countdownEnabled);
      if(item.name==='lyricWipeEnabled')assert.strictEqual(await page.locator('canvas.lyric-line').evaluateAll(items=>items.every(r=>JSON.parse(r.dataset.wipes||'[]').every(w=>w===0))),true);
      if(item.name==='lyricOutlineThickness')assert.strictEqual(Number(await page.locator('canvas.lyric-line').first().getAttribute('data-outline')),item.frame.settings.lyricOutlineThickness*2);
      if(item.name==='lyricShadowIntensity')assert.strictEqual(await page.locator('.lyric-line').first().evaluate(n=>n.style.filter),'');
      if(item.name==='lyricLetterSpacing')assert.strictEqual(await page.locator('canvas.lyric-line').first().evaluate(n=>n.style.letterSpacing),'4px');
      if(item.name==='lyricLineSpacing')assert.strictEqual(await page.locator('#lyrics').evaluate(n=>n.style.gap),'46px');
      if(item.name==='lyricTextAlignment')assert.strictEqual(await page.locator('canvas.lyric-line').first().getAttribute('data-left'),'2');
      if(item.name.startsWith('weight:'))assert.strictEqual(Number(await page.locator('canvas.lyric-line').first().evaluate(n=>n.style.fontWeight)),({LIGHT:300,NORMAL:400,MEDIUM:500,SEMI_BOLD:600,BOLD:700,EXTRA_BOLD:800,BLACK:900})[item.frame.settings.lyricFontWeight]);
      if(item.name==='lyricStagePosition')assert.strictEqual(await page.locator('#lyrics').getAttribute('data-position'),'BOTTOM');
      if(item.name.startsWith('visualizer:'))assert.strictEqual(await page.locator('#stageCanvas').getAttribute('data-style'),item.frame.settings.visualizerStyle);
      if(item.name.startsWith('palette:'))assert.strictEqual(await page.locator('canvas.lyric-line').first().getAttribute('data-fill'),item.frame.settings.palette.unsungFill);
      await checkpoint('setting:'+item.name);
    }
    // Verify pixels, not just received settings: the first MIDI syllable must fill progressively.
    const wipePixels=[];
    for(const position of [1100,1400,1700]){
      const sample=structuredClone(base);sample.playing=false;sample.position=position;sample.rawPosition=position;
      sample.transportRevision=position;sample.settings.lyricOutlineThickness=0;sample.settings.lyricShadowIntensity=0;
      sample.settings.palette={...sample.settings.palette,activeFill:'#FF0000',activeGlow:'#FF0000',unsungFill:'#FFFFFF',nextLineFill:'#FFFFFF',isRainbow:false};
      await apply(sample);
      const pixels=await page.locator('canvas.lyric-line').first().evaluate(canvas=>{
        const data=canvas.getContext('2d').getImageData(0,0,canvas.width,canvas.height).data;
        let red=0,white=0;for(let i=0;i<data.length;i+=4){if(data[i+3]>180){if(data[i]>200&&data[i+1]<40&&data[i+2]<40)red++;if(data[i]>200&&data[i+1]>200&&data[i+2]>200)white++;}}
        return {red,white,wipes:JSON.parse(canvas.dataset.wipes)};
      });
      wipePixels.push(pixels);await page.screenshot({path:path.join(out,'wipe-'+position+'.png')});
    }
    assert(wipePixels[0].red>0&&wipePixels[1].red>wipePixels[0].red&&wipePixels[2].red>wipePixels[1].red);
    assert(wipePixels[2].white<wipePixels[0].white);
    assert(wipePixels.every(p=>p.wipes[0]>0&&p.wipes.slice(1).every(w=>w===0)));
    assert.equal(await page.locator('svg.lyric-line').count(),0);
    fs.writeFileSync(path.join(out,'wipe-pixels.json'),JSON.stringify(wipePixels,null,2));
    await checkpoint('Actual Canvas pixels progressively wipe one MIDI syllable while later syllables stay unsung');
    const fallback=await browser.newPage();
    await fallback.addInitScript(()=>{delete Element.prototype.replaceChildren;window.AbortController=undefined;});
    await fallback.goto(origin+'/?pin=1234');await fallback.getByText('Show Karaoke Stage',{exact:true}).click();
    await fallback.waitForFunction(()=>window.JktvStage?.state().settings!==null);
    assert(await fallback.locator('canvas.lyric-line').count()>0);await fallback.close();
    await checkpoint('TV DOM and fetch timeout fallbacks work without replaceChildren or AbortController');
    // DOM persists during ordinary ticking; no per-frame innerHTML replacement or text size changes.
    await apply(base);
    const initial=await page.evaluate(()=>({n:window.JktvStage.state().rebuilds,size:document.querySelector('canvas.lyric-line').style.fontSize}));
    await page.waitForTimeout(600);
    const after=await page.evaluate(()=>({n:window.JktvStage.state().rebuilds,size:document.querySelector('canvas.lyric-line').style.fontSize}));
    assert.deepStrictEqual(after,initial);await checkpoint('Stable lyric DOM and font size across clock packets');
    const stalePacket=await page.evaluate(()=>{
      const current=window.JktvStage.state().frame;
      return window.JktvStage.acceptFrame({...current,sampleTime:current.sampleTime-1000},performance.now(),performance.now());
    });
    assert.strictEqual(stalePacket,false);await checkpoint('Late host packets are rejected');
    const types=await page.evaluate(()=>{
      const C=window.JktvStage.HostClock,c=new C();
      const packet={session:'s',song:'1',playing:true,position:1000,duration:100000,tempo:1,transportRevision:0,sampleTime:5000,sentTime:5002};
      c.accept(packet,100,122,false);
      const start=c.position(122),oneSecond=c.position(1122);
      const monotonic=[];
      c.accept({...packet,position:1110,sampleTime:5100,sentTime:5102},200,222,false);
      for(let t=222;t<1200;t+=20)monotonic.push(c.position(t));
      c.accept({...packet,playing:false,position:2345,sampleTime:6100,sentTime:6102},1200,1222,false);
      const paused=c.position(2000);
      c.accept({...packet,transportRevision:2,position:9000,sampleTime:7100,sentTime:7102},2200,2222,false);
      const seek=c.position(2222),stale=c.position(10000),lease=c.position(3722);
      return {start,oneSecond,paused,seek,monotonic,stale,lease};
    });
    assert(Math.abs(types.start-1012)<1);
    assert(Math.abs(types.oneSecond-2012)<1);
    assert.strictEqual(types.paused,2345);
    assert(Math.abs(types.seek-9012)<1);
    assert(types.monotonic.every((v,i,a)=>!i||v>=a[i-1]));
    assert.strictEqual(types.stale,types.lease);
    await checkpoint('Clock transit, drift slewing, pause, seek revision and stale lease');
    // Lost connection does not exit Stage or reload. Latest configuration is restored.
    online=false;
    await page.waitForFunction(()=>window.JktvStage.state().offline);
    latest=structuredClone(base);latest.settings.lyricFontFamily='SERIF';latest.settings.lyricFontSizeScale=1.5;
    latest.position=8200;latest.rawPosition=8223;latest.transportRevision++;version++;online=true;
    await page.waitForFunction(v=>window.JktvStage.state().settingsRevision==='case-'+v,version);
    assert.strictEqual(await page.evaluate(()=>window.JktvStage.state().mode),'stage');
    assert(Math.abs(await page.evaluate(()=>window.JktvStage.state().position)-8200)<1);
    await checkpoint('Reconnect retains Stage and restores latest host styles and position');
    // Image/video switching and settings changes must not repeatedly reload the background.
    let f=structuredClone(base);f.settings.hasBackground=true;f.settings.backgroundIsImage=true;f.settings.backgroundRevision='image-one';
    await apply(f);await page.waitForFunction(()=>!!document.querySelector('.stage-background img')?.complete);
    assert.strictEqual(await page.locator('.stage-background img').count(),1);
    f.settings.backgroundIsImage=false;f.settings.backgroundRevision='video-one';f.playing=true;
    await apply(f);await page.waitForFunction(()=>document.querySelector('.stage-background video')?.readyState>=2);
    const beforeMedia=mediaRequests;
    f.settings.lyricFontWeight='BOLD';await apply(f);await page.waitForTimeout(500);
    assert.strictEqual(mediaRequests,beforeMedia);
    await checkpoint('Image/video backgrounds, pause/playing and no reload on typography change');
    // Resolution/overscan, countdown stability and alternate aspect ratios.
    for(const [width,height] of [[1280,720],[1920,1080],[3840,2160],[2560,1080]]){
      await page.setViewportSize({width,height});
      await apply(base);await page.waitForTimeout(120);
      const bounds=await page.locator('#lyrics').boundingBox();
      assert(bounds.x>=width*.049&&bounds.x+bounds.width<=width*.951);
      const before=await page.locator('#lyrics').boundingBox();
      f=structuredClone(base);f.position=500;f.rawPosition=523;f.transportRevision++;await apply(f);
      const countdownBounds=await page.locator('#lyrics').boundingBox();
      assert.deepStrictEqual(countdownBounds,before);
      await apply(base);
      const file='stage-'+width+'x'+height+'.png';await page.screenshot({path:path.join(out,file)});screenshotFiles.push(file);
      await checkpoint('Resolution '+width+'x'+height+' and countdown layout stability');
    }
    assert.deepStrictEqual(errors,[]);
    const summary={passed:results.length,cases:results,errors,screenshots:screenshotFiles,tickRequests,mediaRequests,
      scope:'Headless Chrome using actual phone HTML/assets and Kotlin-encoded host packets. Physical TV/acoustic latency not measured.'};
    fs.writeFileSync(path.join(out,'browser-settings-results.json'),JSON.stringify(summary,null,2));
    console.log(JSON.stringify(summary,null,2));
  }catch(error){
    fs.writeFileSync(path.join(out,'browser-failure.json'),JSON.stringify({error:String(error),errors,passed:results,
      media:await page.evaluate(()=>({error:document.getElementById('tvStage').dataset.mediaError,
        videos:Array.from(document.querySelectorAll('video')).map(v=>({src:v.src,state:v.readyState,error:v.error?.message}))}))},null,2));
    await page.screenshot({path:path.join(out,'browser-failure.png')});throw error;
  }finally{await browser.close();server.close();}
})().catch(e=>{console.error(e);server.close();process.exitCode=1;});

