(()=>{
  let lastTriggered='';
  let busy=false;

  function phoneForCall(){
    try{
      const c=(typeof designatedCall==='function'?designatedCall():null) ||
        ((typeof networkCollections==='function'?(networkCollections().syncedContacts||[]):[]).find(x=>x&&x.call_enabled===true));
      if(!c)return '';
      const raw=(typeof first==='function'?first(c,'phone_e164','phone','target_phone','contact_phone'):null)||c.phone_e164||c.phone||'';
      const s=String(raw||'').trim();
      const plus=s.startsWith('+')?'+':'';
      const tel=plus+s.replace(/\D/g,'');
      return tel;
    }catch{return ''}
  }

  function emergencyKey(e){
    if(!e)return '';
    return String(e.id||e.emergency_id||e.started_at||e.created_at||'active');
  }

  function isNormalActive(e){
    if(!e)return false;
    const mode=String(e.mode||e.emergency_mode||'normal').toLowerCase();
    const status=String(e.status||'active').toLowerCase();
    return mode!=='silent' && status!=='resolved' && status!=='closed' && status!=='finished';
  }

  function triggerNative(phone){
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

  function tick(){
    if(busy)return;
    try{
      if(typeof S==='undefined')return;
      const e=S.activeEmergency;
      if(!isNormalActive(e))return;
      const key=emergencyKey(e);
      if(!key||key===lastTriggered)return;
      const phone=phoneForCall();
      if(!phone)return;
      busy=true;
      lastTriggered=key;
      try{sessionStorage.setItem('cerca_last_auto_call',key)}catch{}
      triggerNative(phone);
      setTimeout(()=>{busy=false},1500);
    }catch{busy=false}
  }

  try{lastTriggered=sessionStorage.getItem('cerca_last_auto_call')||''}catch{}
  setInterval(tick,350);
  window.addEventListener('focus',tick);
  document.addEventListener('visibilitychange',()=>{if(document.visibilityState==='visible')tick()});
})();
