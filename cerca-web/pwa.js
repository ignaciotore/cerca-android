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

  function ensureIosInstallStyles(){
    if(document.getElementById('cercaIosInstallStyles'))return;
    const style=document.createElement('style');
    style.id='cercaIosInstallStyles';
    style.textContent=`
      .cerca-install-backdrop{position:fixed;inset:0;z-index:2147483646;background:rgba(18,34,32,.55);display:flex;align-items:center;justify-content:center;padding:20px;box-sizing:border-box}
      .cerca-install-card{width:min(100%,420px);background:#fff;border-radius:24px;padding:24px 20px 20px;box-shadow:0 24px 60px rgba(0,0,0,.24);color:#193431;font-family:inherit;box-sizing:border-box;text-align:center}
      .cerca-install-title{font-size:25px;font-weight:800;line-height:1.15;margin:0 0 12px}
      .cerca-install-text{font-size:18px;line-height:1.35;margin:0 0 20px;color:#314b48}
      .cerca-install-primary{width:100%;border:0;border-radius:16px;padding:16px;background:#2d7774;color:#fff;font-size:19px;font-weight:800;min-height:56px}
      .cerca-install-help{font-size:16px;line-height:1.35;margin:16px 0 0;padding:14px;background:#f5f8f7;border-radius:14px;color:#314b48;display:none}
      .cerca-install-close{width:100%;border:0;border-radius:14px;padding:13px 16px;background:#2d7774;color:#fff;font-size:18px;font-weight:800;min-height:52px}
      .cerca-install-share{font-size:24px;font-weight:800;line-height:1.3;margin:6px 0 22px;padding:18px 14px;border-radius:16px;background:#f5f8f7;border:1px solid #e5ecea}
    `;
    document.head.appendChild(style);
  }

  function closeIosInstallGuide(){
    document.getElementById('cercaIosInstallGuide')?.remove();
  }

  function makeDialog(title,body){
    closeIosInstallGuide();
    ensureIosInstallStyles();
    const backdrop=document.createElement('div');
    backdrop.id='cercaIosInstallGuide';
    backdrop.className='cerca-install-backdrop';
    backdrop.setAttribute('role','dialog');
    backdrop.setAttribute('aria-modal','true');
    backdrop.setAttribute('aria-label',title);
    backdrop.innerHTML=`<div class="cerca-install-card"><h2 class="cerca-install-title">${title}</h2>${body}</div>`;
    backdrop.addEventListener('click',e=>{if(e.target===backdrop)closeIosInstallGuide()});
    document.body.appendChild(backdrop);
    return backdrop;
  }

  function safariInstallUrl(){
    const u=new URL(location.href);
    u.searchParams.set('cerca_install','1');
    return 'x-safari-https://'+u.host+u.pathname+u.search+u.hash;
  }

  function showOpenInSafari(){
    const d=makeDialog('Instalar CERCA',`
      <p class="cerca-install-text">Tocá este botón. CERCA se abre en Safari para terminar la instalación.</p>
      <button class="cerca-install-primary" type="button">ABRIR EN SAFARI</button>
      <p class="cerca-install-help">Si no se abre: tocá <b>⋯</b> abajo a la derecha y elegí <b>Abrir en Safari</b>.</p>`);
    const btn=d.querySelector('.cerca-install-primary');
    const help=d.querySelector('.cerca-install-help');
    btn.onclick=()=>{
      let left=false;
      const mark=()=>{left=true};
      addEventListener('pagehide',mark,{once:true});
      document.addEventListener('visibilitychange',()=>{if(document.hidden)left=true},{once:true});
      location.href=safariInstallUrl();
      setTimeout(()=>{if(!left&&document.visibilityState==='visible'){help.style.display='block';btn.textContent='ABRIR EN SAFARI'}} ,1400);
    };
  }

  function showFinishInSafari(){
    const d=makeDialog('Último paso',`
      <p class="cerca-install-text">En Safari, hacé esto:</p>
      <div class="cerca-install-share">Compartir ↑<br>→ Agregar a Inicio</div>
      <button class="cerca-install-close" type="button">Entendido</button>`);
    d.querySelector('.cerca-install-close').onclick=closeIosInstallGuide;
    try{
      const u=new URL(location.href);
      u.searchParams.delete('cerca_install');
      history.replaceState(null,'',u.pathname+u.search+u.hash);
    }catch{}
  }

  if(ios){
    install.style.display='inline-flex';
    install.textContent='Instalar CERCA';
    try{
      if(new URL(location.href).searchParams.get('cerca_install')==='1')setTimeout(showFinishInSafari,350);
    }catch{}
  }

  install.onclick=async()=>{
    if(promptEvent){
      promptEvent.prompt();
      await promptEvent.userChoice.catch(()=>{});
      promptEvent=null;
      return;
    }
    if(ios){showOpenInSafari();return}
    alert('Abrí el menú del navegador y elegí “Instalar app” o “Agregar a pantalla principal”.');
  };

  addEventListener('appinstalled',()=>install.style.display='none');
  addEventListener('pageshow',()=>{if(standalone())install.style.display='none'});
})();
