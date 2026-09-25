(()=>{
  if(typeof loadNetwork!=='function')return;
  const originalLoadNetwork=loadNetwork;

  function emergencyOwnerId(e){
    if(!e||typeof e!=='object')return '';
    return String(
      e.owner_user_id??
      e.user_id??
      e.creator_user_id??
      e.created_by??
      e.owner_id??
      ''
    );
  }

  function belongsToCurrentUser(e){
    if(!e)return false;
    const uid=String(S?.user?.id||'');
    const owner=emergencyOwnerId(e);
    // Si el backend no trae owner, mantenemos compatibilidad con el contrato anterior.
    // Cuando sí lo trae, la emergencia propia debe pertenecer al usuario logueado.
    return !owner||!uid||owner===uid;
  }

  loadNetwork=async function(...args){
    const before=S?.activeEmergency||null;
    const result=await originalLoadNetwork.apply(this,args);
    const candidate=S?.network?.active_emergency||null;

    if(candidate&&!belongsToCurrentUser(candidate)){
      S.activeEmergency=null;
      // Una alerta recibida de Mi Red no debe convertir el Inicio del contacto
      // en "SOS ACTIVO". Refrescamos solo la vista propia si estaba mostrando eso.
      if(before&&S?.tab==='home'&&typeof renderSide==='function'&&document.getElementById('sideContent')){
        queueMicrotask(()=>{try{renderSide()}catch{}});
      }
    }else{
      S.activeEmergency=candidate;
    }
    return result;
  };
})();
