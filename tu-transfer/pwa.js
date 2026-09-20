(() => {
  const shareBtn=document.getElementById('shareAppBtn');
  if(shareBtn){
    if(location.pathname.includes('/admin')) shareBtn.style.display='none';
    else shareBtn.addEventListener('click',async()=>{
      const url=location.href.split('#')[0].split('?')[0];
      const data={title:'Tu Transfer',text:'Cotizá y reservá tu traslado con Tu Transfer.',url};
      try{
        if(navigator.share){await navigator.share(data);return;}
        if(navigator.clipboard&&window.isSecureContext){await navigator.clipboard.writeText(url);}
        else{
          const ta=document.createElement('textarea'); ta.value=url; ta.style.position='fixed'; ta.style.opacity='0';
          document.body.appendChild(ta); ta.focus(); ta.select(); document.execCommand('copy'); ta.remove();
        }
        alert('Link de Tu Transfer copiado. Ya podés compartirlo.');
      }catch(e){
        if(e&&e.name==='AbortError')return;
        prompt('Copiá este link para compartir Tu Transfer:',url);
      }
    });
  }
  const installBtn=document.getElementById('installAppBtn');
  if(!installBtn)return;
  if(location.pathname.includes('/admin')){installBtn.style.display='none';return;}
  const standalone=window.matchMedia('(display-mode: standalone)').matches||window.navigator.standalone===true;
  if(standalone){installBtn.style.display='none';return;}
  let deferredPrompt=null;
  const isiOS=/iphone|ipad|ipod/i.test(navigator.userAgent);
  if('serviceWorker' in navigator) window.addEventListener('load',()=>navigator.serviceWorker.register('./sw.js',{scope:'./'}).catch(()=>{}));
  window.addEventListener('beforeinstallprompt',e=>{e.preventDefault();deferredPrompt=e;installBtn.style.display='inline-flex';installBtn.textContent='Instalar app';});
  if(isiOS){installBtn.style.display='inline-flex';installBtn.textContent='Agregar a inicio';}
  installBtn.addEventListener('click',async()=>{
    if(deferredPrompt){deferredPrompt.prompt();await deferredPrompt.userChoice.catch(()=>{});deferredPrompt=null;installBtn.style.display='none';return;}
    if(isiOS){alert('En iPhone/iPad: tocá Compartir en Safari y elegí “Agregar a pantalla de inicio”.');return;}
    alert('Abrí el menú del navegador y elegí “Instalar app” o “Agregar a pantalla principal”.');
  });
})();