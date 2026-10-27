const pin = window.JKTV_PAIRING_PIN;
const remoteQuery=window.JKTV_GUEST_KEY?'?guest='+encodeURIComponent(window.JKTV_GUEST_KEY):'?pin='+encodeURIComponent(pin);
let remoteActive=false,statusBusy=false,statusTimer=0,searchGeneration=0,queueStamp='';
const pendingCommands=new Set();
const requestId=()=>Date.now().toString(36)+'_'+Math.random().toString(36).slice(2);
let timer = null;

function debouncedSearch() {
  clearTimeout(timer);
  timer = setTimeout(()=>doSearch().catch(()=>{}), 250);
}

async function doSearch() {
  if(!remoteActive)return;
  const generation=++searchGeneration;
  const q = document.getElementById('searchBox').value;
  const res = await window.JktvFetch('/api/search' + remoteQuery + '&q=' + encodeURIComponent(q));
  const data = await res.json();
  if(generation!==searchGeneration||!remoteActive)return;
  const container = document.getElementById('resultsList');
  if (!data.ok || !data.songs.length) {
    container.innerHTML = '<div style="padding:10px;color:#8E9BBF;font-size:13px;">No matching songs found.</div>';
    return;
  }
  container.innerHTML = data.songs.map(s => `
    <div class="song">
      <div style="flex:1;min-width:0;">
        <div><span class="num">#${s.number}</span> <span class="title">${escapeHtml(s.title)}</span></div>
        <div class="artist">${escapeHtml(s.artist || 'Karaoke Arrangement')}</div>
      </div>
      <div class="row">
        <button onclick="reserve(${s.id}, 0)" class="pri">+ Queue</button>
        <button onclick="reserve(${s.id}, 1)">Sing</button>
      </div>
    </div>
  `).join('');
}

async function reserve(songId, playNow) {
  const key='reserve:'+songId+':'+playNow;if(pendingCommands.has(key))return;pendingCommands.add(key);
  try {
  const singer = document.getElementById('singerInput').value || 'Guest Singer';
  const partner = document.getElementById('partnerInput').value || '';
  const mode = document.getElementById('modeSelect').value || 'SOLO';
  localStorage.setItem('jktv_singer', singer);
  await window.JktvFetch('/api/reserve'+remoteQuery+'&requestId='+requestId()+'&songId=' + songId +
    '&singer=' + encodeURIComponent(singer) + '&secondSinger=' + encodeURIComponent(partner) +
    '&mode=' + encodeURIComponent(mode) + '&playNow=' + playNow);
  refreshStatus();
  }catch(e){document.getElementById('nowPlaying').textContent='Could not confirm reservation. Check the queue before retrying.';}finally{pendingCommands.delete(key);}
}

async function sendCmd(cmd) {
  if(pendingCommands.has(cmd))return;pendingCommands.add(cmd);
  try{await window.JktvFetch('/api/control'+remoteQuery+'&requestId='+requestId()+'&cmd='+encodeURIComponent(cmd));refreshStatus();}
  catch(e){document.getElementById('nowPlaying').textContent='Connection interrupted. Reconnecting…';}
  finally{pendingCommands.delete(cmd);}
}

async function refreshStatus() {
  if(!remoteActive||document.hidden||statusBusy)return;
  statusBusy=true;
  try {
    const res = await window.JktvFetch('/api/status'+remoteQuery);
    const data = await res.json();
    if (!data.ok) return;
    const np = document.getElementById('nowPlaying');
    np.textContent = data.currentTitle
      ? ('Now Singing: ' + data.currentTitle + ' (' + (data.playing ? 'Playing' : 'Paused') + ' | Key ' + data.keyTranspose + ' | ' + data.tempoPercent + '%)')
      : 'Stage Ready — Pick a song!';
    const stamp=JSON.stringify(data.queue||[]);if(stamp===queueStamp)return;queueStamp=stamp;
    const qEl = document.getElementById('queueList');
    if (!data.queue || !data.queue.length) {
      qEl.innerHTML = 'No songs queued yet.';
    } else {
      qEl.innerHTML = data.queue.map((q, idx) =>
        `<div class="song"><div><b>${idx+1}. ${escapeHtml(q.title)}</b><div class="artist">🎤 ${escapeHtml(q.singer)}${q.secondSinger ? ' & ' + escapeHtml(q.secondSinger) : ''} [${q.mode}]</div></div></div>`
      ).join('');
    }
  } catch (e) {document.getElementById('nowPlaying').textContent='Reconnecting to host…';}finally{statusBusy=false;}
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, m => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m]));
}

document.getElementById('singerInput').value = localStorage.getItem('jktv_singer') || '';
window.JktvRemoteMode=function(active){
  remoteActive=active;clearInterval(statusTimer);clearTimeout(timer);
  if(active){doSearch().catch(()=>{});refreshStatus();statusTimer=setInterval(refreshStatus,2500);}
};
document.addEventListener('visibilitychange',()=>{if(!document.hidden&&remoteActive)refreshStatus();});
if(window.JKTV_GUEST_KEY){document.getElementById('pinBadge').textContent='GUEST REMOTE';document.getElementById('stageChoice').hidden=true;}

