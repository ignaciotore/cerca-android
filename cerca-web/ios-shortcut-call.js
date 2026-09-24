(()=>{
  const ua=navigator.userAgent||'';
  const ios=/iPhone|iPad|iPod/i.test(ua)||(navigator.platform==='MacIntel'&&navigator.maxTouchPoints>1);
  const nativeAndroid=/CERCA-Native-Android/i.test(ua)||window.__CERCA_NATIVE_ANDROID__===true||!!window.CercaNative;
  if(!ios||nativeAndroid)return;

  const SHORTCUT_NAME='CERCA SOS';
  const READY_KEY='cerca_ios_shortcut_ready_v1';
  const CALLED_PREFIX='cerca_ios_shortcut_called_v1:';

  function cleanPhone(raw){
    const s=String(raw||'').trim();
    if(!s)return '';
    const digits=s.replace(/\D/g,'');
    if(!digits)return '';
    return s.startsWith('+')?('+'+digits):digits;
  }

  function isReady(){try{return localStorage.getItem(READY_KEY)==='1'}catch{return false}}
  function setReady(v){try{v?localStorage.setItem(READY_KEY,'1'):localStorage.removeItem(READY_KEY)}catch{}}
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

  function setupModal(){
    const html='<h2>Llamada automática en iPhone</h2>'+
      '<p>Configuración única. CERCA va a pasarle al Atajo el teléfono elegido para llamada.</p>'+
      '<div class="statusBox" style="text-align:left;margin:14px 0">'+
      '<div><b>1.</b> Tocá <b>Crear Atajo CERCA SOS</b>.</div>'+
      '<div style="margin-top:8px"><b>2.</b> Poné el nombre exacto <b>CERCA SOS</b>.</div>'+
      '<div style="margin-top:8px"><b>3.</b> Agregá <b>Obtener números de teléfono de Entrada del atajo</b>.</div>'+
      '<div style="margin-top:8px"><b>4.</b> Agregá <b>Llamar</b> usando el número obtenido.</div>'+
      '<div style="margin-top:8px"><b>5.</b> En Detalles → Privacidad, activá <b>Permitir ejecutar bloqueado</b> si aparece.</div>'+
      '</div>'+
      '<button id="iosShortcutCreate" class="btn primary block">Crear Atajo CERCA SOS</button>'+
      '<button id="iosShortcutReady" class="btn light block" style="margin-top:8px">Ya lo configuré</button>'+
      '<button id="iosShortcutClose" class="btn light block" style="margin-top:8px">Ahora no</button>';
    const w=typeof modal==='function'?modal(html):null;
    if(!w)return;
    const create=w.querySelector('#iosShortcutCreate');
    const ready=w.querySelector('#iosShortcutReady');
    const close=w.querySelector('#iosShortcutClose');
    if(create)create.onclick=()=>{location.href='shortcuts://create-shortcut'};
    if(ready)ready.onclick=()=>{setReady(true);w.remove();refreshCard();if(typeof toast==='function')toast('Llamada automática de iPhone activada.');};
    if(close)close.onclick=()=>w.remove();
  }

  async function testShortcut(){
    const phone=await getCallPhone();
    if(!phone){if(typeof toast==='function')toast('Elegí primero un contacto para llamada en Tu Red CERCA.','error');return}
    if(!confirm('La prueba va a iniciar una llamada real a '+phone+'. ¿Continuar?'))return;
    launchShortcut(phone);
  }

  function refreshCard(){
    const sos=document.getElementById('sosBtn');
    const old=document.getElementById('iosShortcutCard');
    if(!sos){if(old)old.remove();return}
    if(old)old.remove();
    const zone=sos.closest('.sosZone')||sos.parentElement;
    if(!zone)return;
    const card=document.createElement('div');
    card.id='iosShortcutCard';
    card.className='statusBox';
    card.style.marginTop='14px';
    const ready=isReady();
    card.innerHTML='<div class="statusLine"><span class="dot '+(ready?'live':'')+'"></span><span>'+(ready?'Llamada iPhone preparada':'Activar llamada automática en iPhone')+'</span></div>'+
      '<div class="mini">'+(ready?'SOS intentará ejecutar el Atajo CERCA SOS con tu contacto de llamada.':'Se configura una sola vez con la app Atajos de Apple. La llamada sale desde este iPhone.')+'</div>'+
      '<div class="actions" style="margin-top:10px">'+
      '<button id="iosShortcutSetupBtn" class="btn light">'+(ready?'Revisar configuración':'Configurar')+'</button>'+
      (ready?'<button id="iosShortcutTestBtn" class="btn light">Probar llamada</button>':'')+
      '</div>';
    zone.appendChild(card);
    const setup=card.querySelector('#iosShortcutSetupBtn');
    const test=card.querySelector('#iosShortcutTestBtn');
    if(setup)setup.onclick=setupModal;
    if(test)test.onclick=testShortcut;
  }

  async function triggerForEmergency(){
    if(!isReady())return;
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

  let tries=0;
  const wait=setInterval(()=>{tries++;if(installWrapper()||tries>120)clearInterval(wait);refreshCard()},250);
  const obs=new MutationObserver(()=>refreshCard());
  obs.observe(document.getElementById('app')||document.body,{childList:true,subtree:true});
  addEventListener('pageshow',refreshCard);
})();