(()=>{
  const install=document.getElementById('installAppBtn'),share=document.getElementById('shareAppBtn');
  const ua=navigator.userAgent||'';
  const ios=/iphone|ipad|ipod/i.test(ua)||(navigator.platform==='MacIntel'&&navigator.maxTouchPoints>1);
  const nativeAndroid=/CERCA-Native-Android/i.test(ua)||window.__CERCA_NATIVE_ANDROID__===true||!!window.CercaNative;
  const standalone=()=>matchMedia('(display-mode: standalone)').matches||navigator.standalone===true;

  window.__CERCA_IS_STANDALONE__=standalone;

  if('serviceWorker'in navigator)addEventListener('load',async()=>{try{const reg=await navigator.serviceWorker.register('/sw.js',{updateViaCache:'none'});reg.update().catch(()=>{})}catch{}});

  if(share)share.onclick=async()=>{
    const data={title:'CERCA',text:'CERCA · Estamos conectados para cuidarte.',url:location.origin+'/'};
    try{
      if(navigator.share){await navigator.share(data);return}
      await navigator.clipboard.writeText(data.url);alert('Link de CERCA copiado.');
    }catch(e){
      if(e?.name==='AbortError')return;
      try{await navigator.clipboard.writeText(data.url);alert('Link de CERCA copiado.')}catch{prompt('Copiá este link:',data.url)}
    }
  };

  if(!install)return;
  if(nativeAndroid||standalone()){
    install.style.display='none';
    return;
  }

  let promptEvent=null;
  addEventListener('beforeinstallprompt',e=>{
    e.preventDefault();promptEvent=e;install.style.display='inline-flex';install.textContent='Instalar CERCA';
  });

  function showIosInstall(){
    if(typeof modal!=='function'){
      alert('En iPhone: tocá Compartir, elegí “Agregar a Inicio”, dejá activado “Abrir como app web” y tocá Agregar.');
      return;
    }
    const html='<h2>Instalar CERCA</h2>'+
      '<p>Queda instalada en tu iPhone como una app, con su ícono y apertura independiente de Safari.</p>'+
      '<div class="statusBox" style="text-align:left;margin:14px 0">'+
      '<div><b>1.</b> Tocá <b>Compartir</b> en Safari.</div>'+
      '<div style="margin-top:8px"><b>2.</b> Elegí <b>Agregar a Inicio</b>.</div>'+
      '<div style="margin-top:8px"><b>3.</b> Dejá activado <b>Abrir como app web</b> y tocá <b>Agregar</b>.</div>'+
      '</div>'+
      '<p class="mini">Apple exige esta confirmación una sola vez. Después abrís CERCA desde el ícono y la app te pide los permisos necesarios.</p>'+
      '<button id="iosInstallOk" class="btn primary block">Entendido</button>';
    const w=modal(html);
    const ok=w?.querySelector('#iosInstallOk');
    if(ok)ok.onclick=()=>w.remove();
  }

  if(ios){
    install.style.display='inline-flex';
    install.textContent='Instalar CERCA';
  }

  install.onclick=async()=>{
    if(promptEvent){
      promptEvent.prompt();
      await promptEvent.userChoice.catch(()=>{});
      promptEvent=null;
      return;
    }
    if(ios){showIosInstall();return}
    alert('Abrí el menú del navegador y elegí “Instalar app” o “Agregar a pantalla principal”.');
  };

  addEventListener('appinstalled',()=>install.style.display='none');
  addEventListener('pageshow',()=>{if(standalone())install.style.display='none'});
})();
