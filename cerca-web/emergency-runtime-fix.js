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

  function openConfiguredCall(){
    const info=callInfo();
    if(!info)return false;
    window.location.href='tel:'+info.tel;
    return true;
  }

  function showCallPrompt(){
    const info=callInfo();
    if(!info)return;
    const existing=document.getElementById('cercaCallPrompt');
    if(existing)return;
    const w=modal('<div id="cercaCallPrompt"><h2>SOS activado</h2><p>La alerta ya fue enviada a tu Red CERCA.</p><button id="cercaCallNow" class="btn danger block" style="margin-top:16px;font-size:20px;padding:18px">📞 LLAMAR A '+esc(info.name).toUpperCase()+'</button><button id="cercaCallLater" class="btn light block" style="margin-top:8px">Seguir sin llamar</button></div>');
    const callBtn=w.querySelector('#cercaCallNow');
    const later=w.querySelector('#cercaCallLater');
    if(callBtn)callBtn.onclick=()=>openConfiguredCall();
    if(later)later.onclick=()=>w.remove();
  }

  function installActiveCallButton(){
    try{
      if(typeof S==='undefined'||!S.activeEmergency||S.activeEmergency.mode==='silent')return;
      const info=callInfo();
      if(!info)return;
      const resolve=document.getElementById('resolveBtn');
      if(!resolve||document.getElementById('activeCallBtn'))return;
      const b=document.createElement('button');
      b.id='activeCallBtn';
      b.className='btn danger block';
      b.style.margin='10px auto';
      b.style.maxWidth='360px';
      b.style.fontSize='18px';
      b.textContent='📞 LLAMAR A '+String(info.name).toUpperCase();
      b.onclick=()=>openConfiguredCall();
      resolve.parentElement?.insertBefore(b,resolve);
    }catch{}
  }

  if(typeof startEmergency==='function'){
    const baseStartEmergency=startEmergency;
    startEmergency=async function(silent){
      await baseStartEmergency(silent);
      if(!silent&&typeof S!=='undefined'&&S.activeEmergency){
        installActiveCallButton();
        setTimeout(showCallPrompt,150);
      }
    };
  }

  if(typeof renderDashboard==='function'){
    const baseRenderDashboard=renderDashboard;
    renderDashboard=function(){
      const r=baseRenderDashboard.apply(this,arguments);
      setTimeout(installActiveCallButton,0);
      return r;
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
      installActiveCallButton();
      refreshIncoming();
    }else if(tries>120){clearInterval(wait)}
  },500);

  window.addEventListener('focus',()=>{installActiveCallButton();refreshIncoming()});
  document.addEventListener('visibilitychange',()=>{if(document.visibilityState==='visible'){installActiveCallButton();refreshIncoming()}});
})();
