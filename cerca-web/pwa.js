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

  function guideIosInstall(){
    // iOS no expone una API web para disparar "Agregar a Inicio". Evitamos un modal
    // y convertimos el propio botón en una instrucción breve y no bloqueante.
    install.textContent='⋯ → Compartir → Agregar a Inicio';
    install.setAttribute('aria-label','En Safari: abrí el menú de página, tocá Compartir y luego Agregar a Inicio');
    install.style.maxWidth='260px';
    install.style.whiteSpace='normal';
    install.style.lineHeight='1.15';
    install.style.textAlign='center';
    install.style.height='auto';
    install.style.minHeight='44px';
    install.style.padding='8px 12px';
    setTimeout(()=>{
      if(!standalone()){
        install.textContent='Instalar CERCA';
        install.removeAttribute('aria-label');
        install.style.maxWidth='';install.style.whiteSpace='';install.style.lineHeight='';install.style.textAlign='';install.style.height='';install.style.minHeight='';install.style.padding='';
      }
    },12000);
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
    if(ios){guideIosInstall();return}
    alert('Abrí el menú del navegador y elegí “Instalar app” o “Agregar a pantalla principal”.');
  };

  addEventListener('appinstalled',()=>install.style.display='none');
  addEventListener('pageshow',()=>{if(standalone())install.style.display='none'});
})();
