(()=>{
  const ua=navigator.userAgent||'';
  const ios=/iPhone|iPad|iPod/i.test(ua)||(navigator.platform==='MacIntel'&&navigator.maxTouchPoints>1);
  const nativeAndroid=/CERCA-Native-Android/i.test(ua)||window.__CERCA_NATIVE_ANDROID__===true||!!window.CercaNative;
  if(!ios||nativeAndroid)return;

  const DEFAULT_SHORTCUT_NAME='CERCA SOS';
  const CONFIG_URL='/ios-shortcut-config.json';
  const READY_KEY='cerca_ios_shortcut_ready_v5';
  const SETUP_STARTED_KEY='cerca_ios_shortcut_setup_started_v5';
  const CALLED_PREFIX='cerca_ios_shortcut_called_v5:';
  let onboardingShown=false;
  let refreshQueued=false;
  let shortcutConfig=null;

  function cleanPhone(raw){
    const s=String(raw||'').trim();
    if(!s)return '';
    const digits=s.replace(/\D/g,'');
    if(!digits)return '';
    return s.startsWith('+')?('+'+digits):digits;
  }

  async function loadShortcutConfig(){
    if(shortcutConfig)return shortcutConfig;
    try{
      const r=await fetch(CONFIG_URL+'?v=5',{cache:'no-store'});
      const d=await r.json();
      shortcutConfig={name:String(d?.name||DEFAULT_SHORTCUT_NAME),installUrl:String(d?.install_url||'').trim()};
    }catch{shortcutConfig={name:DEFAULT_SHORTCUT_NAME,installUrl:''}}
    return shortcutConfig;
  }

  function isReady(){try{return localStorage.getItem(READY_KEY)==='1'}catch{return false}}
  function setReady(v){try{v?localStorage.setItem(READY_KEY,'1'):localStorage.removeItem(READY_KEY)}catch{}}
  function setupStarted(){try{return sessionStorage.getItem(SETUP_STARTED_KEY)==='1'}catch{return false}}
  function setSetupStarted(v){try{v?sessionStorage.setItem(SETUP_STARTED_KEY,'1'):sessionStorage.removeItem(SETUP_STARTED_KEY)}catch{}}
  function emergencyId(){try{return String(S?.activeEmergency?.id||S?.activeEmergency?.emergency_id||'')}catch{return''}}
  function wasCalled(id){try{return localStorage.getItem(CALLED_PREFIX+id)==='1'}catch{return false}}
  function markCalled(id){try{localStorage.setItem(CALLED_PREFIX+id,'1')}catch{}}

  async function getCallPhone(){
    try{
      if(typeof designatedCall==='function'){
        const c=designatedCall();
        const p=cleanPhone(c?.phone_e164||c?.phone||c?.target_phone||c?.contact_phone||'');
        if(p)return p;
      }
    }catch{}
    try{
      if(typeof S==='undefined'||!S.user?.id||typeof request!=='function')return '';
      const uid=encodeURIComponent(S.user.id);
      const rows=await request('/rest/v1/cerca_contacts_v2?owner_user_id=eq.'+uid+'&call_enabled=eq.true&select=phone_e164&order=updated_at.desc&limit=1');
      const row=Array.isArray(rows)?rows[0]:null;
      return cleanPhone(row?.phone_e164||'');
    }catch{return ''}
  }

  async function shortcutUrl(phone){
    const cfg=await loadShortcutConfig();
    return 'shortcuts://run-shortcut?name='+encodeURIComponent(cfg.name||DEFAULT_SHORTCUT_NAME)+'&input=text&text='+encodeURIComponent(phone);
  }

  async function launchShortcut(phone){
    const p=cleanPhone(phone);if(!p)return false;
    location.href=await shortcutUrl(p);
    return true;
  }

  function afterInstallModal(){
    if(document.querySelector('.modalWrap'))return;
    const html='<h2>Terminar activación</h2>'+
      '<p>Apple pide confirmar una sola vez que <b>CERCA SOS</b> quede agregado a Atajos.</p>'+
      '<div class="statusBox" style="text-align:left;margin:14px 0">Si ya lo agregaste en Atajos, tocá <b>Listo</b>. No vas a tener que configurarlo de nuevo.</div>'+
      '<button id="iosShortcutReady" class="btn primary block">Listo, ya está agregado</button>'+
      '<button id="iosShortcutRetry" class="btn light block" style="margin-top:8px">Volver a abrir el Atajo</button>';
    const w=typeof modal==='function'?modal(html):null;
    if(!w)return;
    const ready=w.querySelector('#iosShortcutReady');
    const retry=w.querySelector('#iosShortcutRetry');
    if(ready)ready.onclick=()=>{
      setReady(true);setSetupStarted(false);w.remove();onboardingShown=true;refreshCard();
      try{if(typeof toast==='function')toast('Llamada de iPhone activada.')}catch{}
    };
    if(retry)retry.onclick=()=>{w.remove();openInstaller();};
  }

  async function shareSignedShortcut(url){
    const r=await fetch(url,{cache:'no-store'});
    if(!r.ok)throw new Error('No pude preparar CERCA SOS.');
    const blob=await r.blob();
    const file=new File([blob],'CERCA-SOS.shortcut',{type:'application/x-apple-shortcut'});
    if(navigator.share&&(!navigator.canShare||navigator.canShare({files:[file]}))){
      await navigator.share({files:[file],title:'CERCA SOS'});
      return true;
    }
    return false;
  }

  async function openInstaller(){
    const cfg=await loadShortcutConfig();
    setSetupStarted(true);
    if(!cfg.installUrl){
      try{if(typeof toast==='function')toast('No pude preparar el Atajo de CERCA.','error')}catch{}
      return;
    }
    try{
      const shared=await shareSignedShortcut(cfg.installUrl);
      if(shared){
        setTimeout(afterInstallModal,450);
        return;
      }
    }catch(e){
      if(e?.name==='AbortError')return;
    }
    // Fallback compatible: Safari descarga el archivo firmado por Apple.
    // Al abrirlo, iOS muestra la pantalla de Agregar Atajo.
    location.href=cfg.installUrl;
  }

  async function setupModal(){
    if(document.querySelector('.modalWrap'))return;
    const html='<h2>Activar llamadas de CERCA en iPhone</h2>'+
      '<p>Esto se hace una sola vez. CERCA ya preparó el Atajo; no tenés que crear acciones ni escribir nada.</p>'+
      '<div class="statusBox" style="text-align:left;margin:14px 0">Tocá <b>Activar ahora</b>. Cuando Apple muestre las opciones, elegí <b>Atajos</b> y confirmá <b>Agregar</b>.</div>'+
      '<button id="iosShortcutCreate" class="btn primary block">Activar ahora</button>'+
      '<button id="iosShortcutLater" class="btn light block" style="margin-top:8px">Ahora no</button>';
    const w=typeof modal==='function'?modal(html):null;
    if(!w)return;
    const create=w.querySelector('#iosShortcutCreate');
    const later=w.querySelector('#iosShortcutLater');
    if(create)create.onclick=()=>{w.remove();openInstaller();};
    if(later)later.onclick=()=>w.remove();
  }

  async function testShortcut(){
    const phone=await getCallPhone();
    if(!phone){if(typeof toast==='function')toast('Elegí primero un contacto para llamada en Tu Red CERCA.','error');return}
    if(!confirm('La prueba puede iniciar una llamada real a '+phone+'. ¿Continuar?'))return;
    launchShortcut(phone);
  }

  function refreshCard(){
    if(refreshQueued)return;
    refreshQueued=true;
    requestAnimationFrame(()=>{
      refreshQueued=false;
      const sos=document.getElementById('sosBtn');
      const old=document.getElementById('iosShortcutCard');
      if(!sos){if(old)old.remove();return}
      const zone=sos.closest('.sosZone')||sos.parentElement;
      if(!zone)return;
      if(old)old.remove();
      const card=document.createElement('div');
      card.id='iosShortcutCard';card.className='statusBox';card.style.marginTop='14px';
      const ready=isReady();
      card.innerHTML='<div class="statusLine"><span class="dot '+(ready?'live':'')+'"></span><span>'+(ready?'Llamada iPhone preparada':'Activar llamada en iPhone')+'</span></div>'+
        '<div class="mini">'+(ready?'SOS normal ejecutará CERCA SOS con tu contacto de llamada.':'Configuración única. CERCA ya prepara todo automáticamente.')+'</div>'+
        '<div class="actions" style="margin-top:10px"><button id="iosShortcutSetupBtn" class="btn light">'+(ready?'Revisar':'Activar')+'</button>'+
        (ready?'<button id="iosShortcutTestBtn" class="btn light">Probar llamada</button>':'')+'</div>';
      zone.appendChild(card);
      const setup=card.querySelector('#iosShortcutSetupBtn');
      const test=card.querySelector('#iosShortcutTestBtn');
      if(setup)setup.onclick=()=>{if(ready)setReady(false);setupModal();};
      if(test)test.onclick=testShortcut;
    });
  }

  async function triggerForEmergency(){
    if(!isReady()){
      try{if(typeof toast==='function')toast('Falta activar una vez la llamada de iPhone.','error')}catch{}
      return;
    }
    const id=emergencyId();
    if(!id||wasCalled(id))return;
    const phone=await getCallPhone();
    if(!phone)return;
    markCalled(id);
    launchShortcut(phone);
  }

  function installWrapper(){
    if(typeof startEmergency!=='function'||startEmergency.__cercaIosShortcut)return false;
    const previous=startEmergency;
    const wrapped=async function(silent){
      const before=emergencyId();
      const result=await previous(silent);
      try{
        if(!silent&&typeof S!=='undefined'&&S.activeEmergency){
          const after=emergencyId();
          if(after&&after!==before)await triggerForEmergency();
        }
      }catch{}
      return result;
    };
    wrapped.__cercaIosShortcut=true;
    startEmergency=wrapped;
    return true;
  }

  function maybeOnboard(){
    try{
      if(window.__CERCA_LOCATION_ONBOARDING_PENDING__)return;
      if(isReady()||onboardingShown||typeof S==='undefined'||!S.user||!document.getElementById('sosBtn'))return;
      if(document.querySelector('.modalWrap'))return;
      onboardingShown=true;
      setTimeout(()=>{if(setupStarted())afterInstallModal();else setupModal();},350);
    }catch{}
  }

  let tries=0;
  const wait=setInterval(()=>{
    tries++;installWrapper();refreshCard();maybeOnboard();
    if((typeof S!=='undefined'&&S.user&&typeof startEmergency==='function')||tries>180)clearInterval(wait);
  },300);

  const appRoot=document.getElementById('app')||document.body;
  const obs=new MutationObserver(()=>{refreshCard();maybeOnboard();});
  obs.observe(appRoot,{childList:true,subtree:true});

  addEventListener('cerca-location-ready',()=>setTimeout(maybeOnboard,250));
  addEventListener('pageshow',()=>{refreshCard();setTimeout(()=>{if(setupStarted()&&!isReady())afterInstallModal();else maybeOnboard()},350)});
  addEventListener('focus',()=>{refreshCard();setTimeout(()=>{if(setupStarted()&&!isReady())afterInstallModal();else maybeOnboard()},350)});
})();