const fs=require('fs'),path=require('path'),assert=require('assert'),{execFileSync}=require('child_process');
const {chromium}=require('C:/Users/hillm/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const root=path.resolve(__dirname,'..'),out=path.join(root,'stage-sync-validation');
const adb='C:/Users/hillm/AppData/Local/Android/Sdk/platform-tools/adb.exe',serial='emulator-5580';
const call=(...args)=>execFileSync(adb,['-s',serial,...args],{encoding:'utf8',timeout:30000});
const nodes=()=>{
 call('shell','uiautomator','dump','/sdcard/stage-live-qa.xml');
 return [...call('shell','cat','/sdcard/stage-live-qa.xml').matchAll(/<node\b[^>]*>/g)].map(m=>Object.fromEntries([...m[0].matchAll(/([\w-]+)="([^"]*)"/g)].map(a=>[a[1],a[2]])));
};
const tap=n=>{const b=n.bounds.match(/\d+/g).map(Number);call('shell','input','tap',String(Math.round((b[0]+b[2])/2)),String(Math.round((b[1]+b[3])/2)));};
const click=(label)=>{
 const list=nodes(),match=list.filter(n=>n.text===label||n['content-desc']===label);
 assert(match.length,'Missing phone UI label '+label);tap(match[match.length-1]);
};
(async()=>{
 const session=JSON.parse(fs.readFileSync(path.join(out,'live-session.json'),'utf8'));
 const pin=new URL(session.url).searchParams.get('pin'),url='http://127.0.0.1:58383/?pin='+pin;
 const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
 const page=await browser.newPage({viewport:{width:1920,height:1080}}),errors=[],passed=[];
 page.on('pageerror',e=>errors.push(e.message));
 try{
  await page.goto(url);await page.getByText('Show Karaoke Stage',{exact:true}).click();
  await page.waitForFunction(()=>window.JktvStage?.state().settings!==null);
  passed.push('Actual Android QR HTML/assets/protocol loaded in Chrome');
  const full=await page.evaluate(()=>!!document.fullscreenElement);
  if(nodes().some(n=>n.text==='Close'))click('Close');click('Setup');
  for(const [label,key] of [['Show lyrics','lyricsEnabled'],['Syllable highlighting','lyricWipeEnabled'],['Show countdown','countdownEnabled'],
      ['Show song information','songInfoEnabled'],['Show singer information','singerInfoEnabled']]){
    let list,entry;
    for(let i=0;i<4;i++){
      list=nodes();entry=list.find(n=>n.text===label);
      if(entry)break;
      call('shell','input','swipe','540','1850','540','900','300');
    }
    assert(entry,'Phone setting not found '+label);
    const center=n=>{const b=n.bounds.match(/\d+/g).map(Number);return (b[1]+b[3])/2;};
    const choices=list.filter(n=>n.checkable==='true').sort((a,b)=>Math.abs(center(a)-center(entry))-Math.abs(center(b)-center(entry)));
    const control=choices[0];assert(control&&Math.abs(center(control)-center(entry))<90,'Switch location mismatch '+label);
    const original=control.checked==='true';tap(control);
    await page.waitForFunction(({key,value})=>window.JktvStage.state().settings[key]===value,{key,value:!original});
    assert.strictEqual(await page.evaluate(()=>window.JktvStage.state().mode),'stage');
    tap(control);
    await page.waitForFunction(({key,value})=>window.JktvStage.state().settings[key]===value,{key,value:original});
    passed.push(label+' ON/OFF: actual phone UI -> live HTTP -> TV DOM');
  }
  for(const label of ['Library','Queue','Home']){
    click(label);await page.waitForTimeout(250);
    assert.strictEqual(await page.evaluate(()=>window.JktvStage.state().mode),'stage');
    if(full)assert(await page.evaluate(()=>!!document.fullscreenElement));
    passed.push('Phone '+label+' navigation leaves TV Stage/fullscreen active');
  }
  await page.screenshot({path:path.join(out,'live-browser-stage.png')});
  assert.deepStrictEqual(errors,[]);
  const state=await page.evaluate(()=>window.JktvStage.state());
  const packet=await page.request.get('http://127.0.0.1:58383/api/stage?pin='+pin);
  const body=await packet.json();
  assert(body.sentTime>=body.sampleTime);
  const font=await page.request.get('http://127.0.0.1:58383/stage-font?pin='+pin+'&family=DEFAULT&weight=900',{maxRetries:2});
  assert(font.ok());assert(font.headers()['x-jktv-font-weight']);
  passed.push('Actual host font bytes and weight descriptor served');
  fs.writeFileSync(path.join(out,'live-android-results.json'),JSON.stringify({passed:passed.length,cases:passed,errors,fullScreenSupported:full,
    settingsCount:Object.keys(state.settings).length,scope:'Live API 37 Android emulator host and headless Chrome receiver; no physical TV/audio playback test.'},null,2));
  console.log(JSON.stringify({passed:passed.length,cases:passed,errors},null,2));
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});

