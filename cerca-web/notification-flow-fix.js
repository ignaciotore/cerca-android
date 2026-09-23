(()=>{
  function currentAlertId(){return new URLSearchParams(location.search).get('alert')||''}
  function requestedView(){return new URLSearchParams(location.search).get('view')||''}
  async function openRequested(){
    const id=currentAlertId(),view=requestedView();
    if(!id)return;
    try{
      if(view==='medical'&&typeof openAlertInfo==='function'){
        await openAlertInfo(id);
        return;
      }
      if(typeof openAlert==='function')await openAlert(id);
    }catch{}
  }
  let tries=0;
  const timer=setInterval(()=>{
    tries++;
    if(typeof S!=='undefined'&&S.user){clearInterval(timer);openRequested()}
    else if(tries>20)clearInterval(timer);
  },500);

  navigator.serviceWorker?.addEventListener?.('message',e=>{
    if(e.data?.type!=='open-alert'||!e.data.id)return;
    if(e.data.view==='medical'&&typeof openAlertInfo==='function'){
      setTimeout(()=>openAlertInfo(e.data.id),200);
    }else if(typeof openAlert==='function'){
      openAlert(e.data.id);
    }
  });

  const proto=window.ServiceWorkerRegistration?.prototype;
  if(proto?.showNotification&&!proto.__cercaWrapped){
    const original=proto.showNotification;
    proto.showNotification=function(title,options={}){
      const o={...options};
      if(String(title||'').includes('Alerta CERCA')||String(o.tag||'').length){
        o.body=o.body||'Una persona de tu Red CERCA necesita ayuda. Tocá para ver ubicación e información útil autorizada.';
        o.actions=[
          {action:'location',title:'📍 Ver ubicación'},
          {action:'medical',title:'🩺 Información útil'}
        ];
      }
      return original.call(this,title,o);
    };
    proto.__cercaWrapped=true;
  }

  const baseRenderNetwork=renderNetwork;
  renderNetwork=function(el){
    baseRenderNetwork(el);
    const head=el.querySelector('.sectionHead');
    if(!head)return;
    const ios=/iphone|ipad|ipod/i.test(navigator.userAgent);
    const standalone=matchMedia('(display-mode: standalone)').matches||navigator.standalone===true;
    let html='';
    if(ios&&!standalone){
      html='<div class="infoNote notificationSetup"><strong>🔔 Para recibir alertas en iPhone</strong><br>Agregá CERCA a la pantalla de inicio y después activá las notificaciones.</div>';
    }else if('Notification'in window&&Notification.permission!=='granted'){
      const denied=Notification.permission==='denied';
      html='<div class="infoNote notificationSetup"><strong>🔔 Alertas en este teléfono: '+(denied?'bloqueadas':'pendientes')+'</strong><br>'+(denied?'Habilitá las notificaciones de CERCA desde los ajustes del teléfono.':'Activá las notificaciones para recibir los pedidos de ayuda aunque CERCA no esté abierta.')+(denied?'':'<br><button id="inlineNotifyBtn" class="btn primary sm" style="margin-top:10px">Activar notificaciones</button>')+'</div>';
    }else if('Notification'in window&&Notification.permission==='granted'){
      html='<div class="infoNote notificationSetup"><strong>✅ Notificaciones CERCA activas en este teléfono</strong></div>';
    }
    if(html){head.insertAdjacentHTML('afterend',html);const b=document.getElementById('inlineNotifyBtn');if(b)b.onclick=enableNotifications}
  };
})();