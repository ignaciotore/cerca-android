(()=>{
  let confirmedKey='';
  let pendingKey='';
  let busy=false;
  let lastAttemptAt=0;
  let warnedKey='';

  function phoneForCall(){
    try{
      const c=(typeof designatedCall==='function'?designatedCall():null) ||
        ((typeof networkCollections==='function'?(networkCollections().syncedContacts||[]):[]).find(x=>x&&x.call_enabled===true));
      if(!c)return '';
      const raw=(typeof first==='function'?first(c,'phone_e164','phone','target_phone','contact_phone'):null)||c.phone_e164||c.phone||'';
      const s=String(raw||'').trim();
      const plus=s.startsWith('+')?'+':'';
      return plus+s.replace(/\D/g,'');
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
    return mode!=='silent'&&status!=='resolved'&&status!=='closed'&&status!=='finished';
  }

  function isNative(){
    try{return !!window.CercaNative&&typeof window.CercaNative.directCall==='function'}catch{return false}
  }

  function markConfirmed(key){
    if(!key)return;
    confirmedKey=key;
    pendingKey='';
    busy=false;
    try{sessionStorage.setItem('cerca_last_auto_call',key)}catch{}
  }

  function warnWebOnce(key){
    if(warnedKey===key)return;
    warnedKey=key;
    try{
      if(typeof toast==='function'){
        toast('La alerta CERCA fue enviada. La llamada automática requiere la app Android instalada.','info');
      }
    }catch{}
  }

  function trigger(phone,key){
    pendingKey=key;
    lastAttemptAt=Date.now();
    busy=true;

    // IMPORTANTE: desde navegador/PWA no forzamos abrir la app nativa.
    // Evita el bucle que expulsaba al usuario de CERCA cuando había un SOS activo.
    if(!isNative()){
      busy=false;
      pendingKey='';
      warnWebOnce(key);
      return;
    }

    try{
      window.CercaNative.directCall(phone);
    }catch{
      busy=false;
      return;
    }

    setTimeout(()=>{
      if(document.visibilityState==='hidden')markConfirmed(key);
      else busy=false;
    },1800);
  }

  function tick(){
    if(busy)return;
    try{
      if(typeof S==='undefined')return;
      const e=S.activeEmergency;
      if(!isNormalActive(e))return;
      const key=emergencyKey(e);
      if(!key||key===confirmedKey)return;
      if(Date.now()-lastAttemptAt<5000)return;
      const phone=phoneForCall();
      if(!phone)return;
      trigger(phone,key);
    }catch{busy=false}
  }

  try{confirmedKey=sessionStorage.getItem('cerca_last_auto_call')||''}catch{}
  setInterval(tick,700);
  window.addEventListener('focus',()=>setTimeout(tick,250));
  document.addEventListener('visibilitychange',()=>{
    if(document.visibilityState==='hidden'&&pendingKey)markConfirmed(pendingKey);
    if(document.visibilityState==='visible')setTimeout(tick,350);
  });
})();
