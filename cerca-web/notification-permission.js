(()=>{
  const IOS=/iphone|ipad|ipod/i.test(navigator.userAgent)||(navigator.platform==='MacIntel'&&navigator.maxTouchPoints>1);
  const standalone=()=>matchMedia('(display-mode: standalone)').matches||navigator.standalone===true;
  let promptOpen=false;
  let started=false;

  window.__CERCA_NOTIFICATION_ONBOARDING_PENDING__=IOS&&standalone();

  async function registerIfPossible(){
    if(!('Notification' in window)||Notification.permission!=='granted')return;
    try{if(typeof tryWebPush==='function')await tryWebPush()}catch{}
  }

  function finish(state){
    window.__CERCA_NOTIFICATION_ONBOARDING_PENDING__=false;
    try{window.dispatchEvent(new CustomEvent('cerca-notifications-ready',{detail:{state}}))}catch{}
  }

  function closePrompt(w){
    try{w?.remove()}catch{}
    promptOpen=false;
  }

  function blockedMessage(){
    if(IOS)return'Las notificaciones están desactivadas para CERCA. Podés habilitarlas más adelante desde Ajustes > Notificaciones > CERCA.';
    return'Las notificaciones están bloqueadas. Habilitalas desde los ajustes de notificaciones de CERCA en tu teléfono.';
  }

  function showPermissionPrompt(){
    if(started||promptOpen)return;

    // En iPhone, antes de instalar CERCA como app no pedimos notificaciones ni mostramos otro popup.
    if(IOS&&!standalone()){finish('browser');return}
    if(!('Notification' in window)){finish('unsupported');return}

    if(Notification.permission==='granted'){
      started=true;
      registerIfPossible().finally(()=>finish('granted'));
      return;
    }

    if(typeof modal!=='function')return;
    started=true;
    promptOpen=true;
    const denied=Notification.permission==='denied';
    const title=denied?'Notificaciones desactivadas':'Activar alertas de CERCA';
    const text=denied
      ?blockedMessage()
      :'Permití las notificaciones para recibir un SOS de tu Red CERCA incluso con el iPhone bloqueado.';
    const primary=denied?'CONTINUAR':'ACTIVAR NOTIFICACIONES';

    const w=modal('<h2>'+title+'</h2><p>'+text+'</p><button id="cercaNotifyPrimary" class="btn primary block">'+primary+'</button>');
    const primaryBtn=w?.querySelector('#cercaNotifyPrimary');
    if(!primaryBtn){finish('error');return}

    primaryBtn.onclick=async()=>{
      if(denied){closePrompt(w);finish('denied');return}
      try{
        const p=await Notification.requestPermission();
        if(p==='granted')await registerIfPossible();
        closePrompt(w);
        if(p==='granted'&&typeof toast==='function')toast('Notificaciones CERCA activadas.');
        finish(p||'default');
      }catch{
        closePrompt(w);finish('error');
      }
    };
  }

  function maybeStart(){
    if(started)return;
    if(typeof S==='undefined'||!S.user)return;
    if(window.__CERCA_LOCATION_ONBOARDING_PENDING__)return;
    setTimeout(showPermissionPrompt,250);
  }

  function waitForSession(){
    let tries=0;
    const timer=setInterval(()=>{
      tries++;
      if(typeof S!=='undefined'&&S.user){
        clearInterval(timer);
        maybeStart();
      }else if(tries>1200){
        clearInterval(timer);finish('timeout');
      }
    },500);
  }

  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',waitForSession);
  else waitForSession();

  window.addEventListener('cerca-location-ready',()=>maybeStart());
  window.addEventListener('focus',()=>{
    if(typeof S!=='undefined'&&S.user&&'Notification'in window&&Notification.permission==='granted')registerIfPossible();
  });
})();
