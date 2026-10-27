/* Small TV DOM compatibility layer. Source syntax targets Chromium 56 / Tizen 4+. */
(function(){
  'use strict';
  if(!Element.prototype.replaceChildren)Element.prototype.replaceChildren=function(){
    while(this.firstChild)this.removeChild(this.firstChild);
    for(var i=0;i<arguments.length;i++)this.appendChild(arguments[i]);
  };
  window.JktvFetch=function(url,options,timeout){
    var controller=window.AbortController?new AbortController():null,timer;
    options=options||{};if(controller)options.signal=controller.signal;
    return new Promise(function(resolve,reject){
      timer=setTimeout(function(){if(controller)controller.abort();reject(Error('Connection timed out'));},timeout||2000);
      fetch(url,options).then(function(response){clearTimeout(timer);resolve(response);},function(error){clearTimeout(timer);reject(error);});
    });
  };
  var flex=document.createElement('div');flex.style.cssText='display:flex;flex-direction:column;row-gap:1px;position:absolute;visibility:hidden';
  flex.appendChild(document.createElement('div'));flex.appendChild(document.createElement('div'));document.body.appendChild(flex);
  window.JKTV_FLEX_GAP=flex.scrollHeight===1;flex.parentNode.removeChild(flex);
})();
