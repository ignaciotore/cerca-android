(()=>{
  window.__cercaPushRegistered=false;
  window.__cercaPushChecked=false;

  async function pushConfig(){
    return request('/functions/v1/cerca-web-push?action=config',{method:'GET'});
  }

  window.tryWebPush=async function(){
    if(!('Notification' in window)||Notification.permission!=='granted')return false;
    if(!('serviceWorker' in navigator)||!('PushManager' in window))return false;
    try{
      const cfg=await pushConfig();
      window.__cercaPushChecked=true;
      if(!cfg?.enabled||!cfg?.vapidKey){window.__cercaPushRegistered=false;return false}
      const reg=await navigator.serviceWorker.ready;
      let sub=await reg.pushManager.getSubscription();
      if(!sub){
        sub=await reg.pushManager.subscribe({userVisibleOnly:true,applicationServerKey:uint8(cfg.vapidKey)});
      }
      await request('/functions/v1/cerca-web-push?action=register',{method:'POST',body:{subscription:sub.toJSON()}});
      window.__cercaPushRegistered=true;
      window.dispatchEvent(new Event('cerca-push-status'));
      return true;
    }catch(e){
      window.__cercaPushChecked=true;
      window.__cercaPushRegistered=false;
      console.warn('CERCA web push',e);
      window.dispatchEvent(new Event('cerca-push-status'));
      return false;
    }
  };

  async function refreshPushStatus(){
    if(typeof S==='undefined'||!S.user)return;
    try{
      const cfg=await pushConfig();
      window.__cercaPushChecked=true;
      window.__cercaPushRegistered=cfg?.registered===true;
      if(Notification.permission==='granted'&&!window.__cercaPushRegistered){
        await window.tryWebPush();
      }
    }catch{
      window.__cercaPushChecked=true;
      window.__cercaPushRegistered=false;
    }
  }

  const previousRenderNetwork=renderNetwork;
  renderNetwork=function(el){
    previousRenderNetwork(el);
    const note=el.querySelector('.notificationSetup');
    if(!note)return;
    const ios=/iphone|ipad|ipod/i.test(navigator.userAgent);
    const standalone=matchMedia('(display-mode: standalone)').matches||navigator.standalone===true;
    if(ios&&!standalone)return;
    if('Notification' in window&&Notification.permission==='granted'){
      if(window.__cercaPushRegistered){
        note.innerHTML='<strong>✅ Alertas CERCA listas en este teléfono</strong>';
      }else{
        note.innerHTML='<strong>⚠️ Permiso activo, falta completar el registro de alertas</strong><br><button id="finishPushBtn" class="btn primary sm" style="margin-top:10px">Completar registro</button>';
        const b=note.querySelector('#finishPushBtn');
        if(b)b.onclick=async()=>{b.disabled=true;b.textContent='Registrando…';const ok=await window.tryWebPush();if(ok){toast('Alertas CERCA listas.');renderSide()}else{b.disabled=false;b.textContent='Reintentar registro';toast('No pudimos registrar las alertas en este teléfono.','error')}};
      }
    }
  };

  window.addEventListener('cerca-push-status',()=>{try{if(typeof renderSide==='function')renderSide()}catch{}});
  let tries=0;
  const timer=setInterval(async()=>{
    tries++;
    if(typeof S!=='undefined'&&S.user){clearInterval(timer);await refreshPushStatus();try{renderSide()}catch{}}
    else if(tries>120)clearInterval(timer);
  },500);
})();
