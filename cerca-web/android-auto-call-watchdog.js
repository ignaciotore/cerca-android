(()=>{
  let confirmedKey='';
  let busy=false;
  let lastAttemptAt=0;
  let cachedPhone='';
  let cachedAt=0;

  function cleanPhone(raw){
    const s=String(raw||'').trim();
    const plus=s.startsWith('+')?'+':'';
    return plus+s.replace(/\D/g,'');
  }

  function emergencyKey(e){
    if(!e)return '';
    return String(e.id||e.emergency_id||e.started_at||e.created_at||'active');
  }

  function isNormalActive(e){
    if(!e)return false;
    const mode=String(e.mode||e.emergency_mode||'normal').toLowerCase();
    const status=String(e.status||'active').toLowerCase();
    return mode!=='silent'&&status!=='resolved'&&status!=='closed'&&status!=='finished';
  }

  function hasBridge(){
    try{return !!window.CercaNative&&typeof window.CercaNative.directCall==='function'}catch{return false}
  }

  function isNativeShell(){
    try{return /CERCA-Native-Android/i.test(navigator.userAgent||'')}catch{return false}
  }

  function canCallNatively(){return isNativeShell()||hasBridge()}

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

  function markConfirmed(key){
    if(!key)return;
    confirmedKey=key;
    busy=false;
  }

  function dispatchNativeCall(phone){
    try{
      // En la app Android real preferimos el esquema interno. WebAppActivity ya
      // intercepta cerca://call y ejecuta startDirectCall() nativamente.
      if(isNativeShell()){
        window.location.href='cerca://call?phone='+encodeURIComponent(phone);
        return true;
      }
      if(hasBridge()){
        window.CercaNative.directCall(phone);
        return true;
      }
    }catch{}
    return false;
  }

  async function trigger(e){
    if(!canCallNatively())return;
    const key=emergencyKey(e);
    if(!key||key===confirmedKey)return;
    if(Date.now()-lastAttemptAt<4500)return;
    lastAttemptAt=Date.now();
    busy=true;

    const phone=await phoneForCall();
    if(!phone){busy=false;return}

    if(!dispatchNativeCall(phone)){
      busy=false;
      return;
    }

    // Si Android abre permiso o llamada, la actividad cambia de foco/visibilidad.
    // Si nada ocurre, liberamos el intento para volver a disparar.
    setTimeout(()=>{
      if(document.visibilityState==='hidden')markConfirmed(key);
      else busy=false;
    },2200);
  }

  async function tick(){
    if(busy||!canCallNatively())return;
    try{
      if(typeof S==='undefined'||!isNormalActive(S.activeEmergency))return;
      await trigger(S.activeEmergency);
    }catch{busy=false}
  }

  setInterval(tick,700);
  window.addEventListener('focus',()=>setTimeout(tick,250));
  document.addEventListener('visibilitychange',()=>{
    if(document.visibilityState==='visible')setTimeout(tick,300);
  });
})();
