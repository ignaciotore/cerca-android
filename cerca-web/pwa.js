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
      .cerca-install-title{font-size:25px;font-weight:800;line-height:1.15;margin:0 0 20px}
      .cerca-install-steps{display:grid;gap:12px;margin:0 0 20px;text-align:left}
      .cerca-install-step{display:flex;gap:12px;align-items:center;background:#f5f8f7;border:1px solid #e5ecea;border-radius:16px;padding:14px;font-size:18px;font-weight:700;line-height:1.25}
      .cerca-install-num{flex:0 0 34px;width:34px;height:34px;border-radius:50%;display:grid;place-items:center;background:#dceeed;color:#1e6b68;font-weight:800;font-size:18px}
      .cerca-install-ok{width:100%;border:0;border-radius:14px;padding:14px 16px;background:#2d7774;color:#fff;font-size:18px;font-weight:800;min-height:52px}
    `;
    document.head.appendChild(style);
  }

  function closeIosInstallGuide(){
    document.getElementById('cercaIosInstallGuide')?.remove();
  }

  function showIosInstallGuide(){
    if(standalone()){install.style.display='none';return}
    closeIosInstallGuide();
    ensureIosInstallStyles();

    const backdrop=document.createElement('div');
    backdrop.id='cercaIosInstallGuide';
    backdrop.className='cerca-install-backdrop';
    backdrop.setAttribute('role','dialog');
    backdrop.setAttribute('aria-modal','true');
    backdrop.setAttribute('aria-label','Instalar CERCA');
    backdrop.innerHTML=`
      <div class="cerca-install-card">
        <h2 class="cerca-install-title">Instalar CERCA</h2>
        <div class="cerca-install-steps">
          <div class="cerca-install-step"><span class="cerca-install-num">1</span><span>Abrí CERCA en <b>Safari</b></span></div>
          <div class="cerca-install-step"><span class="cerca-install-num">2</span><span>Tocá <b>Compartir ↑</b> → <b>Agregar a Inicio</b></span></div>
        </div>
        <button class="cerca-install-ok" type="button">Listo</button>
      </div>`;

    backdrop.querySelector('.cerca-install-ok').onclick=closeIosInstallGuide;
    backdrop.addEventListener('click',e=>{if(e.target===backdrop)closeIosInstallGuide()});
    document.body.appendChild(backdrop);
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
    if(ios){showIosInstallGuide();return}
    alert('Abrí el menú del navegador y elegí “Instalar app” o “Agregar a pantalla principal”.');
  };

  addEventListener('appinstalled',()=>install.style.display='none');
  addEventListener('pageshow',()=>{if(standalone())install.style.display='none'});
})();
