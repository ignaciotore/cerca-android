(()=>{
  const IOS=/iphone|ipad|ipod/i.test(navigator.userAgent);
  const standalone=()=>matchMedia('(display-mode: standalone)').matches||navigator.standalone===true;
  let promptOpen=false;

  async function registerIfPossible(){
    if(!('Notification' in window)||Notification.permission!=='granted')return;
    try{if(typeof tryWebPush==='function')await tryWebPush()}catch{}
  }

  function closePrompt(w){
    try{w?.remove()}catch{}
    promptOpen=false;
  }

  function blockedMessage(){
    if(IOS)return'Las notificaciones están bloqueadas. Abrí Ajustes > Notificaciones > CERCA y habilitalas para recibir alertas de emergencia.';
    return'Las notificaciones están bloqueadas. Habilitalas desde los ajustes de notificaciones de CERCA en tu teléfono.';
  }

  function showPermissionPrompt(){
    if(promptOpen||!('Notification' in window))return;
    if(Notification.permission==='granted'){registerIfPossible();return}
    if(typeof modal!=='function')return;
    promptOpen=true;

    const needsInstall=IOS&&!standalone();
    const denied=Notification.permission==='denied';
    const title=denied?'Activá las notificaciones de CERCA':'No te pierdas una alerta CERCA';
    const text=needsInstall
      ?'En iPhone, las alertas funcionan cuando CERCA está agregada a la pantalla de inicio. Primero instalala y después podremos activar las notificaciones.'
      :denied
        ?blockedMessage()
        :'Necesitamos permiso para avisarte cuando alguien de tu Red CERCA active un SOS, incluso con el teléfono bloqueado o mientras usás otra app.';
    const primary=needsInstall?'CÓMO INSTALAR':denied?'ENTENDIDO':'ACTIVAR NOTIFICACIONES';

    const w=modal('<h2>'+title+'</h2><p>'+text+'</p><button id="cercaNotifyPrimary" class="btn primary block">'+primary+'</button><button id="cercaNotifyLater" class="btn light block" style="margin-top:8px">Ahora no</button>');
    const primaryBtn=w.querySelector('#cercaNotifyPrimary');
    const later=w.querySelector('#cercaNotifyLater');

    later.onclick=()=>closePrompt(w);
    primaryBtn.onclick=async()=>{
      if(needsInstall){
        closePrompt(w);
        const install=document.getElementById('installAppBtn');
        if(install){install.click();return}
        alert('En iPhone: abrí CERCA en Safari, tocá Compartir y elegí “Agregar a pantalla de inicio”.');
        return;
      }
      if(denied){closePrompt(w);alert(blockedMessage());return}
      try{
        const p=await Notification.requestPermission();
        if(p==='granted'){
          await registerIfPossible();
          closePrompt(w);
          if(typeof toast==='function')toast('Notificaciones CERCA activadas.');
        }else{
          closePrompt(w);
          setTimeout(showPermissionPrompt,250);
        }
      }catch{
        closePrompt(w);
      }
    };
  }

  function waitForSession(){
    let tries=0;
    const timer=setInterval(()=>{
      tries++;
      if(typeof S!=='undefined'&&S.user){
        clearInterval(timer);
        if(Notification.permission==='granted')registerIfPossible();
        else setTimeout(showPermissionPrompt,350);
      }else if(tries>1200){
        clearInterval(timer);
      }
    },500);
  }

  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',waitForSession);
  else waitForSession();

  window.addEventListener('focus',()=>{
    if(typeof S!=='undefined'&&S.user&&Notification.permission==='granted')registerIfPossible();
  });
})();