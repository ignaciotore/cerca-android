(()=>{
  const STORAGE_KEY='cerca_auto_call_emergency_v2';
  let confirmedKey='';
  let busy=false;
  let lastAttemptAt=0;
  let cachedPhone='';
  let cachedAt=0;

  try{confirmedKey=localStorage.getItem(STORAGE_KEY)||''}catch{}

  function cleanPhone(raw){
    const s=String(raw||'').trim();
    const plus=s.startsWith('+')?'+':'';
    return plus+s.replace(/\D/g,'');
  }

  function emergencyKey(e){
    if(!e)return '';
    return String(e.id||e.emergency_id||e.started_at||e.created_at||'');
  }

  function isNormalActive(e){
    if(!e)return false;
    const mode=String(e.mode||e.emergency_mode||'normal').toLowerCase();
    const status=String(e.status||'active').toLowerCase();
    return mode!=='silent'&&status!=='resolved'&&status!=='closed'&&status!=='finished';
  }

  function isAndroid(){
    try{return /Android/i.test(navigator.userAgent||'')}catch{return false}
  }

  function isNativeContainer(){
    try{return /CERCA-Native-Android\/[^\s]+/i.test(navigator.userAgent||'')||window.__CERCA_NATIVE_ANDROID__===true||!!window.CercaNative}catch{return false}
  }

  function rememberCalled(key){
    confirmedKey=key;
    try{localStorage.setItem(STORAGE_KEY,key)}catch{}
  }

  function forgetCalled(key){
    if(confirmedKey!==key)return;
    confirmedKey='';
    try{localStorage.removeItem(STORAGE_KEY)}catch{}
  }

  function localPhone(){
    try{
      const c=(typeof designatedCall==='function'?designatedCall():null) ||
        ((typeof networkCollections==='function'?(networkCollections().syncedContacts||[]):[]).find(x=>x&&x.call_enabled===true));
      if(!c)return '';
      return cleanPhone((typeof first==='function'?first(c,'phone_e164','phone','target_phone','contact_phone'):null)||c.phone_e164||c.phone||'');
    }catch{return ''}
  }

  async function backendPhone(){
    if(cachedPhone&&Date.now()-cachedAt<30000)return cachedPhone;
    try{
      if(typeof S==='undefined'||!S.user?.id||typeof request!=='function')return '';
      const uid=encodeURIComponent(S.user.id);
      const rows=await request('/rest/v1/cerca_contacts_v2?owner_user_id=eq.'+uid+'&call_enabled=eq.true&select=phone_e164&limit=1');
      const row=Array.isArray(rows)?rows[0]:null;
      const p=cleanPhone(row?.phone_e164||'');
      if(p){cachedPhone=p;cachedAt=Date.now()}
      return p;
    }catch{return ''}
  }

  async function phoneForCall(){return localPhone()||await backendPhone()}

  function dispatchNativeCall(phone){
    try{
      if(isNativeContainer()&&window.CercaNative){
        // In the native container Android has its own once-per-emergency watchdog.
        // Do not also fire from JS or the same SOS can redial after returning from the Phone app.
        return true;
      }
    }catch{}

    try{
      if(isAndroid()){
        window.location.href='cerca://call?phone='+encodeURIComponent(phone);
        return true;
      }
    }catch{}
    return false;
  }

  async function trigger(e){
    if(!isAndroid()&&!isNativeContainer())return;
    const key=emergencyKey(e);
    if(!key||key===confirmedKey)return;
    if(Date.now()-lastAttemptAt<3500)return;
    lastAttemptAt=Date.now();
    busy=true;

    // Native Android handles the call itself and persists the emergency id.
    if(isNativeContainer()){
      rememberCalled(key);
      busy=false;
      return;
    }

    const phone=await phoneForCall();
    if(!phone){busy=false;return}

    // Persist BEFORE leaving the page. Timers are suspended while Android's Phone app is foregrounded.
    rememberCalled(key);
    if(!dispatchNativeCall(phone)){
      forgetCalled(key);
      busy=false;
      return;
    }
    busy=false;
  }

  async function tick(){
    if(busy)return;
    try{
      if(typeof S==='undefined'||!isNormalActive(S.activeEmergency))return;
      await trigger(S.activeEmergency);
    }catch{busy=false}
  }

  setInterval(tick,700);
  window.addEventListener('focus',()=>setTimeout(tick,200));
  document.addEventListener('visibilitychange',()=>{
    if(document.visibilityState==='visible')setTimeout(tick,250);
  });
})();
