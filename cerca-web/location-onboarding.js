(()=>{
  const ua=navigator.userAgent||'';
  const ios=/iPhone|iPad|iPod/i.test(ua)||(navigator.platform==='MacIntel'&&navigator.maxTouchPoints>1);
  const nativeAndroid=/CERCA-Native-Android/i.test(ua)||window.__CERCA_NATIVE_ANDROID__===true||!!window.CercaNative;
  if(!ios||nativeAndroid)return;

  let started=false;
  let done=false;
  let deniedModalShown=false;
  window.__CERCA_LOCATION_ONBOARDING_PENDING__=true;

  function finish(state){
    if(done)return;
    done=true;
    window.__CERCA_LOCATION_ONBOARDING_PENDING__=false;
    try{window.dispatchEvent(new CustomEvent('cerca-location-ready',{detail:{state}}))}catch{}
  }

  function showDenied(){
    if(deniedModalShown)return;
    if(document.querySelector('.modalWrap')){setTimeout(showDenied,500);return}
    deniedModalShown=true;
    const html='<h2>Activar ubicación</h2>'+
      '<p>CERCA necesita tu ubicación para enviar el lugar de la emergencia cuando pedís ayuda.</p>'+
      '<div class="statusBox" style="text-align:left;margin:14px 0">En iPhone, permití la ubicación para CERCA/Safari. Si antes elegiste “No permitir”, habilitala desde Ajustes y volvé a CERCA.</div>'+
      '<button id="cercaLocationRetry" class="btn primary block">Volver a intentar</button>'+
      '<button id="cercaLocationLater" class="btn light block" style="margin-top:8px">Ahora no</button>';
    const w=typeof modal==='function'?modal(html):null;
    if(!w)return;
    const retry=w.querySelector('#cercaLocationRetry');
    const later=w.querySelector('#cercaLocationLater');
    if(retry)retry.onclick=()=>{w.remove();deniedModalShown=false;started=false;done=false;window.__CERCA_LOCATION_ONBOARDING_PENDING__=true;ask();};
    if(later)later.onclick=()=>w.remove();
  }

  function ask(){
    if(started||done)return;
    try{
      if(typeof S==='undefined'||!S.user||!document.getElementById('sosBtn'))return;
    }catch{return}
    started=true;
    if(!navigator.geolocation){finish('unsupported');return}
    navigator.geolocation.getCurrentPosition(
      ()=>finish('granted'),
      e=>{
        if(e?.code===1){finish('denied');showDenied();}
        else finish('unavailable');
      },
      {enableHighAccuracy:true,timeout:15000,maximumAge:300000}
    );
  }

  let tries=0;
  const wait=setInterval(()=>{
    tries++;
    ask();
    if(done||tries>180)clearInterval(wait);
  },300);

  addEventListener('focus',()=>{if(!done)ask()});
  addEventListener('pageshow',()=>{if(!done)ask()});
})();