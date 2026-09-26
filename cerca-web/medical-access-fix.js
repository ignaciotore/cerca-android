(()=>{
  if(typeof linkCard!=='function'||typeof networkCall!=='function')return;

  setMedicalAccess=async function(phone,value){
    try{
      await networkCall('set_medical_access',{phone,medical_access:value});
      toast('Permiso actualizado.');
      await loadNetwork();
      renderSide();
      renderBelow();
    }catch(e){
      toast(e?.message||String(e),'error');
      refreshAll();
    }
  };

  linkCard=function(x){
    const linkIds=(x.__linkIds||[]).filter(Boolean);
    const linkId=linkIds.join(',');
    const n=x.display_name||x.full_name||x.name||x.email||'Contacto CERCA';
    const rel=x.relationship||x.relation||'Red CERCA';
    const med=x.medical_access||'never';
    const phone=first(x,'phone_e164','phone','target_phone','contact_phone');
    const roles=syncedRoleFor(x);
    const roleAction=phone&&!roles.call?'<button class="btn light sm" data-make-call="'+esc(phone)+'">Usar para llamada</button>':'';
    const medicalBlock=roles.cerca&&phone
      ?'<label style="margin-top:10px">Información útil</label><select class="select" data-medical="'+esc(phone)+'"><option value="never" '+(med==='never'?'selected':'')+'>No compartir</option><option value="emergency" '+(med==='emergency'?'selected':'')+'>Solo durante una emergencia</option><option value="always" '+(med==='always'?'selected':'')+'>Siempre autorizada</option></select>'
      :'';
    const removeAction=linkId
      ?'<button class="btn light sm" data-remove="'+esc(linkId)+'">Quitar de la red</button>'
      :(phone?'<button class="btn light sm" data-remove-contact="'+esc(phone)+'">Quitar contacto</button>':'');
    return '<div class="item"><div class="itemTop"><div><h3>'+esc(n)+'</h3><p>'+esc(rel)+(phone?' · '+esc(phone):'')+'</p>'+roleBadges(x)+'</div>'+(roles.cerca?'<span class="badge">'+(med==='never'?'Info privada':med==='emergency'?'Solo en emergencia':'Info autorizada')+'</span>':'')+'</div>'+medicalBlock+'<div class="itemActions">'+roleAction+removeAction+'</div></div>';
  };
})();
