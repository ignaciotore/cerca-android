(()=>{
  // CERCA usa notificaciones como canal principal en todas las plataformas.
  // Puede haber un único contacto de llamada.
  syncedRoleFor=function(x){
    const all=networkCollections().syncedContacts||[];
    const k=contactKey(x);
    const px=phoneKey(first(x,'phone_e164','phone','target_phone','contact_phone'));
    const matches=all.filter(c=>contactKey(c)===k||(px&&phoneKey(first(c,'phone_e164','phone'))===px));
    const pool=matches.length?matches:[];
    const call=designatedCall();
    const callPhone=phoneKey(first(call,'phone_e164','phone','target_phone','contact_phone'));
    const isCall=!!call&&((px&&callPhone&&px===callPhone)||contactKey(call)===k);
    const hasCerca=(x.__linkIds||[]).length>0||pool.some(c=>c.has_cerca===true)||!!(x.owner_user_id||x.target_user_id);
    return{call:isCall,cerca:hasCerca};
  };

  roleBadges=function(x){
    const r=syncedRoleFor(x),out=[];
    if(r.call)out.push('<span class="contactRole call">📞 Contacto de llamada</span>');
    if(r.cerca)out.push('<span class="contactRole cerca">🔔 Notificación CERCA</span>');
    else if(!r.call)out.push('<span class="contactRole">Notificación pendiente · debe usar CERCA</span>');
    return '<div class="contactRoles">'+out.join('')+'</div>';
  };

  saveSyncedContacts=async function(list){
    const clean=mergeNetworkContacts(list.map(x=>({...x,phone_e164:x.phone}))).slice(0,4).map((x,i)=>({
      slot:i+1,
      name:x.name||x.display_name||'Contacto',
      phone:first(x,'phone','phone_e164')||'',
      sms_enabled:x.sms_enabled===true,
      call_enabled:x.call_enabled===true,
      medical_access:x.medical_access||'never'
    }));
    if(clean.filter(x=>x.call_enabled).length>1){
      let seen=false;
      clean.forEach(x=>{
        if(x.call_enabled&&!seen)seen=true;
        else if(x.call_enabled)x.call_enabled=false;
      });
    }
    const result=await networkCall('sync_contacts',{contacts:clean});
    await loadNetwork();renderSide();renderBelow();
    return result;
  };

  addContactModal=function(){
    const w=modal('<h2>Agregar contacto</h2><button id="pickPhoneContact" class="btn light block">Elegir de mis contactos</button><label>Nombre</label><input id="contactName" class="field" autocomplete="name"><label>Teléfono</label><input id="contactPhone" class="field" inputmode="tel" autocomplete="tel" placeholder="+54 9 11..."><label>Función</label><select id="contactRole" class="select"><option value="notify">Contacto CERCA · notificaciones</option><option value="call">Solo llamada</option><option value="call_notify">Llamada + CERCA</option></select><button id="saveContactBtn" class="btn primary block" style="margin-top:16px">Guardar contacto</button>');
    const pick=$('pickPhoneContact');
    if(!navigator.contacts?.select)pick.style.display='none';
    else pick.onclick=async()=>{try{const r=await navigator.contacts.select(['name','tel'],{multiple:false});const p=r?.[0];if(p){$('contactName').value=p.name?.[0]||'';$('contactPhone').value=p.tel?.[0]||''}}catch{}};
    $('saveContactBtn').onclick=async()=>{
      const name=$('contactName').value.trim(),phone=$('contactPhone').value.trim(),role=$('contactRole').value;
      if(name.length<2)return toast('Ingresá el nombre del contacto.','error');
      if(phoneKey(phone).length<8)return toast('Ingresá un teléfono válido.','error');
      const previous=syncedPayload().map(x=>({...x}));
      const list=previous.map(x=>({...x}));
      const key=phoneKey(phone);
      let target=list.find(x=>phoneKey(x.phone)===key);
      const wantsCall=role==='call'||role==='call_notify';
      const wantsNotify=role==='notify'||role==='call_notify';
      if(target){
        if(wantsCall)list.forEach(x=>{x.call_enabled=phoneKey(x.phone)===key});
        target.name=name||target.name;
        target.call_enabled=wantsCall;
        target.sms_enabled=wantsNotify;
      }else{
        if(list.length>=4)return toast('Ya tenés 4 contactos configurados.','error');
        if(wantsCall)list.forEach(x=>{x.call_enabled=false});
        list.push({slot:list.length+1,name,phone,sms_enabled:wantsNotify,call_enabled:wantsCall,medical_access:'never'});
      }
      try{
        const state=await saveSyncedContacts(list);
        const rows=arr(state,'contacts','outgoing');
        const saved=rows.find(x=>phoneKey(first(x,'phone_e164','phone'))===key);
        const hasCerca=saved?.has_cerca===true;
        if(wantsNotify&&!hasCerca){
          await saveSyncedContacts(previous);
          w.remove();
          alert('Este contacto todavía no tiene una cuenta CERCA y no puede recibir alertas desde la app. Podés compartirle CERCA desde el botón Compartir y agregarlo cuando cree su cuenta.');
          return;
        }
        w.remove();
        if(role==='call')toast('Contacto de llamada guardado.');
        else if(role==='call_notify')toast('Contacto guardado para llamada y alertas CERCA.');
        else toast(target?'Contacto CERCA actualizado.':'Contacto CERCA agregado.');
      }catch(e){toast(e.message,'error')}
    };
  };

  makeCallContact=async function(phone){
    const list=syncedPayload();
    const key=phoneKey(phone);
    let found=false;
    list.forEach(x=>{
      const is=phoneKey(x.phone)===key;
      if(is)found=true;
      x.call_enabled=is;
    });
    if(!found)return toast('No encontré ese contacto.','error');
    try{await saveSyncedContacts(list);toast('Contacto de llamada actualizado.')}catch(e){toast(e.message,'error')}
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
    const notifyStatus=roles.cerca
      ?'<div class="infoNote" style="margin-top:10px;padding:10px 12px"><strong>Notificación de emergencia</strong><br>📍 Ubicación: disponible desde la alerta.<br>🩺 Información útil: '+(med==='never'?'no autorizada.':'autorizada.')+'</div>'
      :(roles.call?'':'<div class="infoNote" style="margin-top:10px;padding:10px 12px"><strong>Notificación pendiente</strong><br>Esta persona debe tener una cuenta CERCA para recibir alertas.</div>');
    const medicalBlock=roles.cerca&&linkId
      ?'<label style="margin-top:10px">Información útil en la alerta</label><select class="select" data-medical="'+esc(linkId)+'"><option value="never" '+(med==='never'?'selected':'')+'>No compartir</option><option value="emergency" '+(med==='emergency'?'selected':'')+'>Mostrar durante una emergencia</option><option value="always" '+(med==='always'?'selected':'')+'>Siempre autorizada</option></select>'
      :'';
    const removeAction=linkId
      ?'<button class="btn light sm" data-remove="'+esc(linkId)+'">Quitar de la red</button>'
      :(phone?'<button class="btn light sm" data-remove-contact="'+esc(phone)+'">Quitar contacto</button>':'');
    return'<div class="item"><div class="itemTop"><div><h3>'+esc(n)+'</h3><p>'+esc(rel)+(phone?' · '+esc(phone):'')+'</p>'+roleBadges(x)+'</div>'+(roles.cerca?'<span class="badge">'+(med==='never'?'Info privada':med==='emergency'?'Solo en emergencia':'Info autorizada')+'</span>':'')+'</div>'+notifyStatus+medicalBlock+'<div class="itemActions">'+roleAction+removeAction+'</div></div>';
  };

  setMedicalAccess=async function(ids,value){
    const linkList=String(ids||'').split(',').filter(Boolean);
    try{
      for(const id of linkList)await networkCall('set_medical_access',{link_id:id,medical_access:value});
      await loadNetwork();renderSide();renderBelow();
      toast(value==='never'?'Información útil desactivada para este contacto.':'Información útil disponible para este contacto durante la alerta.');
    }catch(e){toast(e.message,'error');refreshAll()}
  };

  const originalRenderNetwork=renderNetwork;
  renderNetwork=function(el){
    originalRenderNetwork(el);
    const p=el.querySelector('.sectionHead p');
    if(p)p.textContent='Tu Red CERCA recibe notificaciones con ubicación e información útil autorizada. Además podés configurar 1 contacto de llamada.';
    el.querySelectorAll('[data-medical]').forEach(s=>s.onchange=()=>setMedicalAccess(s.dataset.medical,s.value));
  };
})();