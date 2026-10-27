(function(){
  'use strict';
  class StageLink {
    constructor(pin){this.pin=pin;this.connected=false;this.worker=null;this.enabled=false;this.received=0;this.frames=0;}
    start(client){
      if(this.enabled)return;this.enabled=true;this.client=client;
      if(!window.Worker)return;
      try {
        this.worker=new Worker('/stage-worker.js');
        this.worker.onmessage=e=>{
          const data=e.data;
          if(data.type==='connection')this.connected=data.connected;
          else if(data.type==='tick'){
            this.received=performance.now();const rtt=Math.max(0,data.received-data.packet.echo),end=performance.now(),start=end-rtt;
            if(data.packet.frame){this.frames++;try{this.onframe(data.packet.frame,start,end);}catch(error){this.connected=false;}}
            if(data.packet.audio&&this.onaudio)this.onaudio(data.packet.audio,start,end);
          }else if(data.type==='pcm'&&this.onpcm)this.onpcm(data.blocks);
        };
        this.worker.onerror=()=>{this.connected=false;this.worker.terminate();this.worker=null;};
        this.update({});
      }catch(e){this.worker=null;}
    }
    update(audio){
      if(!this.worker)return;
      this.worker.postMessage({type:'config',url:(location.protocol==='https:'?'wss://':'ws://')+location.host+'/stage-stream?pin='+encodeURIComponent(this.pin)+'&client='+this.client,...audio});
    }
    stop(){this.enabled=false;this.connected=false;if(this.worker){this.worker.postMessage({type:'stop'});this.worker.terminate();this.worker=null;}}
  }
  window.JktvStageLink=StageLink;
})();
