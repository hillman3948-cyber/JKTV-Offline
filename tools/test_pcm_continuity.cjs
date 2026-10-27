const assert=require('assert'),fs=require('fs'),vm=require('vm'),path=require('path');
const root=path.resolve(__dirname,'..');
let now=0;const nodes=[];
const context={currentTime:0,baseLatency:.02,createBuffer:(channels,length,rate)=>{const data=[new Float32Array(length),new Float32Array(length)];return {duration:length/rate,getChannelData:i=>data[i]};},
  createBufferSource:()=>{const node={playbackRate:{value:1},connect(){},disconnect(){},start(at){this.at=at;this.end=at+this.buffer.duration/this.playbackRate.value;nodes.push(this);},stop(){this.stopped=true;}};return node;}};
const sandbox={window:{},self:{},document:{createElement:()=>({})},performance:{now:()=>now*1000},setTimeout,clearTimeout,fetch:()=>Promise.resolve({}),console};
vm.createContext(sandbox);
vm.runInContext(fs.readFileSync(path.join(root,'app/src/main/stage-src/stage-pcm.js'),'utf8'),sandbox);
sandbox.window.JktvPcm=sandbox.self.JktvPcm;
vm.runInContext(fs.readFileSync(path.join(root,'app/src/main/stage-src/stage-audio.js'),'utf8'),sandbox);
const audio=new sandbox.window.JktvStageAudio('1234',{append(){}},null);
audio.context=context;audio.gain={};audio.wanted=true;audio.active=true;audio.latency=400;
function block(seq,revision=1){return {seq,revision,position:(seq-1)*256/44.1,capture:(seq-1)*256/44.1,tempo:1,playing:true,frames:256,left:new Float32Array(256),right:new Float32Array(256)};}
let seq=48;audio.enqueue(Array.from({length:48},(_,i)=>block(i+1)));
let maxSources=0,maxSegments=0,maxQueue=0;
// Two hours of sample time, with recurring 180 ms network jitter and ordinary batches.
for(let tick=0;now<7200;tick++){
  now+=[.04,.04,.08,.04,.18,.04,.05,.04][tick%8];context.currentTime=now;
  while(nodes.length&&nodes[0].end<=now){const source=nodes.shift();if(source.onended)source.onended();}
  const through=48+Math.floor(now*44100/256),blocks=[];
  while(seq<through)blocks.push(block(++seq));
  audio.enqueue(blocks);
  maxSources=Math.max(maxSources,audio.sources.length);maxSegments=Math.max(maxSegments,audio.segments.length);maxQueue=Math.max(maxQueue,audio.next-now);
}
assert.equal(audio.stats.underruns,0);assert.equal(audio.stats.missingBlocks,0);assert.equal(audio.stats.transportFlushes,0);
assert(maxSources<30);assert(maxSegments<400);assert(maxQueue<1.2);
// A newer PCM revision arriving before its Stage snapshot must flush exactly once.
audio.enqueue([block(++seq,2)]);const flushes=audio.stats.transportFlushes;
audio.frame({session:'s',transportRevision:2});assert.equal(audio.stats.transportFlushes,flushes);
audio.enqueue([block(++seq,1)]);assert.equal(audio.acceptedRevision,2);
const result={simulatedSeconds:now,stats:audio.stats,maxSources,maxSegments,maxQueue,scope:'Deterministic audio scheduler model; not a hardware audio benchmark.'};
fs.mkdirSync(path.join(root,'performance-validation'),{recursive:true});fs.writeFileSync(path.join(root,'performance-validation/pcm-continuity.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result,null,2));
