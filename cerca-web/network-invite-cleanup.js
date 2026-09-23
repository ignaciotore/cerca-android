(()=>{
  // Mi Red ya permite agregar contactos directamente y la app se comparte desde el botón global.
  // Se elimina el flujo redundante de invitaciones/códigos de esta pantalla.
  const baseRenderNetwork=renderNetwork;
  renderNetwork=function(el){
    baseRenderNetwork(el);

    const inviteBtn=el.querySelector('#inviteCercaBtn');
    if(inviteBtn)inviteBtn.remove();

    const acceptBtn=el.querySelector('#acceptInvite');
    if(acceptBtn){
      const parent=acceptBtn.parentElement;
      acceptBtn.remove();
      if(parent&&!parent.children.length)parent.remove();
    }

    const actions=el.querySelector('.networkAddActions');
    if(actions){
      actions.style.gridTemplateColumns='1fr';
      actions.style.display='grid';
    }

    el.querySelectorAll('.infoNote').forEach(note=>{
      const txt=(note.textContent||'').trim();
      if(txt.includes('debe instalar CERCA y aceptar tu invitación')){
        note.innerHTML='<strong>Notificación pendiente</strong><br>Esta persona debe instalar CERCA para recibir alertas. Podés compartirle la app desde el botón Compartir de la pantalla principal.';
      }
    });
  };
})();
