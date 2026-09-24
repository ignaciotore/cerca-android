(()=>{
  function cleanPhone(raw){
    const s=String(raw||'').trim();
    const plus=s.startsWith('+')?'+':'';
    return plus+s.replace(/\D/g,'');
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
      const rows=await request('/rest/v1/cerca_contacts_v2?owner_user_id=eq.'+uid+'&call_enabled=eq.true&select=phone_e164&limit=1');
      const row=Array.isArray(rows)?rows[0]:null;
      return cleanPhone(row?.phone_e164||'');
    }catch{return ''}
  }

  async function callNow(){
    const phone=await getCallPhone();
    if(!phone)return false;
    try{
      if(window.CercaNative&&typeof window.CercaNative.directCall==='function'){
        window.CercaNative.directCall(phone);
        return true;
      }
    }catch{}
    try{
      if(/Android/i.test(navigator.userAgent||'')){
        location.href='cerca://call?phone='+encodeURIComponent(phone);
        return true;
      }
    }catch{}
    return false;
  }

  if(typeof startEmergency==='function'&&!startEmergency.__cercaImmediateCall){
    const originalStartEmergency=startEmergency;
    const wrapped=async function(silent){
      await originalStartEmergency(silent);
      try{
        if(!silent&&typeof S!=='undefined'&&S.activeEmergency){
          syncNativeSession();
          await callNow();
        }
      }catch{}
    };
    wrapped.__cercaImmediateCall=true;
    startEmergency=wrapped;
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
