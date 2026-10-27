/* Phone-owned Stage protocol v3. No TV preferences or independently advancing song player. */
(function () {
  'use strict';
  const STAGE_VISUALIZER_ENABLED = false;
  const byId = id => document.getElementById(id);
  const pin = String(window.JKTV_PAIRING_PIN || '');
  const query = '?pin=' + encodeURIComponent(pin);
  const stage = byId('tvStage'), lyrics = byId('lyrics'), canvas = byId('stageCanvas');
  const context = canvas.getContext('2d'), measure = document.createElement('canvas').getContext('2d');
  if (!STAGE_VISUALIZER_ENABLED && canvas) {
    canvas.hidden = true;
    canvas.style.display = 'none';
  }
  const background = document.createElement('div');
  background.className = 'stage-background';
  stage.insertBefore(background, stage.firstChild);
  byId('stageVideo').remove(); byId('stageImage').remove();
  const clamp = (n,a,b) => Math.max(a,Math.min(b,n));
  const fonts = {DEFAULT:'Arial,Roboto,sans-serif',SANS_SERIF:'Arial,Roboto,sans-serif',
    SERIF:'Georgia,"Times New Roman",serif',MONOSPACE:'"Courier New",monospace',
    CONDENSED:'"Arial Narrow","Roboto Condensed",sans-serif',CASUAL:'"Comic Sans MS",cursive',CURSIVE:'"Comic Sans MS",cursive'};
  const fontCache={};
  const setText=(id,text)=>{const node=byId(id);if(node.textContent!==text)node.textContent=text;};
  const weights = {LIGHT:300,NORMAL:400,MEDIUM:500,SEMI_BOLD:600,BOLD:700,EXTRA_BOLD:800,BLACK:900};
  let settings=null, frame=null, lines=[], revision='', settingsRevision='', mode='chooser';
  let pollTimer=0, raf=0, requestGeneration=0, awaiting=false, lastReply=0, offline=false;
  let fitKey='',fitSize=0;
  let layoutKey='', nodes=[], rebuilds=0, fontFamily='', fontKey='', mediaKey='', media=null, lastVideoSeek=0;
  let lastVisual=0, visualPhase=0, visualAt=0, lastAnimation=0, slowFrames=0, quality=1, workMs=0, lyricDraws=0, guestQrUrl="";
  class HostClock {
    constructor(){this.ready=false;this.samples=[];this.lastPacket=0;}
    position(now=performance.now()){
      if(!this.ready)return 0;
      const until=Math.min(now,this.lastPacket+1500);
      return clamp(this.anchor+(this.playing?Math.max(0,until-this.at)*this.tempo*this.rate:0),0,this.duration);
    }
    accept(f,start,end,force){
      const sessionChanged=this.session!==f.session;
      if(sessionChanged)this.samples=[];
      const process=Math.max(0,f.sentTime-f.sampleTime), network=Math.max(0,end-start-process);
      const offset=f.sentTime+network/2-end;
      this.samples.push({rtt:network,offset});if(this.samples.length>12)this.samples.shift();
      const best=this.samples.reduce((a,b)=>a.rtt<b.rtt?a:b);
      const transit=clamp(end+best.offset-f.sampleTime,0,1500);
      const target=clamp(f.position+(f.playing?transit*f.tempo:0),0,f.duration);
      const before=this.position(end);
      const jump=!this.ready||force||sessionChanged||this.song!==f.song||
        this.transport!==f.transportRevision||this.playing!==f.playing||Math.abs(target-before)>600;
      this.anchor=jump?target:before;this.at=end;this.duration=f.duration;this.tempo=f.tempo;
      this.rate=jump||!f.playing?1:clamp(1+(target-before)/1200,.92,1.08);
      this.playing=f.playing;this.session=f.session;this.song=f.song;this.transport=f.transportRevision;
      this.ready=true;this.lastPacket=end;this.drift=target-before;this.rtt=network;return jump;
    }
  }
  const clock=new HostClock();
  const link=new window.JktvStageLink(pin);
  const audio=new window.JktvStageAudio(pin,stage,link);
  link.onframe=(f,start,end)=>acceptFrame(f,start,end);
  function activeLine(items,pos){
    if(!items.length)return -1;
    for(let i=0;i<items.length;i++){
      const end=Math.min(items[i].end+160,(items[i+1]?items[i+1].start:Infinity)-1);
      if(pos>=items[i].start&&pos<=end)return i;
    }
    const next=items.findIndex(l=>l.start>pos);
    if(next===0)return 0;
    if(next>0)return pos>=items[next-1].end+160||items[next].start-pos<=650?next:next-1;
    return items.length-1;
  }
  function indices(active,display){
    if(display==='ONE_LINE')return [active];
    if(display==='TWO_LINE')return active%2===0?[active,active+1]:[active+1,active];
    return [active,active+1,active+2,active+3];
  }
  function textWidth(text,size){
    measure.font=weights[settings.lyricFontWeight]+' '+size+'px '+fontFamily;
    return measure.measureText(text).width+Math.max(0,text.length-1)*settings.lyricLetterSpacing*innerHeight/540;
  }
  function invalidate(){layoutKey='';}
  function lineLayout(pos){
    const active=activeLine(lines,pos), chosen=indices(active,settings.lyricDisplayMode);
    const key=revision+'|'+chosen.join(',')+'|'+settingsRevision+'|'+innerWidth+'x'+innerHeight+'|'+fontFamily;
    if(key===layoutKey)return active;
    layoutKey=key;lyrics.replaceChildren();nodes=[];rebuilds++;
    const width=lyrics.clientWidth, scale=innerHeight/540;
    let size=innerHeight*.067*settings.lyricFontSizeScale*(chosen.length===1?1.12:1);
    if(chosen.length===4)size=Math.min(size,lyrics.clientHeight/6.2);
    const fitting=revision+'|'+settingsRevision+'|'+fontFamily+'|'+width+'|'+innerHeight;
    if(fitting!==fitKey){
      const unique=Array.from(new Set(lines.map(l=>l.text)));
      const widest=unique.reduce((n,text)=>Math.max(n,textWidth(text,size)),1);
      fitSize=Math.max(14,size*Math.min(1,(width-8)/widest));fitKey=fitting;
    }
    size=fitSize;
    const height=size*1.52;
    lyrics.style.gap=settings.lyricLineSpacing*scale+'px';
    chosen.forEach((index,slot)=>{
      const line=lines[index], surface=document.createElement('canvas');
      surface.className='lyric-line';surface.style.height=height+'px';
      const dpr=Math.min(2,devicePixelRatio||1);
      surface.width=Math.ceil(width*dpr);surface.height=Math.ceil(height*dpr);
      lyrics.append(surface);if(!line)return;
      surface.setAttribute('role','img');surface.setAttribute('aria-label',line.text);
      const left=settings.lyricTextAlignment==='LEFT'?2:(width-textWidth(line.text,size))/2;
      const words=Array.isArray(line.words)&&line.words.length?line.words:
        [{from:0,to:line.text.length,start:line.start,end:line.end,syllables:line.syllables}];
      const overlays=words.map(word=>({word,start:left+textWidth(line.text.slice(0,word.from),size),
        end:left+textWidth(line.text.slice(0,word.to),size),geometry:(word.syllables||[]).map(s=>({
          start:Number(s[1]),end:Number(s[2]),x:left+textWidth(line.text.slice(0,Number(s[3])),size),
          width:textWidth(line.text.slice(Number(s[3]),Number(s[4])),size)}))}));
      surface.style.fontFamily=fontFamily;surface.style.fontWeight=weights[settings.lyricFontWeight];
      surface.style.fontSize=size+'px';surface.style.letterSpacing=settings.lyricLetterSpacing*scale+'px';
      if(!window.JKTV_FLEX_GAP)surface.style.marginBottom=settings.lyricLineSpacing*scale+'px';
      surface.dataset.outline=settings.lyricOutlineThickness*scale;surface.dataset.left=left;
      nodes.push({line,surface,c:surface.getContext('2d'),overlays,slot,left,size,width,height,dpr,scale});
    });
    if(!lines.length){
      const message=document.createElement('div');message.className='lyric-line';
      message.style.textAlign='center';message.style.color=settings.palette.unsungFill;
      message.style.fontSize=size*.6+'px';message.textContent=frame.song?'INSTRUMENTAL — NO TIMED LYRICS':'SELECT A SONG ON YOUR PHONE';
      lyrics.replaceChildren(message);
    }
    return active;
  }
  function renderLyrics(pos){
    if(!settings)return;
    const active=settings.lyricsEnabled?lineLayout(pos):activeLine(lines,pos), palette=settings.palette;
    if(settings.lyricsEnabled)nodes.forEach(node=>{
      const c=node.c;if(!c)return;
      const stamp=(pos<node.line.start?'before':pos>node.line.end?'after':settings.lyricWipeEnabled?Math.floor(pos/12):'static')+'|'+active;
      if(node.drawStamp===stamp)return;node.drawStamp=stamp;lyricDraws++;
      const singing=node.line.index===lines[active]?.index&&pos>=node.line.start&&pos<=node.line.end;
      c.setTransform(node.dpr,0,0,node.dpr,0,0);c.clearRect(0,0,node.width,node.height);
      c.font=weights[settings.lyricFontWeight]+' '+node.size+'px '+fontFamily;
      c.textBaseline='alphabetic';c.lineJoin='round';
      const spacing=settings.lyricLetterSpacing*node.scale;
      function paint(target,fill,outline){
        target.setTransform(node.dpr,0,0,node.dpr,0,0);
        target.font=c.font;target.textBaseline='alphabetic';target.lineJoin='round';
        target.fillStyle=fill;target.strokeStyle=palette.outlineColor;target.lineWidth=settings.lyricOutlineThickness*node.scale;
        target.shadowColor='rgba(0,0,0,.5)';target.shadowBlur=Math.min(4,2*settings.lyricShadowIntensity);target.shadowOffsetY=1;
        let x=node.left;
        if(!spacing){if(outline&&target.lineWidth>0)target.strokeText(node.line.text,x,node.size*1.1);target.fillText(node.line.text,x,node.size*1.1);}
        else for(const ch of node.line.text){if(outline&&target.lineWidth>0)target.strokeText(ch,x,node.size*1.1);target.fillText(ch,x,node.size*1.1);x+=target.measureText(ch).width+spacing;}
        target.shadowBlur=0;target.shadowOffsetY=0;
      }
      function draw(fill,outline){
        const key=String(fill)+'|'+outline;
        if(typeof fill!=='string'){paint(c,fill,outline);return;}
        node.rasters=node.rasters||{};
        if(!node.rasters[key]){
          const surface=document.createElement('canvas');surface.width=node.surface.width;surface.height=node.surface.height;
          paint(surface.getContext('2d'),fill,outline);node.rasters[key]=surface;
        }
        c.drawImage(node.rasters[key],0,0,node.width,node.height);
      }
      const base=node.line.index===lines[active]?.index?palette.unsungFill:palette.nextLineFill;
      c.clearRect(0,0,node.width,node.height);node.surface.dataset.fill=base;draw(base,true);
      const widths=[];
      node.overlays.forEach(o=>{
        let x=o.start,current=false;
        if(singing&&settings.lyricWipeEnabled)o.geometry.forEach(s=>{
          if(pos>=s.start)x=Math.max(x,s.x+s.width*clamp((pos-s.start)/Math.max(1,s.end-s.start),0,1));
          if(pos>=s.start&&pos<=s.end)current=true;
        });
        const width=clamp(x-o.start,0,o.end-o.start);widths.push(width);
        if(width<=0)return;
        c.save();c.beginPath();c.rect(o.start,0,width,node.height);c.clip();
        let fill=current?palette.activeGlow:palette.activeFill;
        if(palette.isRainbow){fill=c.createLinearGradient(node.left,0,node.left+textWidth(node.line.text,node.size),0);
          ['#ff0055','#ff7f00','#ffff00','#00dd77','#00dfff','#6655ff','#ff0055'].forEach((v,i)=>fill.addColorStop(i/6,v));}
        draw(fill,false);c.restore();
      });
      node.surface.dataset.wipes=JSON.stringify(widths);
    });
    const current=lines[active],next=lines[active+1];
    let vocal=null;
    if(current&&current.start-pos>=200&&current.start-pos<=3800)vocal=current.start;
    else if(next&&current&&pos>current.end&&next.start-current.end>=3500&&next.start-pos>=200&&next.start-pos<=3800)vocal=next.start;
    setText('countdown',settings.countdownEnabled&&vocal!==null?'READY IN '+Math.ceil((vocal-pos)/1000):'');
    if(settings.singerInfoEnabled){
      const singer=settings.karaokeMode==='DUET'&&current&&current.index%2===1?(settings.partner||'Partner'):settings.singer;
      setText('stageSinger',singer||'');
    }
  }
  async function hostFont(){
    const key=settings.lyricFontFamily+':'+settings.lyricFontWeight;
    if(key===fontKey)return;fontKey=key;
    fontFamily=fonts[settings.lyricFontFamily]||fonts.DEFAULT;
    if(fontCache[key]){fontFamily=fontCache[key];invalidate();return;}
    if(!window.FontFace)return;
    try{
      const weight=weights[settings.lyricFontWeight], family='JKTVHost'+key.replace(/[^a-z0-9]/gi,'');
      let response,bytes;
      for(let attempt=0;attempt<3;attempt++){
        try {
          response=await fetch('/stage-font'+query+'&family='+encodeURIComponent(settings.lyricFontFamily)+'&weight='+weight);
          if(response.ok)bytes=await response.arrayBuffer();break;
        }
        catch(error){if(attempt===2)throw error;await new Promise(resolve=>setTimeout(resolve,300*(attempt+1)));}
      }
      if(!response.ok)throw new Error('Host font unavailable');
      const actualWeight=response.headers.get('X-JKTV-Font-Variable')==='true'?'100 900':response.headers.get('X-JKTV-Font-Weight')||'400';
      const face=new FontFace(family,bytes,{weight:actualWeight});
      await face.load();document.fonts.add(face);fontCache[key]='"'+family+'",'+(fonts[settings.lyricFontFamily]||fonts.DEFAULT);
      if(fontKey===key){fontFamily='"'+family+'",'+fontFamily;invalidate();}
    }catch(e){/* Use the corresponding generic family if the host OS cannot export this font. */}
  }
  function applySettings(s){
    settings=s;lyrics.hidden=!s.lyricsEnabled;
    const qr=byId('guestQr'),showQr=!!s.stageGuestQrEnabled&&!!s.stageGuestJoinUrl;
    qr.hidden=!showQr;stage.classList.toggle('has-guest-qr',showQr);
    if(showQr&&guestQrUrl!==s.stageGuestJoinUrl){guestQrUrl=s.stageGuestJoinUrl;byId('guestQrImage').src='/stage-qr.svg'+query+'&v='+encodeURIComponent(guestQrUrl);}

    lyrics.dataset.position=s.lyricStagePosition;
    byId('stageTitle').hidden=!s.songInfoEnabled;byId('stageTitle').textContent=s.title+(s.artist?' · '+s.artist:'');
    byId('stageSinger').hidden=!s.singerInfoEnabled;
    byId('countdown').hidden=!s.countdownEnabled;
    stage.style.setProperty('--stage-scrim',clamp(s.lyricsScrimAlpha,0,.95));
    hostFont();setMedia();invalidate();
  }
  function setMedia(){
    const key=settings.hasBackground?settings.backgroundRevision+':'+settings.backgroundIsImage:'';
    if(key===mediaKey){if(media)media.style.filter='brightness('+settings.backgroundBrightness+')';return;}
    mediaKey=key;
    if(!key){background.replaceChildren();media=null;return;}
    const next=document.createElement(settings.backgroundIsImage?'img':'video');
    const previous=media;let activated=false;
    next.style.filter='brightness('+settings.backgroundBrightness+')';
    const ready=()=>{
      if(activated||key!==mediaKey){if(key!==mediaKey)next.remove();return;}
      activated=true;media=next;next.style.opacity='1';
      if(previous){previous.style.opacity='0';setTimeout(()=>{if(previous.tagName==='VIDEO')previous.pause();previous.remove();},200);}
      if(next.tagName==='VIDEO')syncVideo(true);
    };
    if(next.tagName==='VIDEO'){next.muted=true;next.loop=true;next.playsInline=true;next.preload='auto';next.addEventListener('canplay',ready);}
    else{next.alt='';next.addEventListener('load',ready);}
    next.addEventListener('error',()=>{stage.dataset.mediaError=next.error?.message||'Media load failed';next.remove();});
    background.append(next);
    next.src='/stage-media'+query+'&v='+encodeURIComponent(key);
    if(next.tagName==='VIDEO')next.load();
  }
  function syncVideo(force){
    if(!media||media.tagName!=='VIDEO'||media.readyState<1||!frame)return;
    const raw=clock.position()-frame.position+frame.rawPosition,target=(Math.max(0,raw)/1000)%media.duration;
    const now=performance.now();
    if(Number.isFinite(target)&&(force||Math.abs(media.currentTime-target)>.35)&&now-lastVideoSeek>500){media.currentTime=target;lastVideoSeek=now;}
    const playing=frame.playing&&now-lastReply<1500;
    if(playing&&media.paused)media.play().catch(()=>{});if(!playing&&!media.paused)media.pause();
  }
  function drawVisual(now){
    if(!STAGE_VISUALIZER_ENABLED){
      if(canvas&&!canvas.hidden){canvas.hidden=true;canvas.style.display="none";}
      if(context&&canvas&&canvas.width)context.clearRect(0,0,canvas.width,canvas.height);
      return;
    }
    if(!settings||!context)return;
    const fps=settings.visualizerPerformanceMode||quality<.9?30:60;
    if(now-lastVisual<1000/fps)return;
    const dpr=Math.min(1.5,devicePixelRatio||1,1920/innerWidth,1080/innerHeight)*quality,w=Math.round(innerWidth*dpr),h=Math.round(innerHeight*dpr);
    if(canvas.width!==w||canvas.height!==h){canvas.width=w;canvas.height=h;}
    visualPhase+=(now-(visualAt||now))/1000*settings.visualizerSpeed;visualAt=now;lastVisual=now;
    const c=context,t=visualPhase,levels=frame.levels||[];
    c.clearRect(0,0,w,h);
    c.globalAlpha=settings.visualizerBrightness;
    const primary=settings.palette.activeFill,secondary=settings.visualizerSecondaryColor;
    const hasAudio=frame.playing&&levels.some(v=>v>.01);
    const level=i=>clamp((hasAudio?(levels[i%16]||0):Math.sin(t*2.3+i*.45)*.13+.22)*settings.visualizerSensitivity,.02,1);
    const avg=Array.from({length:16},(_,i)=>level(i)).reduce((a,b)=>a+b,0)/16;
    const style=settings.visualizerStyle;
    c.lineWidth=Math.max(1,h/600);c.strokeStyle=primary;c.fillStyle=primary;
    c.shadowColor=primary;c.shadowBlur=Math.min(9,settings.visualizerGlowStrength*3*dpr);
    const line=(points,color,width)=>{c.strokeStyle=color;c.lineWidth=width;c.beginPath();points.forEach((p,i)=>i?c.lineTo(p[0],p[1]):c.moveTo(p[0],p[1]));c.stroke();};
    const bars=(rainbow,threeD)=>{
      const count=settings.visualizerPerformanceMode?24:48,gap=w/count*.28,bw=w/count-gap;
      for(let i=0;i<count;i++){
        const bh=h*(.025+level(i)*.17),x=i*w/count;
        const g=c.createLinearGradient(0,h-bh,0,h);g.addColorStop(0,rainbow?'hsl('+(i/count*300+t*20)+',85%,65%)':primary);g.addColorStop(1,secondary);
        c.fillStyle=g;c.fillRect(x,h-bh,bw,bh);
        if(threeD){c.globalAlpha=settings.visualizerBrightness*.24;c.fillRect(x+bw,h-bh+4*dpr,6*dpr,bh);c.globalAlpha=settings.visualizerBrightness;}
      }
    };
    const wave=(amount,edge)=>{for(let j=0;j<amount;j++)line(Array.from({length:100},(_,i)=>{
      const x=i/99*w;return[x,h*(edge?.86:.72)+Math.sin(i*.13+t*2+j*.8)*h*(.016+avg*.05)*(1+j*.12)];}),j%2?secondary:primary,Math.max(1,h/440));};
    const particles=(galaxy)=>{const count=settings.visualizerPerformanceMode?25:70;
      for(let i=0;i<count;i++){const x=(i*127.37+t*(galaxy?7:13))%100/100*w,y=(Math.sin(i*5.17+t*.25)*.5+.5)*h;
        c.globalAlpha=settings.visualizerBrightness*(.12+.35*level(i));c.fillStyle=i%3?primary:secondary;c.beginPath();c.arc(x,y,(1+level(i)*3)*dpr,0,Math.PI*2);c.fill();}};
    const rings=(tunnel)=>{for(let i=0;i<9;i++){const radius=(i/9+(t*.06)%1)%1;
      c.globalAlpha=settings.visualizerBrightness*(1-radius)*.45;c.strokeStyle=i%2?primary:secondary;c.lineWidth=2*dpr;
      c.beginPath();c.ellipse(w/2,h*.72,radius*w*.65,radius*h*(tunnel?.6:.2),0,0,Math.PI*2);c.stroke();}};
    switch(style){
      case 'NEON_BARS':bars(false,false);break;
      case 'RGB_EQUALIZER':case 'RETRO_DISCO':bars(true,false);break;
      case 'SPECTRUM_3D':bars(false,true);break;
      case 'AUDIO_WAVE':wave(2,false);break;
      case 'ELECTRIC_WAVEFORM':wave(1,true);break;
      case 'AURORA_WAVES':wave(7,false);break;
      case 'RAINBOW_ENERGY':bars(true,false);wave(2,false);break;
      case 'CIRCULAR_SPECTRUM':for(let i=0;i<64;i++){const a=i/64*Math.PI*2,rad=h*.15;
        line([[w/2+Math.cos(a)*rad,h*.77+Math.sin(a)*rad],[w/2+Math.cos(a)*(rad+level(i)*h*.08),h*.77+Math.sin(a)*(rad+level(i)*h*.08)]],i%2?primary:secondary,3*dpr);}break;
      case 'FLOATING_PARTICLES':particles(false);break;
      case 'GALAXY_PARTICLES':particles(true);break;
      case 'DIGITAL_TUNNEL':rings(true);break;
      case 'HOLOGRAPHIC_RINGS':rings(false);break;
      case 'CYBERPUNK_GRID':for(let i=0;i<17;i++){line([[w/2,h*.67],[i/16*w,h]],primary,1*dpr);
        const y=h*(.69+i*.02+(t*.012)% .02);line([[0,y],[w,y]],secondary,1*dpr);}break;
      case 'CONCERT_LASERS':case 'LIGHT_RAYS':case 'DANCING_BEAMS':case 'DYNAMIC_SPOTLIGHT':case 'CONCERT_STAGE':
        for(let i=0;i<6;i++){c.globalAlpha=settings.visualizerBrightness*(style==='LIGHT_RAYS'?.07:.13);
          c.fillStyle=i%2?primary:secondary;c.beginPath();const x=i/5*w,tip=x+Math.sin(t*.55+i)*w*.3;
          c.moveTo(x,h);c.lineTo(tip-w*.06,0);c.lineTo(tip+w*.06,0);c.closePath();c.fill();}break;
      case 'LIGHTNING_PULSE':line(Array.from({length:27},(_,i)=>[i/26*w,h*.88+Math.sin(i*13.3+Math.floor(t*8))*h*avg*.12]),primary,2*dpr);break;
      case 'NEON_EDGE':c.globalAlpha=settings.visualizerBrightness*.5;c.strokeStyle=primary;c.lineWidth=4*dpr;c.strokeRect(3*dpr,3*dpr,w-6*dpr,h-6*dpr);break;
      case 'BASS_PULSE_GLOW':case 'AMBIENT_GLOW':case 'CINEMATIC_STAGE':case 'CUSTOM_VISUALIZER':default:
        {const g=c.createRadialGradient(w/2,h,0,w/2,h,w*.65);g.addColorStop(0,primary);g.addColorStop(1,'transparent');
          c.globalAlpha=settings.visualizerBrightness*(style==='BASS_PULSE_GLOW'?.10+avg*.25:.16);c.fillStyle=g;c.fillRect(0,0,w,h);}
    }
    if(settings.visualizerLyricsSafeZone){
      c.globalAlpha=1;c.globalCompositeOperation='destination-out';
      const g=c.createLinearGradient(0,h*.18,0,h*.83);g.addColorStop(0,'transparent');g.addColorStop(.2,'rgba(0,0,0,.86)');
      g.addColorStop(.8,'rgba(0,0,0,.86)');g.addColorStop(1,'transparent');c.fillStyle=g;c.fillRect(0,h*.18,w,h*.65);
      c.globalCompositeOperation='source-over';
    }
    c.globalAlpha=1;c.shadowBlur=0;
    canvas.dataset.style=style;
  }
  function animation(now){
    if(mode!=='stage'){raf=0;return;}
    const started=performance.now();
    if(lastAnimation&&now-lastAnimation>26)slowFrames++;else slowFrames=Math.max(0,slowFrames-1);
    if(slowFrames>20){quality=Math.max(.65,quality-.1);slowFrames=0;}
    lastAnimation=now;
    if(settings&&frame){renderLyrics(audio.position(frame,clock.position(now)));if(STAGE_VISUALIZER_ENABLED)drawVisual(now);syncVideo(false);}
    workMs=performance.now()-started;
    raf=requestAnimationFrame(animation);
  }
  function acceptFrame(f,start,end){
    if(f.protocol!==3)throw new Error('Update the phone app and reload this page.');
    if(frame && f.session===frame.session && f.sampleTime<frame.sampleTime)return false;
    if(f.session!==frame?.session){revision='';settingsRevision='';fontKey='';}
    if(f.settings){settingsRevision=f.settingsRevision;applySettings(f.settings);}
    if(!settings)throw new Error('Waiting for host Stage settings.');
    if(f.lyrics){lines=f.lyrics.lines||[];revision=f.revision;invalidate();}
    if(revision!==f.revision)throw new Error('Waiting for host lyrics.');
    clock.accept(f,start,end,offline||end-lastReply>1500);
    frame=f;audio.frame(f);lastReply=end;offline=false;
    byId('stageConnection').hidden=true;
    return true;
  }
  async function poll(){
    if(mode!=='stage'||awaiting)return;
    if(link.connected&&performance.now()-link.received<1200){pollTimer=setTimeout(poll,250);return;}
    const generation=requestGeneration,start=performance.now();
    awaiting=true;
    try{
      const response=await window.JktvFetch('/api/stage'+query+'&revision='+encodeURIComponent(revision)+'&settingsRevision='+encodeURIComponent(settingsRevision),
        {cache:'no-store'},2000);
      if(!response.ok)throw new Error(response.status===403?'Pairing PIN expired. Reopen the current phone QR address.':'Phone Stage unavailable.');
      const packet=await response.json();
      if(generation===requestGeneration)acceptFrame(packet,start,performance.now());
    }catch(e){
      if(generation===requestGeneration){offline=true;revision='';settingsRevision='';
        byId('stageConnection').hidden=false;byId('stageConnection').textContent=e.message.includes('PIN')?e.message:'Reconnecting to phone…';}
    }finally{
      awaiting=false;
      if(mode==='stage')pollTimer=setTimeout(poll,offline?700:200);
    }
  }
  function selectMode(next){
    if(window.JktvRemoteMode)window.JktvRemoteMode(next==='remote');
    if(next!=='stage'){audio.stop();link.stop();}
    requestGeneration++;awaiting=false;clearTimeout(pollTimer);mode=next;
    byId('chooser').hidden=next!=='chooser';byId('remoteView').hidden=next!=='remote';stage.hidden=next!=='stage';
    document.documentElement.classList.toggle('stage-mode',next==='stage');document.body.classList.toggle('stage-mode',next==='stage');
    if(next==='stage'){link.start(audio.client);offline=true;revision='';settingsRevision='';poll();if(!raf)raf=requestAnimationFrame(animation);}
    else if(media?.tagName==='VIDEO')media.pause();
  }
  function enterStage(){
    if(window.JKTV_GUEST_KEY)return;
    audio.enable(true);selectMode('stage');
    const root=document.documentElement,full=root.requestFullscreen||root.webkitRequestFullscreen;
    if(full){try{const result=full.call(root);if(result?.catch)result.catch(()=>{});}catch(e){}}
  }
  byId('stageChoice').addEventListener('click',enterStage);
  byId('remoteChoice').addEventListener('click',()=>selectMode('remote'));
  window.addEventListener('resize',invalidate);
  window.addEventListener('pagehide',()=>{audio.stop();link.stop();});
  document.addEventListener('visibilitychange',()=>{if(document.hidden){audio.stop();link.stop();}else if(mode==='stage'){link.start(audio.client);audio.enable(false);}});
  // A fullscreen UI dismissal never resets a live Stage or loses its latest host snapshot.
  window.addEventListener('pageshow',()=>{if(mode==='stage'&&!awaiting){clearTimeout(pollTimer);poll();}});
  window.JktvStage={HostClock,activeLine,acceptFrame,enterStage,selectMode,
    state:()=>({mode,settings,frame,revision,settingsRevision,rebuilds,audio:audio.state(),performance:{workMs,quality,lyricDraws,socket:link.connected,frames:link.frames},renderPosition:frame?audio.position(frame,clock.position()):0,position:clock.position(),drift:clock.drift,rtt:clock.rtt,offline})};
  if(window.JKTV_INITIAL_VIEW==='stage')enterStage();else if(window.JKTV_INITIAL_VIEW==='remote')selectMode('remote');
})();

