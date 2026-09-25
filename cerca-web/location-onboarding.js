(()=>{
  const ua=navigator.userAgent||'';
  const ios=/iPhone|iPad|iPod/i.test(ua)||(navigator.platform==='MacIntel'&&navigator.maxTouchPoints>1);
  const nativeAndroid=/CERCA-Native-Android/i.test(ua)||window.__CERCA_NATIVE_ANDROID__===true||!!window.CercaNative;
  if(!ios||nativeAndroid)return;

  let done=false;
  let onboardingShown=false;
  window.__CERCA_LOCATION_ONBOARDING_PENDING__=true;

  function finish(state){
    if(done)return;
    done=true;
    window.__CERCA_LOCATION_ONBOARDING_PENDING__=false;
    try{window.dispatchEvent(new CustomEvent('cerca-location-ready',{detail:{state}}))}catch{}
  }

  function closeCurrentModal(){
    try{document.querySelectorAll('.modalWrap').forEach(x=>x.remove())}catch{}
  }

  function loggedInHomeReady(){
    try{return typeof S!=='undefined'&&!!S.user&&!!document.getElementById('sosBtn')}catch{return false}
  }

  function requestLocation(onSuccess){
    if(!navigator.geolocation){finish('unsupported');return}
    navigator.geolocation.getCurrentPosition(
      ()=>{
        closeCurrentModal();
        if(typeof toast==='function')toast('Ubicación activada.');
        if(typeof onSuccess==='function')onSuccess();
        finish('granted');
      },
      e=>{
        if(e?.code===1)showDenied();
        else showUnavailable();
      },
      {enableHighAccuracy:true,timeout:15000,maximumAge:300000}
    );
  }

  function showIntro(){
    if(done||!loggedInHomeReady())return;
    if(document.querySelector('.modalWrap')){setTimeout(showIntro,400);return}
    onboardingShown=true;
    const html='<h2>Permitir ubicación</h2>'+
      '<p>CERCA necesita tu ubicación para enviar el lugar de la emergencia cuando pedís ayuda.</p>'+
      '<div class="statusBox" style="text-align:left;margin:14px 0">Tocá <b>Permitir ubicación</b>. El iPhone te va a preguntar si querés darle acceso a CERCA/Safari.</div>'+
      '<button id="cercaLocationAllow" class="btn primary block">Permitir ubicación</button>'+
      '<button id="cercaLocationLater" class="btn light block" style="margin-top:8px">Ahora no</button>';
    const w=typeof modal==='function'?modal(html):null;
    if(!w)return;
    const allow=w.querySelector('#cercaLocationAllow');
    const later=w.querySelector('#cercaLocationLater');
    if(allow)allow.onclick=()=>requestLocation();
    if(later)later.onclick=()=>{w.remove();finish('skipped')};
  }

  function showDenied(){
    closeCurrentModal();
    if(done)return;
    const html='<h2>Ubicación bloqueada en Safari</h2>'+
      '<p>El iPhone ya tiene la ubicación de este sitio en <b>No permitir</b>, por eso CERCA no puede volver a mostrarte el botón “Sí”.</p>'+
      '<div class="statusBox" style="text-align:left;margin:14px 0">'+
      '<div><b>1.</b> En Safari, tocá el <b>menú de página</b> a la izquierda de la barra de direcciones.</div>'+
      '<div style="margin-top:8px"><b>2.</b> Entrá en <b>Configuración del sitio web</b>.</div>'+
      '<div style="margin-top:8px"><b>3.</b> En <b>Ubicación</b>, elegí <b>Permitir</b>.</div>'+
      '<div style="margin-top:8px"><b>4.</b> Volvé a CERCA y tocá el botón de abajo.</div>'+
      '</div>'+
      '<p class="mini">Si no aparece Ubicación ahí: Ajustes → Apps → Safari → Ubicación → elegí Preguntar o Permitir.</p>'+
      '<button id="cercaLocationDone" class="btn primary block">Ya lo habilité</button>'+
      '<button id="cercaLocationLater" class="btn light block" style="margin-top:8px">Ahora no</button>';
    const w=typeof modal==='function'?modal(html):null;
    if(!w)return;
    const retry=w.querySelector('#cercaLocationDone');
    const later=w.querySelector('#cercaLocationLater');
    if(retry)retry.onclick=()=>requestLocation();
    if(later)later.onclick=()=>{w.remove();finish('skipped')};
  }

  function showUnavailable(){
    closeCurrentModal();
    if(done)return;
    const html='<h2>No pude obtener tu ubicación</h2>'+
      '<p>Revisá que los Servicios de ubicación del iPhone estén activados y volvé a intentar.</p>'+
      '<button id="cercaLocationRetry" class="btn primary block">Volver a intentar</button>'+
      '<button id="cercaLocationLater" class="btn light block" style="margin-top:8px">Ahora no</button>';
    const w=typeof modal==='function'?modal(html):null;
    if(!w)return;
    const retry=w.querySelector('#cercaLocationRetry');
    const later=w.querySelector('#cercaLocationLater');
    if(retry)retry.onclick=()=>requestLocation();
    if(later)later.onclick=()=>{w.remove();finish('skipped')};
  }

  async function permissionState(){
    try{
      if(!navigator.permissions?.query)return 'unknown';
      const p=await navigator.permissions.query({name:'geolocation'});
      return p?.state||'unknown';
    }catch{return 'unknown'}
  }

  async function start(){
    if(done||onboardingShown||!loggedInHomeReady())return;
    const state=await permissionState();
    if(state==='granted'){
      requestLocation();
      return;
    }
    if(state==='denied'){
      onboardingShown=true;
      showDenied();
      return;
    }
    showIntro();
  }

  let tries=0;
  const wait=setInterval(()=>{
    tries++;
    start();
    if(done||tries>180)clearInterval(wait);
  },300);

  addEventListener('focus',()=>{
    if(done)return;
    if(onboardingShown&&document.querySelector('.modalWrap'))return;
    onboardingShown=false;
    setTimeout(start,250);
  });
  addEventListener('pageshow',()=>{
    if(done)return;
    if(onboardingShown&&document.querySelector('.modalWrap'))return;
    onboardingShown=false;
    setTimeout(start,250);
  });
})();