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

  function resetInstallLabel(){
    if(standalone())return;
    install.textContent='Instalar CERCA';
    install.removeAttribute('aria-label');
    install.style.maxWidth='';install.style.whiteSpace='';install.style.lineHeight='';install.style.textAlign='';install.style.height='';install.style.minHeight='';install.style.padding='';
  }

  async function openIosInstall(){
    // iOS no permite que una web instale una PWA sin la confirmación del sistema.
    // Abrimos directamente la hoja nativa desde el toque en "Instalar CERCA" para
    // eliminar nuestro popup y reducir el flujo a elegir "Agregar a Inicio" y confirmar.
    install.textContent='Elegí “Agregar a Inicio”';
    install.setAttribute('aria-label','En la hoja de iPhone elegí Agregar a Inicio');
    install.style.maxWidth='240px';
    install.style.whiteSpace='normal';
    install.style.lineHeight='1.15';
    install.style.textAlign='center';
    install.style.height='auto';
    install.style.minHeight='44px';
    install.style.padding='8px 12px';

    try{
      if(navigator.share){
        await navigator.share({title:'CERCA',text:'Instalar CERCA en este iPhone',url:location.href});
        setTimeout(resetInstallLabel,2500);
        return;
      }
    }catch(e){
      if(e?.name==='AbortError'){setTimeout(resetInstallLabel,1200);return}
    }

    // Fallback para iOS/versiones donde Web Share no esté disponible.
    install.textContent='Compartir → Agregar a Inicio';
    setTimeout(resetInstallLabel,10000);
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
    if(ios){await openIosInstall();return}
    alert('Abrí el menú del navegador y elegí “Instalar app” o “Agregar a pantalla principal”.');
  };

  addEventListener('appinstalled',()=>install.style.display='none');
  addEventListener('pageshow',()=>{if(standalone())install.style.display='none'});
})();
