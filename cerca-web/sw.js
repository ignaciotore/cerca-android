const CACHE='cerca-web-v35';
const STATIC=['/','/index.html','/styles.css','/app.js','/contact-role-fix.js','/notification-flow-fix.js','/network-invite-cleanup.js','/notification-permission.js','/web-push-fix.js','/emergency-runtime-fix.js?v=35','/android-auto-call-watchdog.js?v=35','/pwa.js','/manifest.webmanifest','/icon.svg'];

self.addEventListener('install',e=>{
  e.waitUntil(caches.open(CACHE).then(c=>c.addAll(STATIC)).then(()=>self.skipWaiting()));
});

self.addEventListener('activate',e=>{
  e.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(k=>k!==CACHE).map(k=>caches.delete(k)))).then(()=>self.clients.claim()));
});

self.addEventListener('fetch',e=>{
  const r=e.request;
  if(r.method!=='GET')return;
  const u=new URL(r.url);
  if(u.origin!==location.origin)return;
  if(u.pathname.startsWith('/api/')){e.respondWith(fetch(r,{cache:'no-store'}));return}
  if(r.mode==='navigate'){
    e.respondWith(fetch(r,{cache:'no-store'}).then(res=>{const copy=res.clone();caches.open(CACHE).then(c=>c.put('/index.html',copy));return res}).catch(()=>caches.match('/index.html')));
    return;
  }
  e.respondWith(fetch(r,{cache:'no-store'}).then(res=>{if(res.ok){const copy=res.clone();caches.open(CACHE).then(c=>c.put(r,copy))}return res}).catch(()=>caches.match(r)));
});

self.addEventListener('push',e=>{
  let d={};try{d=e.data?e.data.json():{}}catch{}
  const resolved=d.event==='resolved';
  const title=d.title||(resolved?'CERCA · Emergencia finalizada':'🚨 Alerta CERCA');
  const options={
    body:d.body||(resolved?'La emergencia fue finalizada.':'Una persona de tu Red CERCA necesita ayuda. Tocá para ver la alerta.'),
    tag:(d.emergency_id||'cerca-alert')+(resolved?'-resolved':''),
    data:d
  };
  e.waitUntil(self.registration.showNotification(title,options));
});

self.addEventListener('notificationclick',e=>{
  e.notification.close();
  const d=e.notification.data||{};
  const id=d.emergency_id||'';
  const qs=new URLSearchParams();
  if(id)qs.set('alert',id);
  const target='/'+(qs.toString()?'?'+qs.toString():'');
  e.waitUntil(clients.matchAll({type:'window',includeUncontrolled:true}).then(ws=>{
    for(const w of ws){
      if('focus'in w){w.postMessage({type:'open-alert',id});return w.focus()}
    }
    return clients.openWindow(target);
  }));
});
