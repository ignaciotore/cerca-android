(()=>{
  function syncNativeSession(){
    try{
      if(!window.CercaNative||typeof window.CercaNative.syncSession!=='function')return;
      if(typeof S==='undefined'||!S.session?.access_token)return;
      window.CercaNative.syncSession(String(S.session.access_token));
      const install=document.getElementById('installAppBtn');
      if(install)install.style.display='none';
    }catch{}
  }

  // La llamada automática se maneja exclusivamente en android-auto-call-watchdog.js.
  // No usamos tel: ni deep links desde la web/PWA para evitar bucles y duplicados.

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
