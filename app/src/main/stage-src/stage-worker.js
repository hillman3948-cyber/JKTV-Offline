/* Networking and PCM conversion stay off the lyric/visualizer thread. */
importScripts('/stage-pcm.js');
let config={},socket=null,timer=0,watchdog=0,attempt=0,stopped=false,after=0,full=true,lastQueued=0,queuedAt=0;
function state(connected){postMessage({type:'connection',connected});}
function request(){
  if(!socket||socket.readyState!==1||stopped)return;
  const queued=Math.max(0,lastQueued-(Date.now()-queuedAt)/1000);
  socket.send(JSON.stringify({sent:Date.now(),token:config.token||'',after,queued,latency:config.latency||400,full}));
  clearTimeout(watchdog);watchdog=setTimeout(()=>{if(socket)socket.close();},1800);
}
function connect(){
  if(stopped||!config.url)return;
  socket=new WebSocket(config.url);socket.binaryType='arraybuffer';
  socket.onopen=()=>{attempt=0;full=true;state(true);request();};
  socket.onmessage=e=>{
    if(typeof e.data==='string'){
      clearTimeout(watchdog);
      const packet=JSON.parse(e.data);if(packet.frame)full=false;
      postMessage({type:'tick',packet,received:Date.now()});
      clearTimeout(timer);timer=setTimeout(request,config.token?40:150);
    } else {
      try{
        const blocks=self.JktvPcm.decode(e.data),transfers=[];
        blocks.forEach(b=>{after=Math.max(after,b.seq);transfers.push(b.left.buffer,b.right.buffer);});
        postMessage({type:'pcm',blocks},transfers);
      }catch(error){postMessage({type:'error',message:error.message});socket.close();}
    }
  };
  socket.onerror=()=>socket.close();
  socket.onclose=()=>{state(false);clearTimeout(timer);clearTimeout(watchdog);if(!stopped)timer=setTimeout(connect,Math.min(3000,400*Math.pow(1.6,attempt++)));};
}
onmessage=e=>{
  if(e.data.type==='stop'){stopped=true;clearTimeout(timer);clearTimeout(watchdog);if(socket)socket.close();return;}
  const next=e.data;
  if(next.token!==config.token)after=next.after||0;else after=Math.max(after,next.after||0);
  lastQueued=next.queued||0;queuedAt=Date.now();config=next;
  if(!socket)connect();
};
