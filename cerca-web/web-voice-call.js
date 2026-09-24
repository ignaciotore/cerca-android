(()=>{
  const ua=navigator.userAgent||'';
  const ios=/iPhone|iPad|iPod/i.test(ua)||(navigator.platform==='MacIntel'&&navigator.maxTouchPoints>1);
  const nativeAndroid=/CERCA-Native-Android/i.test(ua)||window.__CERCA_NATIVE_ANDROID__===true||!!window.CercaNative;
  if(!ios||nativeAndroid)return;

  const SDK_URL='https://cdn.jsdelivr.net/npm/@twilio/voice-sdk@2.18.5/dist/twilio.min.js';
  const CALL_KEY='cerca_web_voice_called_v1:';
  let sdkPromise=null,device=null,activeCall=null;

  function emergencyId(){
    try{return String(S?.activeEmergency?.id||S?.activeEmergency?.emergency_id||'')}catch{return''}
  }
  function alreadyCalled(id){try{return localStorage.getItem(CALL_KEY+id)==='1'}catch{return false}}
  function markCalled(id){try{localStorage.setItem(CALL_KEY+id,'1')}catch{}}
  function clearCalled(id){try{localStorage.removeItem(CALL_KEY+id)}catch{}}

  function loadSdk(){
    if(window.Twilio?.Device)return Promise.resolve(window.Twilio);
    if(sdkPromise)return sdkPromise;
    sdkPromise=new Promise((resolve,reject)=>{
      const s=document.createElement('script');s.src=SDK_URL;s.async=true;s.crossOrigin='anonymous';
      s.onload=()=>window.Twilio?.Device?resolve(window.Twilio):reject(new Error('No pude iniciar el módulo de llamada.'));
      s.onerror=()=>reject(new Error('No pude cargar el módulo de llamada.'));
      document.head.appendChild(s);
    });
    return sdkPromise;
  }

  async function credentials(id){
    if(typeof request!=='function')throw new Error('La sesión de CERCA no está disponible.');
    return await request('/functions/v1/cerca-voice?action=token',{method:'POST',body:{emergency_id:id}});
  }

  async function startVoiceCall(id){
    if(!id||alreadyCalled(id)||activeCall)return;
    let marked=false;
    try{
      const [Twilio,c]=await Promise.all([loadSdk(),credentials(id)]);
      if(!c?.access_token||!c?.to||!c?.ticket)throw new Error('No pude preparar la llamada.');
      if(device){try{device.destroy()}catch{} device=null}
      device=new Twilio.Device(c.access_token,{closeProtection:false});
      const call=await device.connect({params:{To:String(c.to),Ticket:String(c.ticket),EmergencyId:id}});
      activeCall=call;markCalled(id);marked=true;
      const cleanup=()=>{if(activeCall===call)activeCall=null};
      call.on('disconnect',cleanup);call.on('cancel',cleanup);call.on('reject',cleanup);
      call.on('error',err=>{cleanup();try{console.warn('CERCA voice',err)}catch{}});
    }catch(e){
      if(marked)clearCalled(id);
      const msg=e?.message||String(e||'No pude iniciar la llamada automática.');
      if(/ya fue iniciada/i.test(msg)){markCalled(id);return}
      if(typeof toast==='function')toast(msg,'error');
    }
  }

  function installWrapper(){
    if(typeof startEmergency!=='function'||startEmergency.__cercaIosVoice)return false;
    const previous=startEmergency;
    const wrapped=async function(silent){
      const result=await previous(silent);
      try{
        if(!silent&&typeof S!=='undefined'&&S.activeEmergency){
          const id=emergencyId();
          if(id)await startVoiceCall(id);
        }
      }catch{}
      return result;
    };
    wrapped.__cercaIosVoice=true;
    startEmergency=wrapped;
    return true;
  }

  let n=0;const t=setInterval(()=>{n++;if(installWrapper()||n>120)clearInterval(t)},250);

  // Preparamos el permiso de micrófono cuando la persona elige el contacto de llamada.
  // Es una autorización única de iOS; no agrega un segundo botón durante cada SOS.
  document.addEventListener('click',e=>{
    const el=e.target?.closest?.('[data-make-call]');if(!el||!navigator.mediaDevices?.getUserMedia)return;
    navigator.mediaDevices.getUserMedia({audio:true}).then(s=>s.getTracks().forEach(t=>t.stop())).catch(()=>{});
  },true);
})();