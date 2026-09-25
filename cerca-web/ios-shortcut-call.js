(()=>{
  const ua=navigator.userAgent||'';
  const ios=/iPhone|iPad|iPod/i.test(ua)||(navigator.platform==='MacIntel'&&navigator.maxTouchPoints>1);
  const nativeAndroid=/CERCA-Native-Android/i.test(ua)||window.__CERCA_NATIVE_ANDROID__===true||!!window.CercaNative;
  const standalone=()=>matchMedia('(display-mode: standalone)').matches||navigator.standalone===true;
  if(!ios||nativeAndroid)return;

  const CALLED_PREFIX='cerca_ios_direct_called_v1:';

  function cleanPhone(raw){
    const s=String(raw||'').trim();
    if(!s)return '';
    const digits=s.replace(/\D/g,'');
    if(!digits)return '';
    return s.startsWith('+')?('+'+digits):digits;
  }

  function emergencyId(){
    try{return String(S?.activeEmergency?.id||S?.activeEmergency?.emergency_id||'')}catch{return''}
  }
  function wasCalled(id){try{return sessionStorage.getItem(CALLED_PREFIX+id)==='1'}catch{return false}}
  function markCalled(id){try{sessionStorage.setItem(CALLED_PREFIX+id,'1')}catch{}}

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

  async function callConfiguredContact(){
    const phone=await getCallPhone();
    if(!phone){
      if(typeof toast==='function')toast('Elegí primero un contacto para llamada en Mi Red CERCA.','error');
      return false;
    }
    // iOS decide la confirmación final de la llamada. CERCA abre directamente
    // el marcador con el contacto configurado, sin instalar ni importar Atajos.
    location.href='tel:'+phone;
    return true;
  }

  async function triggerForEmergency(){
    if(!standalone())return;
    const id=emergencyId();
    if(!id||wasCalled(id))return;
    markCalled(id);
    await callConfiguredContact();
  }

  function removeOldShortcutUi(){
    document.getElementById('iosShortcutCard')?.remove();
    document.querySelectorAll('.modalWrap').forEach(w=>{
      const t=(w.textContent||'').toLowerCase();
      if(t.includes('atajo')||t.includes('activar llamada en iphone'))w.remove();
    });
    try{
      ['cerca_ios_shortcut_ready_v6','cerca_ios_shortcut_called_v6:'].forEach(k=>localStorage.removeItem(k));
      sessionStorage.removeItem('cerca_ios_shortcut_setup_started_v6');
    }catch{}
  }

  function installWrapper(){
    if(!standalone())return false;
    if(typeof startEmergency!=='function'||startEmergency.__cercaIosDirectCall)return false;
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
    wrapped.__cercaIosDirectCall=true;
    startEmergency=wrapped;
    return true;
  }

  removeOldShortcutUi();
  let tries=0;
  const wait=setInterval(()=>{
    tries++;
    removeOldShortcutUi();
    installWrapper();
    if((typeof S!=='undefined'&&S.user&&typeof startEmergency==='function')||tries>180)clearInterval(wait);
  },300);

  const appRoot=document.getElementById('app')||document.body;
  const obs=new MutationObserver(()=>{removeOldShortcutUi();installWrapper()});
  obs.observe(appRoot,{childList:true,subtree:true});
  addEventListener('pageshow',()=>{removeOldShortcutUi();installWrapper()});
  addEventListener('focus',()=>{removeOldShortcutUi();installWrapper()});
})();
