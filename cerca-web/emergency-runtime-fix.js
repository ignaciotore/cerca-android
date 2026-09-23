(()=>{
  function cleanTel(raw){
    const s=String(raw||'').trim();
    const plus=s.startsWith('+')?'+':'';
    return plus+s.replace(/\D/g,'');
  }

  function currentCallContact(){
    try{return typeof designatedCall==='function'?designatedCall():null}catch{return null}
  }

  function callInfo(){
    const c=currentCallContact();
    if(!c)return null;
    const phone=(typeof first==='function'?first(c,'phone_e164','phone','target_phone','contact_phone'):null)||c.phone_e164||c.phone||'';
    const tel=cleanTel(phone);
    if(!tel)return null;
    const name=c.display_name||c.full_name||c.name||'contacto de llamada';
    return {name,tel};
  }

  function isNativeAndroid(){
    try{return !!window.CercaNative&&typeof window.CercaNative.directCall==='function'}catch{return false}
  }

  function triggerConfiguredCall(){
    const info=callInfo();
    if(!info)return false;
    try{
      if(isNativeAndroid()){
        window.CercaNative.directCall(info.tel);
        return true;
      }
    }catch{}
    try{
      // Fallback web: Android/iOS pueden abrir el marcador, pero la llamada automática
      // real se realiza únicamente dentro de la app Android nativa.
      window.location.href='tel:'+info.tel;
      return true;
    }catch{return false}
  }

  function syncNativeSession(){
    try{
      if(!window.CercaNative||typeof window.CercaNative.syncSession!=='function')return;
      if(typeof S==='undefined'||!S.session?.access_token)return;
      window.CercaNative.syncSession(String(S.session.access_token));
      const install=document.getElementById('installAppBtn');
      if(install)install.style.display='none';
    }catch{}
  }

  if(typeof startEmergency==='function'){
    const baseStartEmergency=startEmergency;
    startEmergency=async function(silent){
      await baseStartEmergency(silent);
      if(!silent&&typeof S!=='undefined'&&S.activeEmergency){
        // Igual que la app Android original: una vez activado el SOS normal,
        // la llamada al contacto configurado sale automáticamente.
        setTimeout(triggerConfiguredCall,150);
      }
    };
  }

  async function refreshIncoming(){
    try{
      if(typeof S==='undefined'||!S.user||typeof loadNetwork!=='function')return;
      await loadNetwork();
      const alerts=typeof networkCollections==='function'?networkCollections().alerts:[];
      const firstAlert=alerts?.[0];
      const id=String(firstAlert?.id||firstAlert?.emergency_id||'');
      if(id){
        if(typeof S.lastIncomingId!=='undefined')S.lastIncomingId=id;
        if(document.visibilityState==='visible'&&typeof openAlert==='function'){
          const current=String(S.currentAlert?.id||S.currentAlert?.emergency_id||'');
          if(current!==id)await openAlert(id);
        }
      }
      if(typeof renderBelow==='function')renderBelow();
    }catch{}
  }

  let tries=0;
  const wait=setInterval(()=>{
    tries++;
    if(typeof S!=='undefined'&&S.user){
      clearInterval(wait);
      syncNativeSession();
      try{if(typeof startPolling==='function')startPolling()}catch{}
      refreshIncoming();
    }else if(tries>120){clearInterval(wait)}
  },500);

  window.addEventListener('focus',()=>{syncNativeSession();refreshIncoming()});
  document.addEventListener('visibilitychange',()=>{if(document.visibilityState==='visible'){syncNativeSession();refreshIncoming()}});
})();
