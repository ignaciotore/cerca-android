(()=>{
  const ua=navigator.userAgent||'';
  const ios=/iPhone|iPad|iPod/i.test(ua)||(navigator.platform==='MacIntel'&&navigator.maxTouchPoints>1);
  const nativeAndroid=/CERCA-Native-Android/i.test(ua)||window.__CERCA_NATIVE_ANDROID__===true||!!window.CercaNative;
  const standalone=()=>matchMedia('(display-mode: standalone)').matches||navigator.standalone===true;
  if(!ios||nativeAndroid)return;

  // En Safari priorizamos primero instalar CERCA como app.
  if(!standalone()){
    window.__CERCA_LOCATION_ONBOARDING_PENDING__=false;
    try{window.dispatchEvent(new CustomEvent('cerca-location-ready',{detail:{state:'browser'}}))}catch{}
    return;
  }

  let done=false;
  let requested=false;
  window.__CERCA_LOCATION_ONBOARDING_PENDING__=true;

  function finish(state){
    if(done)return;
    done=true;
    window.__CERCA_LOCATION_ONBOARDING_PENDING__=false;
    try{window.dispatchEvent(new CustomEvent('cerca-location-ready',{detail:{state}}))}catch{}
  }

  function loggedInHomeReady(){
    try{return typeof S!=='undefined'&&!!S.user&&!!document.getElementById('sosBtn')}catch{return false}
  }

  function installSafeGetPos(){
    try{
      if(typeof getPos!=='function'||getPos.__cercaIosSafe)return false;
      const original=getPos;
      const wrapped=async function(){
        try{return await original()}
        catch{
          try{if(typeof toast==='function')toast('SOS se activará sin ubicación hasta que iPhone la permita.','error')}catch{}
          return {latitude:null,longitude:null,__cercaNoLocation:true};
        }
      };
      wrapped.__cercaIosSafe=true;
      getPos=wrapped;
      return true;
    }catch{return false}
  }

  function showDenied(){
    if(typeof modal!=='function'){finish('denied');return}
    const w=modal('<h2>Ubicación desactivada</h2><p>CERCA puede seguir funcionando, pero no podrá enviar tu posición hasta que iPhone permita la ubicación.</p><button id="cercaLocationContinue" class="btn primary block">Continuar</button>');
    const b=w?.querySelector('#cercaLocationContinue');
    if(b)b.onclick=()=>{w.remove();finish('denied')};
    else finish('denied');
  }

  function requestNativePermission(){
    if(requested||done||!loggedInHomeReady())return;
    requested=true;
    if(!navigator.geolocation){finish('unsupported');return}

    // En una instalación nueva de CERCA, esta llamada dispara directamente el popup nativo de Apple.
    navigator.geolocation.getCurrentPosition(
      ()=>{
        try{if(typeof toast==='function')toast('Ubicación activada.')}catch{}
        finish('granted');
      },
      e=>{
        if(e?.code===1)showDenied();
        else{
          try{if(typeof toast==='function')toast('No pude obtener la ubicación ahora. CERCA seguirá funcionando.','error')}catch{}
          finish('unavailable');
        }
      },
      {enableHighAccuracy:true,timeout:12000,maximumAge:120000}
    );
  }

  let tries=0;
  const wait=setInterval(()=>{
    tries++;
    installSafeGetPos();
    if(loggedInHomeReady()){
      clearInterval(wait);
      setTimeout(requestNativePermission,250);
    }else if(tries>180){
      clearInterval(wait);finish('timeout');
    }
  },250);

  addEventListener('pageshow',()=>{installSafeGetPos();if(!requested)setTimeout(requestNativePermission,250)});
  addEventListener('focus',()=>installSafeGetPos());
})();
