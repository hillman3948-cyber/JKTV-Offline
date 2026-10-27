/* Shared bounded PCM decoder, also executed in the network Worker. */
(function(root){
  'use strict';
  function decode(bytes){
    const v=new DataView(bytes);
    if(v.byteLength<16||v.getUint32(0)!==0x4a4b4155||v.getInt32(4)!==44100||v.getInt32(8)!==2)throw Error('Invalid host PCM');
    const count=v.getInt32(12);if(count<0||count>128)throw Error('Invalid PCM count');
    const u64=o=>v.getUint32(o)*4294967296+v.getUint32(o+4),blocks=[];
    let at=16,total=0;
    for(let n=0;n<count;n++){
      if(at+44>v.byteLength)throw Error('Incomplete PCM');
      const b={seq:u64(at),revision:u64(at+8),position:v.getFloat64(at+16),capture:v.getFloat64(at+24),tempo:v.getFloat32(at+32),playing:!!v.getInt32(at+36)};
      const length=v.getInt32(at+40);at+=44;
      if(length<4||length>65536||length%4||at+length>v.byteLength)throw Error('Invalid PCM length');
      b.frames=length/4;b.offset=total;b.left=new Float32Array(b.frames);b.right=new Float32Array(b.frames);
      for(let i=0;i<b.frames;i++){b.left[i]=v.getInt16(at+i*4,true)/32768;b.right[i]=v.getInt16(at+i*4+2,true)/32768;}
      blocks.push(b);total+=b.frames;at+=length;
    }
    if(at!==v.byteLength)throw Error('Trailing PCM bytes');
    return blocks;
  }
  root.JktvPcm={decode};
})(typeof self!=='undefined'?self:globalThis);
