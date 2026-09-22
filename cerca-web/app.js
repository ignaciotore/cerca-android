const CFG={
  base:'https://yduoxeqgxolkzvjexlqk.supabase.co',
  key:'sb_publishable_XtjPnBnjESZwcUnUUAPybg_Y6LqivaD',
  network:'/functions/v1/cerca-network-v2'
};
const SESSION_KEY='cerca_web_session_v1';
const $=id=>document.getElementById(id);
const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'}[c]));
const app=$('app');
let S={session:null,user:null,profile:null,entitlement:null,billing:null,enterprise:null,activeEmergency:null,watchId:null,map:null,marker:null,trail:null,trailPoints:[],poll:null,currentAlert:null,lastIncomingId:'',tab:'home'};

function sessionLoad(){try{const x=JSON.parse(localStorage.getItem(SESSION_KEY)||'null');if(x&&x.access_token&&x.refresh_token)S.session=x}catch{}}
function sessionSave(x){S.session=x;localStorage.setItem(SESSION_KEY,JSON.stringify(x))}
function sessionClear(){S.session=null;localStorage.removeItem(SESSION_KEY)}
function token(){return S.session?.access_token||''}
function headers(auth=true){const h={'apikey':CFG.key,'Content-Type':'application/json','Accept':'application/json'};if(auth&&token())h.Authorization='Bearer '+token();return h}
function friendly(raw,status){const t=(raw?.msg||raw?.message||raw?.error_description||raw?.error||'').toString();const l=t.toLowerCase();if(l.includes('invalid login')||l.includes('invalid credentials'))return'Email o contraseña incorrectos.';if(l.includes('email not confirmed'))return'Primero confirmá tu email.';if(l.includes('already registered'))return'Ya existe una cuenta con ese email.';if(status===401)return'Tu sesión venció. Volvé a iniciar sesión.';return t||'No pudimos completar la operación.'}
async function request(path,opt={}){const r=await fetch(CFG.base+path,{method:opt.method||'GET',headers:{...headers(opt.auth!==false),...(opt.headers||{})},body:opt.body===undefined?undefined:JSON.stringify(opt.body)});let d={};const txt=await r.text();try{d=txt?JSON.parse(txt):{}}catch{d={raw:txt}}if(!r.ok)throw new Error(friendly(d,r.status));return d}
async function ensureSession(){if(!S.session)return false;const exp=Number(S.session.expires_at||0);if(exp&&Date.now()/1000<exp-90)return true;try{const d=await request('/auth/v1/token?grant_type=refresh_token',{method:'POST',auth:false,body:{refresh_token:S.session.refresh_token}});sessionSave({...d,expires_at:Math.floor(Date.now()/1000)+Number(d.expires_in||3600)});return true}catch{sessionClear();return false}}
async function authMe(){if(!await ensureSession())return null;try{const d=await request('/auth/v1/user');S.user=d;return d}catch{return null}}
function setPill(text){const p=$('connectionPill');if(p)p.textContent=text}
function toast(text,type='ok'){let x=document.createElement('div');x.className='msg '+(type==='error'?'error':'ok');x.style.display='block';x.style.position='fixed';x.style.left='50%';x.style.bottom='24px';x.style.transform='translateX(-50%)';x.style.zIndex='2000';x.style.maxWidth='calc(100% - 28px)';x.style.boxShadow='0 12px 32px rgba(0,0,0,.14)';x.textContent=text;document.body.appendChild(x);setTimeout(()=>x.remove(),3800)}
function modal(html){const w=document.createElement('div');w.className='modalWrap';w.innerHTML='<div class="modal">'+html+'</div>';w.addEventListener('click',e=>{if(e.target===w)w.remove()});document.body.appendChild(w);return w}
function closeModals(){document.querySelectorAll('.modalWrap').forEach(x=>x.remove())}
function normPhone(v){let s=String(v||'').trim().replace(/[^\d+]/g,'');if(s.startsWith('+'))return s;let d=s.replace(/\D/g,'').replace(/^0+/,'');if(d.startsWith('54'))return'+'+d;return d.length>=10?'+549'+d:s}
function arr(obj,...keys){for(const k of keys){if(Array.isArray(obj?.[k]))return obj[k]}return[]}
function first(obj,...keys){for(const k of keys){if(obj?.[k]!==undefined&&obj?.[k]!==null)return obj[k]}return null}
function recoveryFromHash(){
  try{
    const p=new URLSearchParams(location.hash.replace(/^#/,''));
    if(p.get('type')!=='recovery'||!p.get('access_token'))return null;
    return {access_token:p.get('access_token'),refresh_token:p.get('refresh_token')||'',expires_in:Number(p.get('expires_in')||3600),expires_at:Math.floor(Date.now()/1000)+Number(p.get('expires_in')||3600),token_type:p.get('token_type')||'bearer'};
  }catch{return null}
}
function renderRecovery(){
  app.innerHTML='<section class="card auth"><h1>Nueva contraseña</h1><p class="lead">Elegí una nueva contraseña para tu cuenta CERCA.</p><label>Nueva contraseña</label><input id="newPassword" class="field" type="password" autocomplete="new-password"><label>Repetir contraseña</label><input id="newPassword2" class="field" type="password" autocomplete="new-password"><button id="savePasswordBtn" class="btn primary block" style="margin-top:18px">Guardar contraseña</button><div id="authMsg" class="msg error"></div></section>';
  $('savePasswordBtn').onclick=updateRecoveredPassword;
}
async function updateRecoveredPassword(){
  const p=$('newPassword').value,p2=$('newPassword2').value,m=$('authMsg'),b=$('savePasswordBtn');
  if(p.length<8){m.textContent='La contraseña debe tener al menos 8 caracteres.';m.style.display='block';return}
  if(p!==p2){m.textContent='Las contraseñas no coinciden.';m.style.display='block';return}
  b.disabled=true;b.textContent='Guardando…';
  try{
    await request('/auth/v1/user',{method:'PUT',body:{password:p}});
    history.replaceState(null,'',location.pathname+location.search);
    sessionClear();S.user=null;S.profile=null;S.network=null;
    renderAuth('login');toast('Contraseña actualizada. Ya podés ingresar.');
  }catch(e){m.textContent=e.message||String(e);m.style.display='block'}
  finally{if($('savePasswordBtn')){$('savePasswordBtn').disabled=false;$('savePasswordBtn').textContent='Guardar contraseña'}}
}

function renderAuth(mode='login'){
  setPill('Web instalable');
  app.innerHTML='<section class="card auth"><h1>Tu red, siempre cerca.</h1><p class="lead">Ingresá con tu cuenta CERCA o creá una nueva. Esta misma cuenta funciona con la versión Android.</p><div class="tabs"><button id="tabLogin" class="'+(mode==='login'?'active':'')+'">Ingresar</button><button id="tabSignup" class="'+(mode==='signup'?'active':'')+'">Crear cuenta</button></div><div id="authBody"></div><div id="authMsg" class="msg error"></div></section>';
  $('tabLogin').onclick=()=>renderAuth('login');$('tabSignup').onclick=()=>renderAuth('signup');
  const b=$('authBody');
  if(mode==='login'){
    b.innerHTML='<label>Email</label><input id="email" class="field" type="email" autocomplete="email" placeholder="tu@email.com"><label>Contraseña</label><input id="password" class="field" type="password" autocomplete="current-password"><button id="loginBtn" class="btn primary block" style="margin-top:18px">Ingresar</button><button id="resetBtn" class="btn light block" style="margin-top:8px">Olvidé mi contraseña</button>';
    $('loginBtn').onclick=login;$('resetBtn').onclick=resetPassword;
  }else{
    b.innerHTML='<label>Nombre y apellido</label><input id="name" class="field" autocomplete="name"><div class="grid2"><div><label>Email</label><input id="email" class="field" type="email" autocomplete="email"></div><div><label>Celular</label><input id="phone" class="field" inputmode="tel" autocomplete="tel" placeholder="+54 9 11..."></div></div><label>Contraseña</label><input id="password" class="field" type="password" autocomplete="new-password"><label>Repetir contraseña</label><input id="password2" class="field" type="password" autocomplete="new-password"><button id="signupBtn" class="btn primary block" style="margin-top:18px">Crear cuenta</button>';
    $('signupBtn').onclick=signup;
  }
}
function authErr(e){const m=$('authMsg');if(m){m.textContent=e.message||String(e);m.style.display='block'}}
async function login(){const e=$('email').value.trim(),p=$('password').value;if(!e.includes('@')||!p)return authErr(new Error('Completá email y contraseña.'));const b=$('loginBtn');b.disabled=true;b.textContent='Ingresando…';try{const d=await request('/auth/v1/token?grant_type=password',{method:'POST',auth:false,body:{email:e,password:p}});sessionSave({...d,expires_at:Math.floor(Date.now()/1000)+Number(d.expires_in||3600)});await boot()}catch(err){authErr(err)}finally{b.disabled=false;b.textContent='Ingresar'}}
async function signup(){const n=$('name').value.trim(),e=$('email').value.trim(),ph=$('phone').value.trim(),p=$('password').value,p2=$('password2').value;if(n.length<2)return authErr(new Error('Ingresá tu nombre y apellido.'));if(!e.includes('@'))return authErr(new Error('Ingresá un email válido.'));if(normPhone(ph).replace(/\D/g,'').length<9)return authErr(new Error('Ingresá tu celular.'));if(p.length<8)return authErr(new Error('La contraseña debe tener al menos 8 caracteres.'));if(p!==p2)return authErr(new Error('Las contraseñas no coinciden.'));const b=$('signupBtn');b.disabled=true;b.textContent='Creando…';try{let family=null;try{family=await request('/functions/v1/cerca-family-signup',{method:'POST',auth:false,body:{full_name:n,email:e,password:p,phone_e164:normPhone(ph)}})}catch{}if(family?.session?.access_token){const x=family.session;sessionSave({...x,expires_at:Math.floor(Date.now()/1000)+Number(x.expires_in||3600)});await boot();return}const d=await request('/auth/v1/signup',{method:'POST',auth:false,body:{email:e,password:p,data:{full_name:n,phone_e164:normPhone(ph)}}});if(d.access_token){sessionSave({...d,expires_at:Math.floor(Date.now()/1000)+Number(d.expires_in||3600)});await boot()}else{renderAuth('login');toast('Cuenta creada. Revisá tu email para confirmarla.')}}catch(err){authErr(err)}finally{if($('signupBtn')){$('signupBtn').disabled=false;$('signupBtn').textContent='Crear cuenta'}}}
async function resetPassword(){const e=$('email').value.trim();if(!e.includes('@'))return authErr(new Error('Escribí tu email primero.'));try{const redirect=location.origin+location.pathname;await request('/auth/v1/recover?redirect_to='+encodeURIComponent(redirect),{method:'POST',auth:false,body:{email:e}});toast('Te enviamos el enlace para recuperar la contraseña.')}catch(err){authErr(err)}}

async function loadProfile(){
  const uid=S.user?.id;if(!uid)return null;
  try{const d=await request('/rest/v1/profiles?user_id=eq.'+encodeURIComponent(uid)+'&select=full_name,email,trial_started_at,trial_ends_at,phone_e164');S.profile=Array.isArray(d)?d[0]||null:d;return S.profile}catch{return null}
}
async function loadEntitlement(){
  const uid=S.user?.id;if(!uid)return null;
  try{const d=await request('/rest/v1/entitlements?user_id=eq.'+encodeURIComponent(uid)+'&select=subscription_active,expires_at');S.entitlement=Array.isArray(d)?d[0]||null:d;return S.entitlement}catch{S.entitlement=null;return null}
}
async function loadBilling(){
  if(!token())return null;
  try{
    const r=await fetch('/api/billing?action=status',{headers:{Authorization:'Bearer '+token(),Accept:'application/json'},cache:'no-store'});
    const d=await r.json().catch(()=>({}));
    S.billing=d;return d;
  }catch{S.billing={configured:false};return S.billing}
}
async function enterpriseCall(action,body=null,method='POST'){return request('/functions/v1/cerca-enterprise?action='+encodeURIComponent(action),{method,body:body||undefined})}
async function loadEnterprise(){try{S.enterprise=await enterpriseCall('state',null,'GET');return S.enterprise}catch{S.enterprise={enterprise:false};return S.enterprise}}
function trialEndMs(){const x=S.profile?.trial_ends_at||S.billing?.trial_ends_at;const n=x?Date.parse(x):0;return Number.isFinite(n)?n:0}
function trialActive(){return trialEndMs()>Date.now()}
function trialDaysLeft(){const d=trialEndMs()-Date.now();return d>0?Math.max(1,Math.ceil(d/86400000)):0}
function legacyPremiumActive(){
  const sub=S.entitlement,end=sub?.expires_at?Date.parse(sub.expires_at):0;
  return !!(sub?.subscription_active&&end>Date.now());
}
function mpPremiumActive(){return !!S.billing?.subscription?.active}
function enterpriseAccess(){return !!S.enterprise?.enterprise_access_active}
function fullAccess(){return enterpriseAccess()||trialActive()||legacyPremiumActive()||mpPremiumActive()}
function planLabel(){
  if(enterpriseAccess())return'Empresa';
  if(mpPremiumActive()||legacyPremiumActive())return'Premium';
  if(trialActive())return'Prueba activa';
  return'Free';
}
function subscriptionStatusText(){
  if(enterpriseAccess())return'Incluido por tu empresa';
  if(mpPremiumActive())return'Suscripción activa';
  if(legacyPremiumActive())return'Premium activo';
  if(trialActive())return'Prueba · '+trialDaysLeft()+' día(s) restante(s)';
  return'Prueba finalizada';
}
function premiumWall(title){
  const configured=!!S.billing?.configured;
  return '<div class="subscriptionWall"><div class="subscriptionLock">🔒</div><h2>'+esc(title)+'</h2><p>Esta función requiere CERCA Premium.</p><div class="subscriptionPrice">$25.000 <span>ARS / mes</span></div><p class="mini">El SOS y las alertas de emergencia siguen disponibles.</p><button class="btn primary block" data-subscribe '+(configured?'':'disabled')+'>'+(configured?'Activar suscripción':'Mercado Pago pendiente de vinculación')+'</button></div>';
}
async function startSubscription(){
  if(!S.billing?.configured)return toast('Falta vincular la cuenta de Mercado Pago para habilitar el débito automático.','error');
  try{
    setPill('Abriendo Mercado Pago…');
    const r=await fetch('/api/billing?action=checkout',{method:'POST',headers:{Authorization:'Bearer '+token(),Accept:'application/json'}});
    const d=await r.json().catch(()=>({}));
    if(!r.ok)throw new Error(d.error||'No pudimos iniciar la suscripción.');
    if(d.already_active){await loadBilling();toast('Tu suscripción ya está activa.');renderSide();return}
    if(d.checkout_url){location.href=d.checkout_url;return}
    throw new Error('Mercado Pago no devolvió el checkout.');
  }catch(e){toast(e.message||String(e),'error')}finally{setPill('CERCA activa')}
}
function applyEnterpriseBrand(){
  const org=S.enterprise?.organization;
  if(!S.enterprise?.enterprise||!org)return;
  const primary=org.primary_color||org.primaryColor||'';
  const secondary=org.secondary_color||org.secondaryColor||'';
  const accent=org.accent_color||org.accentColor||'';
  if(/^#[0-9a-f]{6}$/i.test(primary))document.documentElement.style.setProperty('--teal',primary);
  if(/^#[0-9a-f]{6}$/i.test(secondary))document.documentElement.style.setProperty('--mint',secondary);
  if(/^#[0-9a-f]{6}$/i.test(accent))document.documentElement.style.setProperty('--red',accent);
}
function enterpriseBrandBanner(){
  const org=S.enterprise?.organization;
  if(!S.enterprise?.enterprise||!org)return'';
  const logo=String(org.logo_url||org.logoUrl||'').trim();
  const role=S.enterprise.role==='admin'?'CERCA EMPRESAS · Administrador':'CERCA EMPRESAS';
  return '<div class="enterpriseBrand">'+
    (logo?'<div class="enterpriseLogo"><img src="'+esc(logo)+'" alt="'+esc(org.name||'Empresa')+'" referrerpolicy="no-referrer"></div>':'')+
    '<div class="enterpriseBrandCopy"><strong>'+esc(org.name||'CERCA Empresas')+'</strong><span>'+role+'</span></div>'+
  '</div>';
}
async function networkCall(action,body=null,method='POST'){const q='?action='+encodeURIComponent(action);return request(CFG.network+q,{method,body:body||undefined})}
async function loadNetwork(){try{S.network=await networkCall('state',null,'GET');S.activeEmergency=S.network?.active_emergency||null;return S.network}catch(e){console.warn(e);return null}}

function renderDashboard(){
  destroyMap();
  const name=S.profile?.full_name||S.user?.user_metadata?.full_name||S.user?.email?.split('@')[0]||'';
  const orgLine=enterpriseBrandBanner();
  app.innerHTML='<div class="dash"><section class="card panel mainPanel">'+orgLine+'<div class="hello"><div><h1>Hola, '+esc(name)+'</h1><p>Tu Red CERCA está lista para acompañarte.</p></div><button id="profileBtn" class="btn light sm">Mi cuenta</button></div><nav class="nav"><button data-tab="home" class="active">Inicio</button><button data-tab="network">Mi Red</button><button data-tab="info">Información útil</button><button data-tab="alerts">Alertas</button><button data-tab="enterprise">Empresa</button></nav><div id="sideContent"></div></section><section id="alertMapCard" class="card mapCard alertMapCard hidden"><div class="mapHeader"><div><strong id="mapTitle">Alerta CERCA</strong><div id="mapMeta" class="mapMeta">Esperando ubicación…</div></div><span id="liveBadge" class="badge red">SOS ACTIVO</span></div><div id="map"></div><div class="mapFooter"><span id="mapFooterText" class="mapMeta">Seguimiento de la persona que activó la alerta.</span><div class="actions"><button id="centerMapBtn" class="btn light sm">Centrar</button><a id="mapsLink" class="btn light sm hidden" target="_blank" rel="noopener">Abrir en Maps</a><button id="hideMapBtn" class="btn light sm">Ocultar mapa</button></div></div></section></div>';
  document.querySelectorAll('.nav button').forEach(b=>b.onclick=()=>{S.tab=b.dataset.tab;document.querySelectorAll('.nav button').forEach(x=>x.classList.toggle('active',x===b));renderSide()});
  $('profileBtn').onclick=()=>{S.tab='profile';document.querySelectorAll('.nav button').forEach(x=>x.classList.remove('active'));renderSide()};
  $('centerMapBtn').onclick=centerMap;
  $('hideMapBtn').onclick=hideAlertMap;
  renderSide();startPolling();
}
function destroyMap(){
  if(S.map){try{S.map.remove()}catch{}}
  S.map=null;S.marker=null;S.trail=null;S.trailPoints=[];
}
function initMap(){
  const el=$('map');if(!el)return;
  if(S.map){setTimeout(()=>S.map.invalidateSize(),50);return}
  S.map=L.map('map',{zoomControl:true,attributionControl:true}).setView([-34.60,-58.44],13);
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap'}).addTo(S.map);
  setTimeout(()=>S.map?.invalidateSize(),120);
}
function alertMarkerIcon(){
  return L.divIcon({className:'cercaMapPinWrap',html:'<div class="cercaMapPin"><span></span></div>',iconSize:[34,42],iconAnchor:[17,39]});
}
function setMapPoint(lat,lon,opt={}){
  if(!Number.isFinite(+lat)||!Number.isFinite(+lon))return;
  initMap();if(!S.map)return;
  const p=[+lat,+lon];
  if(!S.marker)S.marker=L.marker(p,{icon:alertMarkerIcon()}).addTo(S.map);else S.marker.setLatLng(p);
  if(opt.trail){
    const last=S.trailPoints[S.trailPoints.length-1];
    if(!last||Math.abs(last[0]-p[0])>0.000001||Math.abs(last[1]-p[1])>0.000001)S.trailPoints.push(p);
    if(S.trailPoints.length>120)S.trailPoints.shift();
    const routeColor=getComputedStyle(document.documentElement).getPropertyValue('--teal').trim()||'#0b666d';
    if(S.trail)S.trail.setLatLngs(S.trailPoints);else S.trail=L.polyline(S.trailPoints,{color:routeColor,weight:5,opacity:.78,lineCap:'round',lineJoin:'round'}).addTo(S.map);
  }
  if(opt.center!==false)S.map.setView(p,opt.zoom||16);
  const link=$('mapsLink');if(link){link.href='https://maps.google.com/?q='+p[0]+','+p[1];link.classList.remove('hidden')}
}
function centerMap(){if(S.map&&S.marker)S.map.setView(S.marker.getLatLng(),16)}
function clearMapTrail(){
  S.trailPoints=[];
  if(S.trail&&S.map){try{S.map.removeLayer(S.trail)}catch{}}
  S.trail=null;
}
function showAlertMap(a,{resetTrail=false,center=true}={}){
  if(!a)return;
  const card=$('alertMapCard');if(!card)return;
  card.classList.remove('hidden');
  initMap();
  const name=a.person_name||a.full_name||'Contacto CERCA';
  $('mapTitle').textContent='🚨 '+name+' necesita ayuda';
  $('liveBadge').textContent='SOS ACTIVO';$('liveBadge').className='badge red';
  $('mapFooterText').textContent='La ubicación se actualiza mientras la emergencia siga activa.';
  if(resetTrail)clearMapTrail();
  const lat=+first(a,'latitude','lat'),lon=+first(a,'longitude','lon','lng');
  if(Number.isFinite(lat)&&Number.isFinite(lon)){
    setMapPoint(lat,lon,{center,trail:true});
    $('mapMeta').textContent='Ubicación actualizada · '+new Date().toLocaleTimeString('es-AR');
  }else{
    $('mapMeta').textContent='Esperando la ubicación del contacto…';
  }
  setTimeout(()=>S.map?.invalidateSize(),80);
  if(center)card.scrollIntoView({behavior:'smooth',block:'start'});
}
function hideAlertMap(){const card=$('alertMapCard');if(card)card.classList.add('hidden')}
function renderSide(){
  const el=$('sideContent');if(!el)return;
  if(S.tab==='home')return renderHome(el);
  if(S.tab==='network')return renderNetwork(el);
  if(S.tab==='info')return renderInfo(el);
  if(S.tab==='alerts')return renderAlerts(el);
  if(S.tab==='enterprise')return renderEnterprise(el);
  if(S.tab==='profile')return renderProfile(el);
}
function renderHome(el){
  const active=!!S.activeEmergency;
  el.innerHTML='<div class="sosZone"><button id="sosBtn" class="sosButton '+(active?'active':'')+'">'+(active?'SOS ACTIVO':'PEDIR<br>AYUDA')+'</button><div class="sosHint">'+(active?'Tu ubicación se está actualizando mientras CERCA permanezca activa.':'Mantené apretado 2 segundos. Tendrás una cuenta regresiva para cancelar.')+'</div><div class="actions" style="justify-content:center"><button id="silentBtn" class="btn light">'+(active?'Seguimiento activo':'SOS silencioso')+'</button>'+(active?'<button id="resolveBtn" class="btn successBtn">ESTOY BIEN · FINALIZAR</button>':'')+'</div></div><div class="statusBox"><div class="statusLine"><span class="dot '+(active?'live':'')+'"></span><span>'+(active?'Emergencia activa':'Protección lista')+'</span></div><div class="mini">'+(active?'CERCA seguirá enviando nuevas posiciones al backend mientras el navegador permita el seguimiento.':'Al activar SOS avisaremos a tu Red CERCA con tu ubicación disponible.')+'</div></div>';
  const b=$('sosBtn');
  if(active){
    b.onclick=()=>toast('La emergencia ya está activa.');
  }else{
    let timer=null,triggered=false;
    const cancelPress=()=>{
      if(timer){clearTimeout(timer);timer=null}
      b.classList.remove('pressing');
    };
    b.oncontextmenu=e=>{e.preventDefault();return false};
    b.onselectstart=e=>{e.preventDefault();return false};
    b.onpointerdown=e=>{
      e.preventDefault();
      if(e.button!==undefined&&e.button!==0)return;
      triggered=false;
      try{b.setPointerCapture?.(e.pointerId)}catch{}
      b.classList.add('pressing');
      timer=setTimeout(()=>{
        timer=null;triggered=true;b.classList.remove('pressing');confirmSOS(false);
      },2000);
    };
    b.onpointerup=e=>{
      e.preventDefault();
      try{b.releasePointerCapture?.(e.pointerId)}catch{}
      if(!triggered)cancelPress();
    };
    b.onpointercancel=cancelPress;
    b.onpointerleave=e=>{if(e.pointerType==='mouse')cancelPress()};
    b.onclick=e=>e.preventDefault();
  }
  $('silentBtn').onclick=()=>active?toast('La emergencia ya está activa.'):confirmSOS(true);
  if($('resolveBtn'))$('resolveBtn').onclick=resolveEmergency;
}
function confirmSOS(silent){
  let n=5;const w=modal('<h2>'+(silent?'Activar SOS silencioso':'Pedir ayuda')+'</h2><p>'+(silent?'Avisará a tu Red CERCA y compartirá tu ubicación sin iniciar una llamada desde esta versión web.':'Avisará a tu Red CERCA y comenzará a compartir tu ubicación.')+'</p><div id="count" class="countdown">5</div><button id="cancelSos" class="btn light block">CANCELAR</button>');
  const t=setInterval(()=>{n--;if($('count'))$('count').textContent=String(n);if(n<=0){clearInterval(t);w.remove();startEmergency(silent)}},1000);$('cancelSos').onclick=()=>{clearInterval(t);w.remove()}
}
function getPos(){return new Promise((res,rej)=>{if(!navigator.geolocation)return rej(new Error('Este dispositivo no ofrece ubicación.'));navigator.geolocation.getCurrentPosition(p=>res(p.coords),e=>rej(new Error(e.code===1?'Necesito permiso de ubicación para activar CERCA.':'No pude obtener tu ubicación.')),{enableHighAccuracy:true,timeout:15000,maximumAge:10000})})}
async function startEmergency(silent){
  setPill('Activando SOS…');
  try{
    const c=await getPos();
    const d=await networkCall('emergency_start',{mode:silent?'silent':'normal',latitude:c.latitude,longitude:c.longitude});
    S.activeEmergency=d.emergency||d.active_emergency||d;
    startTracking();
    toast('SOS activado. Tu Red CERCA fue alertada.');
    await loadNetwork();renderSide();renderBelow();
  }catch(e){toast(e.message,'error')}finally{setPill('CERCA activa')}
}
function startTracking(){
  stopTracking();if(!navigator.geolocation)return;let last=0;
  S.watchId=navigator.geolocation.watchPosition(async p=>{
    const now=Date.now(),c=p.coords;
    if(now-last<4000)return;last=now;
    const id=S.activeEmergency?.id||S.network?.active_emergency?.id;if(!id)return;
    try{await networkCall('emergency_update',{emergency_id:id,latitude:c.latitude,longitude:c.longitude})}catch{}
  },()=>{},{enableHighAccuracy:true,maximumAge:2500,timeout:12000})
}
function stopTracking(){if(S.watchId!==null&&navigator.geolocation){navigator.geolocation.clearWatch(S.watchId);S.watchId=null}}
async function resolveEmergency(){const id=S.activeEmergency?.id||S.network?.active_emergency?.id;if(!id)return;try{await networkCall('emergency_resolve',{emergency_id:id});stopTracking();S.activeEmergency=null;await loadNetwork();toast('Emergencia finalizada.');renderSide();renderBelow()}catch(e){toast(e.message,'error')}}

function phoneKey(v){
  const d=String(v||'').replace(/\D/g,'');
  if(!d)return'';
  return d.length>=10?d.slice(-10):d.replace(/^0+/,'');
}
function contactKey(x){
  const p=phoneKey(first(x,'phone_e164','phone','target_phone','contact_phone'));
  if(p)return'p:'+p;
  const uid=first(x,'target_user_id','other_user_id','member_user_id','contact_user_id','owner_user_id');
  if(uid)return'u:'+String(uid);
  const n=String(x.display_name||x.full_name||x.name||x.email||'').trim().toLowerCase();
  return'n:'+n;
}
function mergeNetworkContacts(items){
  const m=new Map();
  for(const x of (items||[])){
    const k=contactKey(x);
    if(!k||k==='n:')continue;
    const linkIds=[...(x.__linkIds||[])];
    const contactIds=[...(x.__contactIds||[])];
    const rawId=x.link_id||x.id||'';
    if(rawId){
      if(x.__kind==='link')linkIds.push(rawId);
      else if(x.__kind==='synced')contactIds.push(rawId);
    }
    const prev=m.get(k);
    if(!prev){
      const copy={...x,__linkIds:[...new Set(linkIds)],__contactIds:[...new Set(contactIds)]};
      delete copy.__kind;
      m.set(k,copy);
    }else{
      prev.__linkIds=[...new Set([...(prev.__linkIds||[]),...linkIds])];
      prev.__contactIds=[...new Set([...(prev.__contactIds||[]),...contactIds])];
      for(const [key,val] of Object.entries(x)){
        if(key==='__kind'||key==='__linkIds'||key==='__contactIds')continue;
        if((prev[key]===undefined||prev[key]===null||prev[key]==='')&&val!==undefined&&val!==null&&val!=='')prev[key]=val;
      }
      if(x.call_enabled===true)prev.call_enabled=true;
      if(x.sms_enabled===true)prev.sms_enabled=true;
      if(x.has_cerca===true)prev.has_cerca=true;
      const medRank={never:0,emergency:1,always:2};
      if((medRank[x.medical_access]||0)>(medRank[prev.medical_access]||0))prev.medical_access=x.medical_access;
    }
  }
  return [...m.values()];
}
function networkCollections(){
  const n=S.network||{};
  const syncedRaw=arr(n,'contacts','outgoing').map(x=>({...x,__kind:'synced'}));
  const linkedRaw=arr(n,'links','network_links','members').map(x=>({...x,__kind:'link'}));
  const synced=mergeNetworkContacts(syncedRaw);
  const linked=mergeNetworkContacts([...linkedRaw,...syncedRaw]);
  return{
    links:linked,
    syncedContacts:synced,
    invites:arr(n,'invitations','pending_invites','invites'),
    alerts:arr(n,'incoming_alerts','alerts')
  }
}
function designatedCall(){
  const all=networkCollections().syncedContacts||[];
  return all.find(c=>c.call_enabled===true)||null;
}
function syncedRoleFor(x){
  const all=networkCollections().syncedContacts||[];
  const k=contactKey(x);
  const px=phoneKey(first(x,'phone_e164','phone','target_phone','contact_phone'));
  const matches=all.filter(c=>contactKey(c)===k||(px&&phoneKey(first(c,'phone_e164','phone'))===px));
  const direct=(x.sms_enabled!==undefined||x.call_enabled!==undefined)?[x]:[];
  const pool=matches.length?matches:direct;
  const call=designatedCall();
  const callPhone=phoneKey(first(call,'phone_e164','phone','target_phone','contact_phone'));
  const isCall=!!call&&((px&&callPhone&&px===callPhone)||contactKey(call)===k);
  const hasSms=pool.some(c=>c.sms_enabled===true||c.call_enabled===true);
  return{
    call:isCall,
    sms:!isCall&&hasSms,
    cerca:(x.__linkIds||[]).length>0||pool.some(c=>c.has_cerca===true)||!!(x.owner_user_id||x.target_user_id)
  }
}
function roleBadges(x){
  const r=syncedRoleFor(x),out=[];
  if(r.call)out.push('<span class="contactRole call">📞 Contacto de llamada</span>');
  else if(r.sms)out.push('<span class="contactRole sms">✉️ Contacto SMS</span>');
  if(r.cerca)out.push('<span class="contactRole cerca">🔔 Alerta CERCA</span>');
  return '<div class="contactRoles">'+out.join('')+'</div>';
}
function renderNetwork(el){
  if(!fullAccess()){
    el.innerHTML=premiumWall('Mi Red CERCA');
    el.querySelector('[data-subscribe]')?.addEventListener('click',startSubscription);
    return;
  }
  const c=networkCollections();
  el.innerHTML='<div class="sectionHead"><div><h2>Mi Red CERCA</h2><p>Configurá 1 contacto de llamada y el resto para SMS. Si usan CERCA, además reciben la alerta dentro de la app.</p></div><div class="networkAddActions"><button id="addContactBtn" class="btn primary sm">+ Agregar contacto</button><button id="inviteCercaBtn" class="btn light sm">Invitar a CERCA</button></div></div><div class="list">'+(c.links.length?c.links.map(linkCard).join(''):'<div class="empty">Todavía no hay contactos configurados.</div>')+'</div><div style="margin-top:16px"><button id="acceptInvite" class="btn light block">Tengo un código de invitación</button></div>';
  $('addContactBtn').onclick=addContactModal;$('inviteCercaBtn').onclick=newInviteModal;$('acceptInvite').onclick=acceptInviteModal;
  el.querySelectorAll('[data-remove]').forEach(b=>b.onclick=()=>removeLink(b.dataset.remove));
  el.querySelectorAll('[data-remove-contact]').forEach(b=>b.onclick=()=>removeSyncedContact(b.dataset.removeContact));
  el.querySelectorAll('[data-medical]').forEach(s=>s.onchange=()=>setMedicalAccess(s.dataset.medical,s.value));
  el.querySelectorAll('[data-make-call]').forEach(b=>b.onclick=()=>makeCallContact(b.dataset.makeCall));
}

function linkCard(x){
  const linkIds=(x.__linkIds||[]).filter(Boolean);
  const linkId=linkIds.join(',');
  const n=x.display_name||x.full_name||x.name||x.email||'Contacto CERCA';
  const rel=x.relationship||x.relation||'Red CERCA';
  const med=x.medical_access||'never';
  const phone=first(x,'phone_e164','phone','target_phone','contact_phone');
  const roles=syncedRoleFor(x);
  const roleAction=phone&&!roles.call?'<button class="btn light sm" data-make-call="'+esc(phone)+'">Usar para llamada</button>':'';
  const medicalBlock=roles.cerca&&linkId
    ?'<label style="margin-top:10px">Información útil</label><select class="select" data-medical="'+esc(linkId)+'"><option value="never" '+(med==='never'?'selected':'')+'>No compartir</option><option value="emergency" '+(med==='emergency'?'selected':'')+'>Solo durante una emergencia</option><option value="always" '+(med==='always'?'selected':'')+'>Siempre autorizada</option></select>'
    :'';
  const removeAction=linkId
    ?'<button class="btn light sm" data-remove="'+esc(linkId)+'">Quitar de la red</button>'
    :(phone?'<button class="btn light sm" data-remove-contact="'+esc(phone)+'">Quitar contacto</button>':'');
  return'<div class="item"><div class="itemTop"><div><h3>'+esc(n)+'</h3><p>'+esc(rel)+(phone?' · '+esc(phone):'')+'</p>'+roleBadges(x)+'</div>'+(roles.cerca?'<span class="badge">'+(med==='never'?'Info privada':med==='emergency'?'Solo en emergencia':'Info autorizada')+'</span>':'')+'</div>'+medicalBlock+'<div class="itemActions">'+roleAction+removeAction+'</div></div>'
}

function syncedPayload(){
  const all=networkCollections().syncedContacts||[];
  return all.map((x,i)=>({
    slot:i+1,
    name:x.name||x.display_name||x.full_name||'Contacto',
    phone:first(x,'phone','phone_e164','target_phone','contact_phone')||'',
    sms_enabled:x.sms_enabled===true,
    call_enabled:x.call_enabled===true,
    medical_access:x.medical_access||'never'
  })).filter(x=>phoneKey(x.phone));
}
async function saveSyncedContacts(list){
  const clean=mergeNetworkContacts(list.map(x=>({...x,phone_e164:x.phone}))).slice(0,4).map((x,i)=>({
    slot:i+1,
    name:x.name||x.display_name||'Contacto',
    phone:first(x,'phone','phone_e164')||'',
    sms_enabled:x.call_enabled===true?false:x.sms_enabled!==false,
    call_enabled:x.call_enabled===true,
    medical_access:x.medical_access||'never'
  }));
  if(clean.filter(x=>x.call_enabled).length>1){
    let seen=false;
    clean.forEach(x=>{if(x.call_enabled&&!seen)seen=true;else if(x.call_enabled){x.call_enabled=false;x.sms_enabled=true}})
  }
  await networkCall('sync_contacts',{contacts:clean});
  await loadNetwork();renderSide();renderBelow();
}
function addContactModal(){
  if((networkCollections().syncedContacts||[]).length>=4)return toast('Ya tenés 4 contactos configurados.','error');
  const w=modal('<h2>Agregar contacto</h2><p>Por defecto quedará como contacto SMS. Solo una persona puede ser el contacto de llamada.</p><button id="pickPhoneContact" class="btn light block">Elegir de mis contactos</button><label>Nombre</label><input id="contactName" class="field" autocomplete="name"><label>Teléfono</label><input id="contactPhone" class="field" inputmode="tel" autocomplete="tel" placeholder="+54 9 11..."><label>Función</label><select id="contactRole" class="select"><option value="sms">Contacto SMS</option><option value="call">Contacto de llamada</option></select><button id="saveContactBtn" class="btn primary block" style="margin-top:16px">Guardar contacto</button>');
  const pick=$('pickPhoneContact');
  if(!navigator.contacts?.select)pick.style.display='none';
  else pick.onclick=async()=>{try{const r=await navigator.contacts.select(['name','tel'],{multiple:false});const p=r?.[0];if(p){$('contactName').value=p.name?.[0]||'';$('contactPhone').value=p.tel?.[0]||''}}catch{}};
  $('saveContactBtn').onclick=async()=>{
    const name=$('contactName').value.trim(),phone=$('contactPhone').value.trim(),role=$('contactRole').value;
    if(name.length<2)return toast('Ingresá el nombre del contacto.','error');
    if(phoneKey(phone).length<8)return toast('Ingresá un teléfono válido.','error');
    const list=syncedPayload();
    if(list.some(x=>phoneKey(x.phone)===phoneKey(phone)))return toast('Ese contacto ya está agregado.','error');
    if(role==='call')list.forEach(x=>{if(x.call_enabled){x.call_enabled=false;x.sms_enabled=true}});
    list.push({slot:list.length+1,name,phone,sms_enabled:role!=='call',call_enabled:role==='call',medical_access:'never'});
    try{await saveSyncedContacts(list);w.remove();toast('Contacto agregado.')}catch(e){toast(e.message,'error')}
  };
}
async function makeCallContact(phone){
  const list=syncedPayload();
  const key=phoneKey(phone);
  let found=false;
  list.forEach(x=>{
    const is=phoneKey(x.phone)===key;
    if(is)found=true;
    x.call_enabled=is;
    x.sms_enabled=!is;
  });
  if(!found)return toast('No encontré ese contacto.','error');
  try{await saveSyncedContacts(list);toast('Contacto de llamada actualizado.')}catch(e){toast(e.message,'error')}
}

async function removeSyncedContact(phone){
  if(!confirm('¿Quitar este contacto de tu configuración de emergencia?'))return;
  const key=phoneKey(phone);
  const list=syncedPayload().filter(x=>phoneKey(x.phone)!==key);
  if(list.length&&!list.some(x=>x.call_enabled)){
    list[0].call_enabled=true;
    list[0].sms_enabled=false;
  }
  try{await saveSyncedContacts(list);toast('Contacto eliminado.')}catch(e){toast(e.message,'error')}
}

function newInviteModal(){const w=modal('<h2>Invitar a Mi Red CERCA</h2><p>Generá una invitación para una persona de confianza.</p><label>Nombre</label><input id="invName" class="field"><label>Relación</label><input id="invRel" class="field" placeholder="Ej: hermana, amigo, pareja"><label>Información útil ante una emergencia</label><select id="invMed" class="select"><option value="never">No compartir</option><option value="emergency">Solo durante una emergencia</option><option value="always">Siempre autorizada</option></select><button id="makeInvite" class="btn primary block" style="margin-top:16px">Generar invitación</button>');$('makeInvite').onclick=async()=>{try{const d=await networkCall('create_invite',{display_name:$('invName').value.trim(),relationship:$('invRel').value.trim(),medical_access:$('invMed').value});const code=d.code||d.invite_code||d.invitation?.code||'';w.innerHTML='<div class="modal"><h2>Invitación creada</h2><p>Compartí este código con la persona:</p><div class="countdown" style="font-size:44px;color:var(--teal)">'+esc(code||'Creada')+'</div><button id="closeInvite" class="btn primary block">Listo</button></div>';$('closeInvite').onclick=()=>{w.remove();refreshAll()}}catch(e){toast(e.message,'error')}}}
function acceptInviteModal(){const w=modal('<h2>Unirme a una Red CERCA</h2><p>Ingresá el código que te compartieron.</p><input id="inviteCode" class="field" style="text-transform:uppercase" placeholder="CÓDIGO"><button id="acceptCode" class="btn primary block" style="margin-top:14px">Aceptar invitación</button>');$('acceptCode').onclick=async()=>{try{await networkCall('accept_code',{code:$('inviteCode').value.trim().toUpperCase()});w.remove();toast('Ya sos parte de esa Red CERCA.');refreshAll()}catch(e){toast(e.message,'error')}}}
async function removeLink(ids){
  if(!confirm('¿Quitar a esta persona de tu Red CERCA?'))return;
  const list=String(ids||'').split(',').filter(Boolean);
  try{
    for(const id of list)await networkCall('remove_link',{link_id:id});
    toast('Contacto eliminado de la red.');refreshAll();
  }catch(e){toast(e.message,'error')}
}
async function setMedicalAccess(ids,value){
  const list=String(ids||'').split(',').filter(Boolean);
  try{
    for(const id of list)await networkCall('set_medical_access',{link_id:id,medical_access:value});
    toast('Permiso actualizado.');await loadNetwork();renderSide();renderBelow();
  }catch(e){toast(e.message,'error');refreshAll()}
}

async function fetchMedical(){try{const uid=S.user.id;const d=await request('/rest/v1/medical_profiles?user_id=eq.'+encodeURIComponent(uid)+'&select=*');return Array.isArray(d)?d[0]||null:d}catch{return null}}
async function renderInfo(el){
  if(!fullAccess()){
    el.innerHTML=premiumWall('Información útil ante una emergencia');
    el.querySelector('[data-subscribe]')?.addEventListener('click',startSubscription);
    return;
  }
  el.innerHTML='<div class="sectionHead"><div><h2>Información útil ante una emergencia</h2><p>Campo opcional que podés compartir con personas autorizadas.</p></div></div><div class="infoNote">Podés escribir alergias, medicación, cobertura, contacto alternativo o cualquier dato que consideres útil. CERCA no brinda diagnóstico ni reemplaza la atención médica.</div><label>Información</label><textarea id="infoText" class="textarea" placeholder="Ej: Alergia a penicilina. Cobertura..."></textarea><label><input id="shareInfo" type="checkbox"> Permitir compartirla cuando corresponda según Mi Red CERCA</label><button id="saveInfo" class="btn primary block" style="margin-top:14px">Guardar</button>';
  const m=await fetchMedical();if($('infoText')){$('infoText').value=m?.notes||'';$('shareInfo').checked=!!m?.share_enabled;$('saveInfo').onclick=()=>saveInfo(m)}
}
async function saveInfo(prev){const uid=S.user.id;const body={user_id:uid,full_name:prev?.full_name||S.profile?.full_name||S.user?.user_metadata?.full_name||'',birth_date:prev?.birth_date||null,blood_type:prev?.blood_type||'',allergies:prev?.allergies||'',medications:prev?.medications||'',conditions:prev?.conditions||'',health_provider:prev?.health_provider||'',member_number:prev?.member_number||'',emergency_contact_name:prev?.emergency_contact_name||'',emergency_contact_phone:prev?.emergency_contact_phone||'',notes:$('infoText').value.trim(),share_enabled:$('shareInfo').checked,updated_at:new Date().toISOString()};try{await request('/rest/v1/medical_profiles?on_conflict=user_id',{method:'POST',body,headers:{Prefer:'resolution=merge-duplicates,return=representation'}});toast('Información guardada.')}catch(e){toast(e.message,'error')}}

function renderAlerts(el){
  const alerts=networkCollections().alerts;
  el.innerHTML='<div class="sectionHead"><div><h2>Alertas de Mi Red</h2><p>Emergencias activas de las personas vinculadas.</p></div></div><div class="list">'+(alerts.length?alerts.map(alertCard).join(''):'<div class="empty">No hay alertas activas en este momento.</div>')+'</div>';
  el.querySelectorAll('[data-alert]').forEach(b=>b.onclick=()=>openAlert(b.dataset.alert));
  el.querySelectorAll('[data-alert-info]').forEach(b=>b.onclick=()=>openAlertInfo(b.dataset.alertInfo));
}

function alertCard(a){const id=a.id||a.emergency_id||'';const name=a.person_name||a.full_name||'Contacto CERCA';const lat=first(a,'latitude','lat'),lon=first(a,'longitude','lon','lng'),med=a.medical_access||'never';return'<div class="item"><div class="itemTop"><div><h3>🚨 '+esc(name)+' necesita ayuda</h3><p>'+(lat!=null&&lon!=null?'Ubicación disponible y actualizable.':'Esperando ubicación…')+'</p></div><span class="badge red">SOS ACTIVO</span></div><div class="itemActions"><button class="btn danger sm" data-alert="'+esc(id)+'">Ver emergencia</button>'+(med!=='never'&&a.owner_user_id?'<button class="btn light sm" data-alert-info="'+esc(id)+'">Información útil</button>':'')+'</div></div>'}

async function openAlert(id){
  await loadNetwork();
  const a=networkCollections().alerts.find(x=>String(x.id||x.emergency_id)===String(id))||networkCollections().alerts[0];
  if(!a)return toast('La alerta ya no está activa.');
  const previousId=S.currentAlert&&(S.currentAlert.id||S.currentAlert.emergency_id);
  S.currentAlert=a;
  try{await networkCall('emergency_seen',{emergency_id:a.id||a.emergency_id})}catch{}
  showAlertMap(a,{resetTrail:String(previousId||'')!==String(a.id||a.emergency_id),center:true});
}
function syncCurrentAlertFromState(){
  if(!S.currentAlert)return;
  const id=S.currentAlert.id||S.currentAlert.emergency_id;
  const a=networkCollections().alerts.find(x=>String(x.id||x.emergency_id)===String(id));
  if(!a){S.currentAlert=null;hideAlertMap();clearMapTrail();return}
  S.currentAlert=a;showAlertMap(a,{center:false});
}
async function openAlertInfo(id){
  await loadNetwork();
  const a=networkCollections().alerts.find(x=>String(x.id||x.emergency_id)===String(id));
  if(!a||!a.owner_user_id)return toast('La información ya no está disponible.','error');
  try{
    const d=await request(CFG.network+'?action=medical&owner_user_id='+encodeURIComponent(a.owner_user_id),{method:'GET'});
    const m=d?.medical;if(!m)return toast('Todavía no hay información cargada.');
    const rows=[
      ['Nombre',m.full_name],['Nacimiento',m.birth_date],['Grupo sanguíneo',m.blood_type],
      ['Alergias',m.allergies],['Medicaciones importantes',m.medications],['Condiciones',m.conditions],
      ['Cobertura',m.health_provider],['N.º afiliado',m.member_number],
      ['Contacto de emergencia',[m.emergency_contact_name,m.emergency_contact_phone].filter(Boolean).join(' ')],
      ['Notas',m.notes]
    ].filter(x=>String(x[1]||'').trim());
    modal('<h2>Información útil · '+esc(a.person_name||'Contacto CERCA')+'</h2><div class="list">'+(rows.length?rows.map(x=>'<div class="item"><strong>'+esc(x[0])+'</strong><p>'+esc(x[1])+'</p></div>').join(''):'<div class="empty">Todavía no hay información cargada.</div>')+'</div><button class="btn primary block" style="margin-top:14px" onclick="this.closest(\'.modalWrap\').remove()">Cerrar</button>');
  }catch(e){toast(e.message,'error')}
}

async function renderEnterprise(el){
  const e=S.enterprise||{enterprise:false};
  if(!e.enterprise&&!fullAccess()){
    el.innerHTML=premiumWall('CERCA Empresas');
    el.querySelector('[data-subscribe]')?.addEventListener('click',startSubscription);
    return;
  }
  if(!e.enterprise){
    el.innerHTML='<div class="sectionHead"><div><h2>CERCA Empresas</h2><p>Usá un código de invitación para vincular tu cuenta.</p></div></div><div class="infoNote">Si tu empresa te invitó, ingresá el código de 8 caracteres. Tu cuenta personal se mantiene y pasa a usar la identidad de la organización.</div><label>Código de empresa</label><input id="enterpriseCode" class="field" maxlength="8" style="text-transform:uppercase" placeholder="XXXXXXXX"><button id="enterpriseJoinBtn" class="btn primary block" style="margin-top:12px">Unirme a una empresa</button>';
    $('enterpriseJoinBtn').onclick=joinEnterprise;return;
  }
  const o=e.organization||{},isAdmin=e.role==='admin';
  el.innerHTML='<div class="sectionHead"><div><h2>'+esc(o.name||'CERCA Empresas')+'</h2><p>'+(isAdmin?'Administración empresarial':'Cuenta empresarial')+'</p></div><span class="badge">'+esc((e.role||'member').toUpperCase())+'</span></div><div class="profileGrid"><div class="profileBox"><small>Miembros</small><strong>'+Number(e.member_count||0)+'</strong></div><div class="profileBox"><small>Acceso</small><strong>'+(e.enterprise_access_active?'Activo':'Según plan')+'</strong></div></div><div id="enterpriseAdminArea" style="margin-top:14px">'+(isAdmin?'<button id="enterpriseInviteBtn" class="btn primary block">Generar invitación empresarial</button><div id="enterpriseMembers" class="list" style="margin-top:12px"><div class="empty">Cargando miembros…</div></div>':'<div class="infoNote">Tu cuenta está vinculada a '+esc(o.name||'tu empresa')+'.</div>')+'</div>';
  if(isAdmin){$('enterpriseInviteBtn').onclick=createEnterpriseInvite;loadEnterpriseMembers()}
}
async function joinEnterprise(){
  const code=$('enterpriseCode').value.trim().toUpperCase();if(code.length!==8)return toast('Ingresá un código válido.','error');
  try{S.enterprise=await enterpriseCall('join',{code});applyEnterpriseBrand();toast('Cuenta vinculada a '+esc(S.enterprise?.organization?.name||'tu empresa')+'.');renderSide();renderDashboard()}catch(e){toast(e.message,'error')}
}
async function createEnterpriseInvite(){
  try{const d=await enterpriseCall('create_invite',{max_uses:50});const code=d?.invite?.code||'';if(!code)throw new Error('No se generó la invitación.');const org=S.enterprise?.organization?.name||'tu empresa';const text='Te invito a usar CERCA con '+org+'. Instalá CERCA, iniciá tu cuenta y en Empresa ingresá el código: '+code;if(navigator.share){try{await navigator.share({title:'CERCA Empresas',text});return}catch(e){if(e?.name==='AbortError')return}}try{await navigator.clipboard.writeText(text);toast('Invitación copiada.')}catch{modal('<h2>Invitación empresarial</h2><p>'+esc(text)+'</p>')}}catch(e){toast(e.message,'error')}
}
async function loadEnterpriseMembers(){
  const box=$('enterpriseMembers');if(!box)return;
  try{const d=await enterpriseCall('members',null,'GET');const members=Array.isArray(d?.members)?d.members:[];box.innerHTML=members.length?members.map(m=>'<div class="item"><div class="itemTop"><div><h3>'+esc(m.full_name||m.email||'Usuario')+'</h3><p>'+esc(m.email||'')+'</p></div><span class="badge">'+esc((m.role||'member').toUpperCase())+'</span></div></div>').join(''):'<div class="empty">Todavía no hay miembros.</div>'}catch(e){box.innerHTML='<div class="empty">'+esc(e.message)+'</div>'}
}
function renderProfile(el){
  const name=S.profile?.full_name||S.user?.user_metadata?.full_name||'—',phone=S.profile?.phone_e164||S.user?.user_metadata?.phone_e164||'—';
  const org=S.enterprise?.enterprise?S.enterprise?.organization?.name:'Individual';
  const configured=!!S.billing?.configured,mp=S.billing?.subscription;
  const next=mp?.next_payment_date?new Date(mp.next_payment_date).toLocaleDateString('es-AR'):'';
  const billingCard=enterpriseAccess()
    ?'<div class="subscriptionCard active"><div><small>Suscripción</small><h3>Incluida por '+esc(org||'tu empresa')+'</h3><p>No necesitás una suscripción individual.</p></div></div>'
    :'<div class="subscriptionCard '+((mpPremiumActive()||legacyPremiumActive())?'active':'')+'"><div><small>CERCA PREMIUM</small><h3>$25.000 <span>ARS / mes</span></h3><p>'+esc(subscriptionStatusText())+(next?' · Próximo cobro: '+esc(next):'')+'</p></div>'+((mpPremiumActive()||legacyPremiumActive())?'<span class="badge">ACTIVA</span>':'<button id="subscribeBtn" class="btn primary sm" '+(configured?'':'disabled')+'>'+(configured?'Activar suscripción':'Mercado Pago pendiente')+'</button>')+'</div>';
  el.innerHTML='<div class="sectionHead"><div><h2>Mi cuenta</h2><p>Datos de tu cuenta CERCA.</p></div></div>'+billingCard+'<div class="profileGrid"><div class="profileBox"><small>Nombre</small><strong>'+esc(name)+'</strong></div><div class="profileBox"><small>Email</small><strong>'+esc(S.user?.email||'—')+'</strong></div><div class="profileBox"><small>Celular</small><strong>'+esc(phone)+'</strong></div><div class="profileBox"><small>Plan</small><strong>'+esc(planLabel())+'</strong></div><div class="profileBox"><small>Cuenta</small><strong>'+esc(org||'Individual')+'</strong></div><div class="profileBox"><small>Versión</small><strong>Web / PWA</strong></div></div><button id="notifyBtn" class="btn light block" style="margin-top:14px">Activar notificaciones</button><button id="logoutBtn" class="btn dark block" style="margin-top:8px">Cerrar sesión</button>';
  $('logoutBtn').onclick=logout;$('notifyBtn').onclick=enableNotifications;if($('subscribeBtn'))$('subscribeBtn').onclick=startSubscription;
}
async function enableNotifications(){if(!('Notification'in window))return toast('Este navegador no ofrece notificaciones web.','error');const p=await Notification.requestPermission();if(p==='granted'){toast('Notificaciones activadas.');await tryWebPush()}else toast('No se habilitaron las notificaciones.','error')}
async function tryWebPush(){try{const cfg=await networkCall('push_config',null,'GET');if(!cfg?.enabled||!cfg?.vapidKey)return;const reg=await navigator.serviceWorker.ready;const key=uint8(cfg.vapidKey);const sub=await reg.pushManager.subscribe({userVisibleOnly:true,applicationServerKey:key});await networkCall('register_web_push',{subscription:sub.toJSON()});toast('Push web registrado.')}catch(e){console.warn('Web push pendiente',e)}}
function uint8(s){const p='='.repeat((4-s.length%4)%4),b=(s+p).replace(/-/g,'+').replace(/_/g,'/'),r=atob(b);return Uint8Array.from([...r].map(c=>c.charCodeAt(0)))}
async function logout(){try{await request('/auth/v1/logout',{method:'POST'})}catch{}stopTracking();stopPolling();sessionClear();S={...S,session:null,user:null,profile:null,network:null,activeEmergency:null,currentAlert:null};renderAuth('login')}

function renderBelow(){
  const b=$('belowContent');if(!b)return;const alerts=networkCollections().alerts;const links=networkCollections().links;
  b.innerHTML='<div class="sectionHead"><div><h2>Estado de CERCA</h2><p>Resumen de tu protección.</p></div><button id="refreshBtn" class="btn light sm">Actualizar</button></div><div class="profileGrid"><div class="profileBox"><small>Mi Red CERCA</small><strong>'+links.length+' persona(s)</strong></div><div class="profileBox"><small>Alertas activas</small><strong>'+alerts.length+'</strong></div><div class="profileBox"><small>Plan</small><strong>'+esc(planLabel())+'</strong></div><div class="profileBox"><small>Ubicación</small><strong>'+(S.activeEmergency?'Compartiendo':'Privada')+'</strong></div><div class="profileBox"><small>Instalación</small><strong>'+(matchMedia('(display-mode: standalone)').matches?'Instalada':'Disponible')+'</strong></div></div>';
  $('refreshBtn').onclick=refreshAll;
}
async function refreshAll(){await Promise.all([loadNetwork(),loadBilling()]);renderSide();renderBelow();syncCurrentAlertFromState()}
function stopPolling(){if(S.poll){clearInterval(S.poll);S.poll=null}}
function startPolling(){stopPolling();S.poll=setInterval(async()=>{const prev=S.lastIncomingId;await loadNetwork();const alerts=networkCollections().alerts;const firstId=String(alerts[0]?.id||alerts[0]?.emergency_id||'');if(firstId&&firstId!==prev){S.lastIncomingId=firstId;if(Notification.permission==='granted'&&document.visibilityState!=='visible'){try{const reg=await navigator.serviceWorker?.ready;await reg?.showNotification('🚨 Alerta CERCA',{body:(alerts[0]?.person_name||'Una persona de tu Red CERCA')+' necesita ayuda.',icon:'/icon.svg',tag:firstId,data:{emergency_id:firstId}})}catch{}}if(S.tab==='alerts')renderSide();if(document.visibilityState==='visible')openAlert(firstId)}syncCurrentAlertFromState();renderBelow()},6000)}
navigator.serviceWorker?.addEventListener?.('message',e=>{if(e.data?.type==='open-alert'&&e.data.id)openAlert(e.data.id)});

async function boot(){
  setPill('Conectando…');
  const recovery=recoveryFromHash();
  if(recovery)sessionSave(recovery);else sessionLoad();
  if(!S.session){renderAuth('login');return}
  const u=await authMe();if(!u){renderAuth('login');return}
  if(recovery){renderRecovery();setPill('Recuperar acceso');return}
  await Promise.all([loadProfile(),loadEntitlement(),loadBilling(),loadNetwork(),loadEnterprise()]);
  if(S.profile?.phone_e164){try{await networkCall('set_phone',{phone:S.profile.phone_e164})}catch{}}
  applyEnterpriseBrand();
  renderDashboard();
  if(S.activeEmergency)startTracking();
  const alerts=networkCollections().alerts;
  S.lastIncomingId=String(alerts[0]?.id||alerts[0]?.emergency_id||'');
  const params=new URLSearchParams(location.search);
  const alertParam=params.get('alert');if(alertParam)openAlert(alertParam);
  if(params.get('subscription')==='return'){history.replaceState(null,'',location.pathname);loadBilling().then(()=>{toast(mpPremiumActive()?'Suscripción activada.':'Mercado Pago está procesando la adhesión.');renderSide();renderBelow()})}
  setPill('CERCA activa');
}
boot();