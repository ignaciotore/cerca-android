const CFG={
  base:location.origin+'/api',
  key:'sb_publishable_XtjPnBnjESZwcUnUUAPybg_Y6LqivaD',
  network:'/functions/v1/cerca-network-v2'
};
const SESSION_KEY='cerca_web_session_v1';
const $=id=>document.getElementById(id);
const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'}[c]));
const app=$('app');
let S={session:null,user:null,profile:null,network:null,activeEmergency:null,watchId:null,map:null,marker:null,trail:null,trailPoints:[],poll:null,currentAlert:null,lastIncomingId:'',tab:'home'};

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
async function resetPassword(){const e=$('email').value.trim();if(!e.includes('@'))return authErr(new Error('Escribí tu email primero.'));try{await request('/auth/v1/recover?redirect_to='+encodeURIComponent(location.origin),{method:'POST',auth:false,body:{email:e}});toast('Te enviamos el enlace para recuperar la contraseña.')}catch(err){authErr(err)}}

async function loadProfile(){
  const uid=S.user?.id;if(!uid)return null;
  try{const d=await request('/rest/v1/profiles?user_id=eq.'+encodeURIComponent(uid)+'&select=full_name,email,trial_started_at,trial_ends_at,phone_e164');S.profile=Array.isArray(d)?d[0]||null:d;return S.profile}catch{return null}
}
async function networkCall(action,body=null,method='POST'){const q='?action='+encodeURIComponent(action);return request(CFG.network+q,{method,body:body||undefined})}
async function loadNetwork(){try{S.network=await networkCall('state',null,'GET');S.activeEmergency=S.network?.active_emergency||null;return S.network}catch(e){console.warn(e);return null}}

function renderDashboard(){
  const name=S.profile?.full_name||S.user?.user_metadata?.full_name||S.user?.email?.split('@')[0]||'';
  app.innerHTML='<div class="dash"><section class="card panel"><div class="hello"><div><h1>Hola, '+esc(name)+'</h1><p>Tu Red CERCA está lista para acompañarte.</p></div><button id="profileBtn" class="btn light sm">Mi cuenta</button></div><nav class="nav"><button data-tab="home" class="active">Inicio</button><button data-tab="network">Mi Red</button><button data-tab="info">Información útil</button><button data-tab="alerts">Alertas</button></nav><div id="sideContent"></div></section><section class="card mapCard"><div class="mapHeader"><div><strong id="mapTitle">Tu ubicación</strong><div id="mapMeta" class="mapMeta">Todavía no compartís ubicación.</div></div><span id="liveBadge" class="badge">En espera</span></div><div id="map"></div><div class="mapFooter"><span id="mapFooterText" class="mapMeta">El mapa se activa al pedir ayuda o abrir una alerta.</span><div class="actions"><button id="centerMapBtn" class="btn light sm">Centrar</button><a id="mapsLink" class="btn light sm hidden" target="_blank" rel="noopener">Abrir mapa</a></div></div></section><section class="card panel" style="grid-column:1/-1"><div id="belowContent"></div></section></div>';
  document.querySelectorAll('.nav button').forEach(b=>b.onclick=()=>{S.tab=b.dataset.tab;document.querySelectorAll('.nav button').forEach(x=>x.classList.toggle('active',x===b));renderSide()});
  $('profileBtn').onclick=()=>{S.tab='profile';document.querySelectorAll('.nav button').forEach(x=>x.classList.remove('active'));renderSide()};
  $('centerMapBtn').onclick=centerMap;
  initMap();renderSide();renderBelow();startPolling();
}
function initMap(){if(S.map)return;S.map=L.map('map',{zoomControl:true}).setView([-34.60,-58.44],11);L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap'}).addTo(S.map);setTimeout(()=>S.map.invalidateSize(),100)}
function setMapPoint(lat,lon,opt={}){if(!Number.isFinite(+lat)||!Number.isFinite(+lon)||!S.map)return;const p=[+lat,+lon];if(!S.marker)S.marker=L.marker(p).addTo(S.map);else S.marker.setLatLng(p);if(opt.trail){S.trailPoints.push(p);if(S.trailPoints.length>120)S.trailPoints.shift();if(S.trail)S.trail.setLatLngs(S.trailPoints);else S.trail=L.polyline(S.trailPoints,{weight:5,opacity:.6}).addTo(S.map)}if(opt.center!==false)S.map.setView(p,opt.zoom||16);$('mapsLink').href='https://maps.google.com/?q='+p[0]+','+p[1];$('mapsLink').classList.remove('hidden')}
function centerMap(){if(S.marker)S.map.setView(S.marker.getLatLng(),16)}
function clearMapTrail(){S.trailPoints=[];if(S.trail){S.map.removeLayer(S.trail);S.trail=null}}

function renderSide(){
  const el=$('sideContent');if(!el)return;
  if(S.tab==='home')return renderHome(el);
  if(S.tab==='network')return renderNetwork(el);
  if(S.tab==='info')return renderInfo(el);
  if(S.tab==='alerts')return renderAlerts(el);
  if(S.tab==='profile')return renderProfile(el);
}
function renderHome(el){
  const active=!!S.activeEmergency;
  el.innerHTML='<div class="sosZone"><button id="sosBtn" class="sosButton '+(active?'active':'')+'">'+(active?'SOS ACTIVO':'PEDIR<br>AYUDA')+'</button><div class="sosHint">'+(active?'Tu ubicación se está actualizando mientras CERCA permanezca activa.':'Mantené apretado 2 segundos. Tendrás una cuenta regresiva para cancelar.')+'</div><div class="actions" style="justify-content:center"><button id="silentBtn" class="btn light">'+(active?'Seguimiento activo':'SOS silencioso')+'</button>'+(active?'<button id="resolveBtn" class="btn successBtn">ESTOY BIEN · FINALIZAR</button>':'')+'</div></div><div class="statusBox"><div class="statusLine"><span class="dot '+(active?'live':'')+'"></span><span>'+(active?'Emergencia activa':'Protección lista')+'</span></div><div class="mini">'+(active?'CERCA seguirá enviando nuevas posiciones al backend mientras el navegador permita el seguimiento.':'Al activar SOS avisaremos a tu Red CERCA con tu ubicación disponible.')+'</div></div>';
  const b=$('sosBtn');if(active)b.onclick=()=>toast('La emergencia ya está activa.');else{let timer=null;b.onpointerdown=()=>{timer=setTimeout(()=>confirmSOS(false),2000)};b.onpointerup=b.onpointercancel=()=>{clearTimeout(timer)}}
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
  try{const c=await getPos();const d=await networkCall('emergency_start',{mode:silent?'silent':'normal',latitude:c.latitude,longitude:c.longitude});S.activeEmergency=d.emergency||d.active_emergency||d;clearMapTrail();setMapPoint(c.latitude,c.longitude,{trail:true});$('mapTitle').textContent='Tu ubicación en vivo';$('mapMeta').textContent='SOS activado · precisión aprox. '+Math.round(c.accuracy||0)+' m';$('liveBadge').textContent='EN VIVO';$('liveBadge').className='badge';$('mapFooterText').textContent='CERCA actualiza tu posición a medida que el teléfono informa cambios.';startTracking();toast('SOS activado. Tu Red CERCA fue alertada.');await loadNetwork();renderSide();renderBelow()}catch(e){toast(e.message,'error')}finally{setPill('CERCA activa')}
}
function startTracking(){stopTracking();if(!navigator.geolocation)return;let last=0;S.watchId=navigator.geolocation.watchPosition(async p=>{const now=Date.now();const c=p.coords;setMapPoint(c.latitude,c.longitude,{trail:true,center:false});$('mapMeta').textContent='Actualizada '+new Date().toLocaleTimeString('es-AR')+' · precisión '+Math.round(c.accuracy||0)+' m';if(now-last<4000)return;last=now;const id=S.activeEmergency?.id||S.network?.active_emergency?.id;if(!id)return;try{await networkCall('emergency_update',{emergency_id:id,latitude:c.latitude,longitude:c.longitude})}catch{}},e=>{$('mapMeta').textContent='Ubicación pausada: '+(e.message||'sin señal')},{enableHighAccuracy:true,maximumAge:2500,timeout:12000})}
function stopTracking(){if(S.watchId!==null&&navigator.geolocation){navigator.geolocation.clearWatch(S.watchId);S.watchId=null}}
async function resolveEmergency(){const id=S.activeEmergency?.id||S.network?.active_emergency?.id;if(!id)return;try{await networkCall('emergency_resolve',{emergency_id:id});stopTracking();S.activeEmergency=null;await loadNetwork();toast('Emergencia finalizada.');$('liveBadge').textContent='Finalizada';$('mapMeta').textContent='El seguimiento se detuvo.';renderSide();renderBelow()}catch(e){toast(e.message,'error')}}

function networkCollections(){const n=S.network||{};return{links:arr(n,'links','network_links','members','contacts'),invites:arr(n,'invitations','pending_invites','invites'),alerts:arr(n,'incoming_alerts','alerts')}}
function renderNetwork(el){
  const c=networkCollections();
  el.innerHTML='<div class="sectionHead"><div><h2>Mi Red CERCA</h2><p>Personas que pueden recibir tus alertas.</p></div><button id="newInvite" class="btn primary sm">+ Invitar</button></div><div class="list">'+(c.links.length?c.links.map(linkCard).join(''):'<div class="empty">Todavía no hay personas vinculadas a tu Red CERCA.</div>')+'</div><div style="margin-top:16px"><button id="acceptInvite" class="btn light block">Tengo un código de invitación</button></div>';
  $('newInvite').onclick=newInviteModal;$('acceptInvite').onclick=acceptInviteModal;
  el.querySelectorAll('[data-remove]').forEach(b=>b.onclick=()=>removeLink(b.dataset.remove));
}
function linkCard(x){const id=x.id||x.link_id||'';const n=x.display_name||x.full_name||x.name||x.email||'Contacto CERCA';const rel=x.relationship||x.relation||'Red CERCA';const med=x.medical_access||'never';return'<div class="item"><div class="itemTop"><div><h3>'+esc(n)+'</h3><p>'+esc(rel)+'</p></div><span class="badge">'+(med==='never'?'Sin info útil':'Info autorizada')+'</span></div>'+(id?'<div class="itemActions"><button class="btn light sm" data-remove="'+esc(id)+'">Quitar de la red</button></div>':'')+'</div>'}
function newInviteModal(){const w=modal('<h2>Invitar a Mi Red CERCA</h2><p>Generá una invitación para una persona de confianza.</p><label>Nombre</label><input id="invName" class="field"><label>Relación</label><input id="invRel" class="field" placeholder="Ej: hermana, amigo, pareja"><label>Información útil ante una emergencia</label><select id="invMed" class="select"><option value="never">No compartir</option><option value="emergency">Solo durante una emergencia</option><option value="always">Siempre autorizada</option></select><button id="makeInvite" class="btn primary block" style="margin-top:16px">Generar invitación</button>');$('makeInvite').onclick=async()=>{try{const d=await networkCall('create_invite',{display_name:$('invName').value.trim(),relationship:$('invRel').value.trim(),medical_access:$('invMed').value});const code=d.code||d.invite_code||d.invitation?.code||'';w.innerHTML='<div class="modal"><h2>Invitación creada</h2><p>Compartí este código con la persona:</p><div class="countdown" style="font-size:44px;color:var(--teal)">'+esc(code||'Creada')+'</div><button id="closeInvite" class="btn primary block">Listo</button></div>';$('closeInvite').onclick=()=>{w.remove();refreshAll()}}catch(e){toast(e.message,'error')}}}
function acceptInviteModal(){const w=modal('<h2>Unirme a una Red CERCA</h2><p>Ingresá el código que te compartieron.</p><input id="inviteCode" class="field" style="text-transform:uppercase" placeholder="CÓDIGO"><button id="acceptCode" class="btn primary block" style="margin-top:14px">Aceptar invitación</button>');$('acceptCode').onclick=async()=>{try{await networkCall('accept_code',{code:$('inviteCode').value.trim().toUpperCase()});w.remove();toast('Ya sos parte de esa Red CERCA.');refreshAll()}catch(e){toast(e.message,'error')}}}
async function removeLink(id){if(!confirm('¿Quitar a esta persona de tu Red CERCA?'))return;try{await networkCall('remove_link',{link_id:id});toast('Contacto eliminado de la red.');refreshAll()}catch(e){toast(e.message,'error')}}

async function fetchMedical(){try{const uid=S.user.id;const d=await request('/rest/v1/medical_profiles?user_id=eq.'+encodeURIComponent(uid)+'&select=*');return Array.isArray(d)?d[0]||null:d}catch{return null}}
async function renderInfo(el){
  el.innerHTML='<div class="sectionHead"><div><h2>Información útil ante una emergencia</h2><p>Campo opcional que podés compartir con personas autorizadas.</p></div></div><div class="infoNote">Podés escribir alergias, medicación, cobertura, contacto alternativo o cualquier dato que consideres útil. CERCA no brinda diagnóstico ni reemplaza la atención médica.</div><label>Información</label><textarea id="infoText" class="textarea" placeholder="Ej: Alergia a penicilina. Cobertura..."></textarea><label><input id="shareInfo" type="checkbox"> Permitir compartirla cuando corresponda según Mi Red CERCA</label><button id="saveInfo" class="btn primary block" style="margin-top:14px">Guardar</button>';
  const m=await fetchMedical();if($('infoText')){$('infoText').value=m?.notes||'';$('shareInfo').checked=!!m?.share_enabled;$('saveInfo').onclick=()=>saveInfo(m)}
}
async function saveInfo(prev){const uid=S.user.id;const body={user_id:uid,full_name:prev?.full_name||S.profile?.full_name||S.user?.user_metadata?.full_name||'',birth_date:prev?.birth_date||null,blood_type:prev?.blood_type||'',allergies:prev?.allergies||'',medications:prev?.medications||'',conditions:prev?.conditions||'',health_provider:prev?.health_provider||'',member_number:prev?.member_number||'',emergency_contact_name:prev?.emergency_contact_name||'',emergency_contact_phone:prev?.emergency_contact_phone||'',notes:$('infoText').value.trim(),share_enabled:$('shareInfo').checked,updated_at:new Date().toISOString()};try{await request('/rest/v1/medical_profiles?on_conflict=user_id',{method:'POST',body,headers:{Prefer:'resolution=merge-duplicates,return=representation'}});toast('Información guardada.')}catch(e){toast(e.message,'error')}}

function renderAlerts(el){
  const alerts=networkCollections().alerts;
  el.innerHTML='<div class="sectionHead"><div><h2>Alertas de Mi Red</h2><p>Emergencias activas de las personas vinculadas.</p></div></div><div class="list">'+(alerts.length?alerts.map(alertCard).join(''):'<div class="empty">No hay alertas activas en este momento.</div>')+'</div>';
  el.querySelectorAll('[data-alert]').forEach(b=>b.onclick=()=>openAlert(b.dataset.alert));
}
function alertCard(a){const id=a.id||a.emergency_id||'';const name=a.person_name||a.full_name||'Contacto CERCA';const lat=first(a,'latitude','lat'),lon=first(a,'longitude','lon','lng');return'<div class="item"><div class="itemTop"><div><h3>🚨 '+esc(name)+' necesita ayuda</h3><p>'+(lat!=null&&lon!=null?'Ubicación disponible y actualizable.':'Esperando ubicación…')+'</p></div><span class="badge red">SOS ACTIVO</span></div><div class="itemActions"><button class="btn danger sm" data-alert="'+esc(id)+'">Ver emergencia</button></div></div>'}
async function openAlert(id){await loadNetwork();const a=networkCollections().alerts.find(x=>String(x.id||x.emergency_id)===String(id))||networkCollections().alerts[0];if(!a)return toast('La alerta ya no está activa.');S.currentAlert=a;try{await networkCall('emergency_seen',{emergency_id:a.id||a.emergency_id})}catch{}const lat=+first(a,'latitude','lat'),lon=+first(a,'longitude','lon','lng');const name=a.person_name||a.full_name||'Contacto CERCA';$('mapTitle').textContent='🚨 '+name+' necesita ayuda';$('mapMeta').textContent='Ubicación de la alerta · actualizada '+new Date().toLocaleTimeString('es-AR');$('liveBadge').textContent='SOS ACTIVO';$('liveBadge').className='badge red';$('mapFooterText').textContent='La posición se actualiza mientras la emergencia siga activa y el teléfono emisor pueda reportarla.';clearMapTrail();if(Number.isFinite(lat)&&Number.isFinite(lon))setMapPoint(lat,lon,{center:true});toast('Marcaste la alerta como vista.')}
function syncCurrentAlertFromState(){if(!S.currentAlert)return;const id=S.currentAlert.id||S.currentAlert.emergency_id;const a=networkCollections().alerts.find(x=>String(x.id||x.emergency_id)===String(id));if(!a)return;S.currentAlert=a;const lat=+first(a,'latitude','lat'),lon=+first(a,'longitude','lon','lng');if(Number.isFinite(lat)&&Number.isFinite(lon)){setMapPoint(lat,lon,{center:false});$('mapMeta').textContent='Actualizada '+new Date().toLocaleTimeString('es-AR')}}

function renderProfile(el){
  const name=S.profile?.full_name||S.user?.user_metadata?.full_name||'—',phone=S.profile?.phone_e164||S.user?.user_metadata?.phone_e164||'—';
  el.innerHTML='<div class="sectionHead"><div><h2>Mi cuenta</h2><p>Datos de tu cuenta CERCA.</p></div></div><div class="profileGrid"><div class="profileBox"><small>Nombre</small><strong>'+esc(name)+'</strong></div><div class="profileBox"><small>Email</small><strong>'+esc(S.user?.email||'—')+'</strong></div><div class="profileBox"><small>Celular</small><strong>'+esc(phone)+'</strong></div><div class="profileBox"><small>Versión</small><strong>Web / PWA</strong></div></div><button id="notifyBtn" class="btn light block" style="margin-top:14px">Activar notificaciones</button><button id="logoutBtn" class="btn dark block" style="margin-top:8px">Cerrar sesión</button>';
  $('logoutBtn').onclick=logout;$('notifyBtn').onclick=enableNotifications;
}
async function enableNotifications(){if(!('Notification'in window))return toast('Este navegador no ofrece notificaciones web.','error');const p=await Notification.requestPermission();if(p==='granted'){toast('Notificaciones activadas.');await tryWebPush()}else toast('No se habilitaron las notificaciones.','error')}
async function tryWebPush(){try{const cfg=await networkCall('push_config',null,'GET');if(!cfg?.enabled||!cfg?.vapidKey)return;const reg=await navigator.serviceWorker.ready;const key=uint8(cfg.vapidKey);const sub=await reg.pushManager.subscribe({userVisibleOnly:true,applicationServerKey:key});await networkCall('register_web_push',{subscription:sub.toJSON()});toast('Push web registrado.')}catch(e){console.warn('Web push pendiente',e)}}
function uint8(s){const p='='.repeat((4-s.length%4)%4),b=(s+p).replace(/-/g,'+').replace(/_/g,'/'),r=atob(b);return Uint8Array.from([...r].map(c=>c.charCodeAt(0)))}
async function logout(){try{await request('/auth/v1/logout',{method:'POST'})}catch{}stopTracking();stopPolling();sessionClear();S={...S,session:null,user:null,profile:null,network:null,activeEmergency:null,currentAlert:null};renderAuth('login')}

function renderBelow(){
  const b=$('belowContent');if(!b)return;const alerts=networkCollections().alerts;const links=networkCollections().links;
  b.innerHTML='<div class="sectionHead"><div><h2>Estado de CERCA</h2><p>Resumen de tu protección.</p></div><button id="refreshBtn" class="btn light sm">Actualizar</button></div><div class="profileGrid"><div class="profileBox"><small>Mi Red CERCA</small><strong>'+links.length+' persona(s)</strong></div><div class="profileBox"><small>Alertas activas</small><strong>'+alerts.length+'</strong></div><div class="profileBox"><small>Ubicación</small><strong>'+(S.activeEmergency?'Compartiendo':'Privada')+'</strong></div><div class="profileBox"><small>Instalación</small><strong>'+(matchMedia('(display-mode: standalone)').matches?'Instalada':'Disponible')+'</strong></div></div>';
  $('refreshBtn').onclick=refreshAll;
}
async function refreshAll(){await loadNetwork();renderSide();renderBelow();syncCurrentAlertFromState()}
function stopPolling(){if(S.poll){clearInterval(S.poll);S.poll=null}}
function startPolling(){stopPolling();S.poll=setInterval(async()=>{const prev=S.lastIncomingId;await loadNetwork();const alerts=networkCollections().alerts;const firstId=String(alerts[0]?.id||alerts[0]?.emergency_id||'');if(firstId&&firstId!==prev){S.lastIncomingId=firstId;if(Notification.permission==='granted'&&document.visibilityState!=='visible'){new Notification('🚨 Alerta CERCA',{body:(alerts[0]?.person_name||'Una persona de tu Red CERCA')+' necesita ayuda.',icon:'/icon.svg',tag:firstId})}if(S.tab==='alerts')renderSide()}syncCurrentAlertFromState();renderBelow()},6000)}
navigator.serviceWorker?.addEventListener?.('message',e=>{if(e.data?.type==='open-alert'&&e.data.id)openAlert(e.data.id)});

async function boot(){
  setPill('Conectando…');sessionLoad();
  if(!S.session){renderAuth('login');return}
  const u=await authMe();if(!u){renderAuth('login');return}
  await Promise.all([loadProfile(),loadNetwork()]);
  renderDashboard();
  if(S.activeEmergency){startTracking();const lat=+first(S.activeEmergency,'latitude','lat'),lon=+first(S.activeEmergency,'longitude','lon','lng');if(Number.isFinite(lat)&&Number.isFinite(lon))setMapPoint(lat,lon,{trail:true});$('mapTitle').textContent='Tu ubicación en vivo';$('liveBadge').textContent='EN VIVO'}
  const alertParam=new URLSearchParams(location.search).get('alert');if(alertParam)openAlert(alertParam);
  setPill('CERCA activa');
}
boot();