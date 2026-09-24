(()=>{
  const install=document.getElementById('installAppBtn'),share=document.getElementById('shareAppBtn');
  const ua=navigator.userAgent||'';
  const ios=/iphone|ipad|ipod/i.test(ua);
  const android=/android/i.test(ua);
  const nativeAndroid=/CERCA-Native-Android/i.test(ua)||window.__CERCA_NATIVE_ANDROID__===true;
  const standalone=matchMedia('(display-mode: standalone)').matches||navigator.standalone===true;
  const playUrl='https://play.google.com/store/apps/details?id=com.help.seguridad';

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

  // Dentro de la app Android nativa no mostramos ninguna instalación web paralela.
  if(nativeAndroid){install.style.display='none';return}

  // En Android CERCA necesita la app nativa para poder realizar la llamada SOS directa.
  // Evitamos ofrecer la PWA, que visualmente era igual pero no tenía permiso CALL_PHONE.
  if(android){
    install.style.display='inline-flex';
    install.textContent='Instalar CERCA';
    install.onclick=()=>{location.href=playUrl};
    return;
  }

  if(standalone){install.style.display='none';return}

  let promptEvent=null;
  addEventListener('beforeinstallprompt',e=>{e.preventDefault();promptEvent=e;install.style.display='inline-flex';install.textContent='Instalar app'});
  if(ios){install.style.display='inline-flex';install.textContent='Agregar a inicio'}
  install.onclick=async()=>{
    if(promptEvent){promptEvent.prompt();await promptEvent.userChoice.catch(()=>{});promptEvent=null;install.style.display='none';return}
    if(ios){alert('En iPhone/iPad: abrí CERCA en Safari, tocá Compartir y elegí “Agregar a pantalla de inicio”.');return}
    alert('Abrí el menú del navegador y elegí “Instalar app” o “Agregar a pantalla principal”.');
  };
  addEventListener('appinstalled',()=>install.style.display='none');
})();