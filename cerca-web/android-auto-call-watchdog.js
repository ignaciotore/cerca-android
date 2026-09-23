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

  function warnBlocked(key){
    if(warnedKey===key)return;
    warnedKey=key;
    try{
      if(typeof toast==='function'){
        toast(isNative()
          ?'Android está bloqueando la llamada automática. Habilitá Teléfono para CERCA en Ajustes > Apps > CERCA > Permisos. No hace falta reinstalar.'
          :'Abriste CERCA desde el acceso web. Para la llamada automática usá la app CERCA instalada.','error');
      }
    }catch{}
  }

  function trigger(phone,key){
    pendingKey=key;
    lastAttemptAt=Date.now();
    busy=true;
    let sent=false;
    try{
      if(isNative()){
        window.CercaNative.directCall(phone);
        sent=true;
      }else if(/Android/i.test(navigator.userAgent||'')){
        location.href='cerca://call?phone='+encodeURIComponent(phone);
        sent=true;
      }
    }catch{}
    setTimeout(()=>{
      if(document.visibilityState==='hidden'){
        markConfirmed(key);
      }else{
        busy=false;
        if(sent)warnBlocked(key);
      }
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
