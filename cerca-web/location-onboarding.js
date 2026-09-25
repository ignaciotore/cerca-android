(()=>{
  const ua=navigator.userAgent||'';
  const ios=/iPhone|iPad|iPod/i.test(ua)||(navigator.platform==='MacIntel'&&navigator.maxTouchPoints>1);
  const nativeAndroid=/CERCA-Native-Android/i.test(ua)||window.__CERCA_NATIVE_ANDROID__===true||!!window.CercaNative;
  if(!ios||nativeAndroid)return;

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

  // En iPhone el SOS nunca debe quedar bloqueado porque la ubicación esté denegada.
  // Si iOS no entrega posición, la emergencia se crea igual y el tracking volverá a intentar después.
  function installSafeGetPos(){
    try{
      if(typeof getPos!=='function'||getPos.__cercaIosSafe)return false;
      const original=getPos;
      const wrapped=async function(){
        try{return await original()}
        catch(e){
          try{if(typeof toast==='function')toast('SOS se activará sin ubicación hasta que iPhone la permita.','error')}catch{}
          return {latitude:null,longitude:null,__cercaNoLocation:true};
        }
      };
      wrapped.__cercaIosSafe=true;
      getPos=wrapped;
      return true;
    }catch{return false}
  }

  function requestNativePermission(){
    if(requested||done||!loggedInHomeReady())return;
    requested=true;
    if(!navigator.geolocation){finish('unsupported');return}

    // Esta llamada es la que hace aparecer el aviso nativo de iOS cuando el sitio está en “Preguntar”.
    navigator.geolocation.getCurrentPosition(
      ()=>{
        try{if(typeof toast==='function')toast('Ubicación activada.')}catch{}
        finish('granted');
      },
      e=>{
        // Si Safari ya recordaba “No permitir”, iOS no deja que una web fuerce otro diálogo.
        // No frenamos CERCA: seguimos con SOS y llamada, simplemente sin coordenadas hasta que se habilite.
        try{
          if(typeof toast==='function'){
            toast(e?.code===1?'CERCA seguirá funcionando; por ahora iPhone no comparte la ubicación.':'No pude obtener la ubicación ahora. CERCA seguirá funcionando.','error');
          }
        }catch{}
        finish(e?.code===1?'denied':'unavailable');
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
      // Pedimos el permiso apenas termina de abrir CERCA. En un iPhone que todavía está en “Preguntar”
      // aparece directamente el popup nativo de Apple.
      setTimeout(requestNativePermission,250);
    }else if(tries>180){
      clearInterval(wait);finish('timeout');
    }
  },250);

  addEventListener('pageshow',()=>{installSafeGetPos();if(!requested)setTimeout(requestNativePermission,250)});
  addEventListener('focus',()=>{installSafeGetPos()});
})();