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
})();