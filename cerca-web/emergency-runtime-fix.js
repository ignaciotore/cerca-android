(()=>{
  function cleanTel(raw){
    const s=String(raw||'').trim();
    const plus=s.startsWith('+')?'+':'';
    return plus+s.replace(/\D/g,'');
  }

  function currentCallContact(){
    try{return typeof designatedCall==='function'?designatedCall():null}catch{return null}
  }

  function openConfiguredCall(){
    const c=currentCallContact();
    const phone=first?.(c,'phone_e164','phone','target_phone','contact_phone')||c?.phone_e164||c?.phone||'';
    const tel=cleanTel(phone);
    if(!tel)return false;
    try{
      if(typeof toast==='function')toast('Abriendo llamada al contacto configurado…');
      window.location.href='tel:'+tel;
      return true;
    }catch{return false}
  }

  if(typeof startEmergency==='function'){
    const baseStartEmergency=startEmergency;
    startEmergency=async function(silent){
      await baseStartEmergency(silent);
      if(!silent && typeof S!=='undefined' && S.activeEmergency){
        setTimeout(()=>openConfiguredCall(),450);
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
      try{if(typeof startPolling==='function')startPolling()}catch{}
      refreshIncoming();
    }else if(tries>120){clearInterval(wait)}
  },500);

  window.addEventListener('focus',refreshIncoming);
  document.addEventListener('visibilitychange',()=>{if(document.visibilityState==='visible')refreshIncoming()});
})();
