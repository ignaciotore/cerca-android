(()=>{
  const ua=navigator.userAgent||'';
  const ios=/iPhone|iPad|iPod/i.test(ua)||(navigator.platform==='MacIntel'&&navigator.maxTouchPoints>1);
  const nativeAndroid=/CERCA-Native-Android/i.test(ua)||window.__CERCA_NATIVE_ANDROID__===true||!!window.CercaNative;
  if(!ios||nativeAndroid)return;

  const SHORTCUT_NAME='CERCA SOS';
  const READY_KEY='cerca_ios_shortcut_ready_v2';
  const SETUP_STARTED_KEY='cerca_ios_shortcut_setup_started_v2';
  const CALLED_PREFIX='cerca_ios_shortcut_called_v2:';
  let onboardingShown=false;
  let refreshQueued=false;

  function cleanPhone(raw){
    const s=String(raw||'').trim();
    if(!s)return '';
    const digits=s.replace(/\D/g,'');
    if(!digits)return '';
    return s.startsWith('+')?('+'+digits):digits;
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

  function shortcutUrl(phone){
    return 'shortcuts://run-shortcut?name='+encodeURIComponent(SHORTCUT_NAME)+'&input=text&text='+encodeURIComponent(phone);
  }

  function launchShortcut(phone){
    const p=cleanPhone(phone);if(!p)return false;
    location.href=shortcutUrl(p);
    return true;
  }

  function openCreateShortcut(){
    setSetupStarted(true);
    location.href='shortcuts://create-shortcut';
  }

  function setupModal(){
    if(document.querySelector('.modalWrap'))return;
    const html='<h2>Activar llamadas de CERCA en iPhone</h2>'+
      '<p>Esto se hace una sola vez. Después, cuando actives un SOS normal, CERCA intentará iniciar la llamada desde este iPhone al contacto que elegiste.</p>'+
      '<div class="statusBox" style="text-align:left;margin:14px 0">'+
      '<div><b>1.</b> Tocá <b>Abrir Atajos</b>.</div>'+
      '<div style="margin-top:8px"><b>2.</b> Creá un atajo con el nombre exacto <b>CERCA SOS</b>.</div>'+
      '<div style="margin-top:8px"><b>3.</b> Agregá la acción <b>Obtener números de teléfono de Entrada del atajo</b>.</div>'+
      '<div style="margin-top:8px"><b>4.</b> Agregá la acción <b>Llamar</b> usando ese número.</div>'+
      '<div style="margin-top:8px"><b>5.</b> Si aparece la opción, activá <b>Permitir ejecutar bloqueado</b>.</div>'+
      '</div>'+
      '<button id="iosShortcutCreate" class="btn primary block">Abrir Atajos</button>'+
      '<button id="iosShortcutLater" class="btn light block" style="margin-top:8px">Configurar después</button>';
    const w=typeof modal==='function'?modal(html):null;
    if(!w)return;
    const create=w.querySelector('#iosShortcutCreate');
    const later=w.querySelector('#iosShortcutLater');
    if(create)create.onclick=()=>{w.remove();openCreateShortcut();};
    if(later)later.onclick=()=>w.remove();
  }

  async function verifyModal(){
    if(document.querySelector('.modalWrap'))return;
    const phone=await getCallPhone();
    const html='<h2>Terminemos la configuración</h2>'+
      '<p>Si ya creaste el atajo <b>CERCA SOS</b>, probalo ahora. Esta prueba puede iniciar una llamada real desde tu iPhone.</p>'+
      (phone?'<div class="statusBox" style="margin:14px 0"><div class="mini">Contacto de prueba: <b>'+String(phone).replace(/</g,'&lt;')+'</b></div></div>':'<div class="msg error" style="display:block;margin:14px 0">Antes de probar, elegí un contacto para llamada en Tu Red CERCA.</div>')+
      '<button id="iosShortcutVerify" class="btn primary block" '+(phone?'':'disabled')+'>Probar y activar</button>'+
      '<button id="iosShortcutAgain" class="btn light block" style="margin-top:8px">Volver a instrucciones</button>'+
      '<button id="iosShortcutLater" class="btn light block" style="margin-top:8px">Ahora no</button>';
    const w=typeof modal==='function'?modal(html):null;
    if(!w)return;
    const verify=w.querySelector('#iosShortcutVerify');
    const again=w.querySelector('#iosShortcutAgain');
    const later=w.querySelector('#iosShortcutLater');
    if(verify)verify.onclick=()=>{
      if(!phone)return;
      setReady(true);
      setSetupStarted(false);
      w.remove();
      refreshCard();
      launchShortcut(phone);
    };
    if(again)again.onclick=()=>{w.remove();setupModal();};
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
      card.id='iosShortcutCard';
      card.className='statusBox';
      card.style.marginTop='14px';
      const ready=isReady();
      card.innerHTML='<div class="statusLine"><span class="dot '+(ready?'live':'')+'"></span><span>'+(ready?'Llamada iPhone preparada':'Configurar llamada en iPhone')+'</span></div>'+
        '<div class="mini">'+(ready?'SOS normal intentará ejecutar el Atajo CERCA SOS con tu contacto de llamada.':'Configuración única con Atajos de Apple. La llamada sale desde este iPhone.')+'</div>'+
        '<div class="actions" style="margin-top:10px">'+
        '<button id="iosShortcutSetupBtn" class="btn light">'+(ready?'Revisar':'Configurar ahora')+'</button>'+
        (ready?'<button id="iosShortcutTestBtn" class="btn light">Probar llamada</button>':'')+
        '</div>';
      zone.appendChild(card);
      const setup=card.querySelector('#iosShortcutSetupBtn');
      const test=card.querySelector('#iosShortcutTestBtn');
      if(setup)setup.onclick=()=>{setReady(false);setupModal();};
      if(test)test.onclick=testShortcut;
    });
  }

  async function triggerForEmergency(){
    if(!isReady()){
      if(typeof toast==='function')toast('La llamada automática de iPhone todavía no está configurada.','error');
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
      if(isReady()||onboardingShown||typeof S==='undefined'||!S.user||!document.getElementById('sosBtn'))return;
      onboardingShown=true;
      setTimeout(()=>{if(setupStarted())verifyModal();else setupModal();},500);
    }catch{}
  }

  let tries=0;
  const wait=setInterval(()=>{
    tries++;
    installWrapper();
    refreshCard();
    maybeOnboard();
    if((typeof S!=='undefined'&&S.user&&typeof startEmergency==='function')||tries>120)clearInterval(wait);
  },300);

  const appRoot=document.getElementById('app')||document.body;
  const obs=new MutationObserver(()=>{refreshCard();maybeOnboard();});
  obs.observe(appRoot,{childList:true,subtree:true});

  addEventListener('pageshow',()=>{
    refreshCard();
    if(!isReady()&&setupStarted())setTimeout(verifyModal,450);
    else maybeOnboard();
  });
  addEventListener('focus',()=>{
    refreshCard();
    if(!isReady()&&setupStarted())setTimeout(verifyModal,450);
  });
})();