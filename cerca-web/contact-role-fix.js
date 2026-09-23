(()=>{
  // Regla CERCA: puede haber un solo contacto de llamada, pero ese mismo
  // contacto también puede recibir SMS. Los roles son independientes.
  syncedRoleFor=function(x){
    const all=networkCollections().syncedContacts||[];
    const k=contactKey(x);
    const px=phoneKey(first(x,'phone_e164','phone','target_phone','contact_phone'));
    const matches=all.filter(c=>contactKey(c)===k||(px&&phoneKey(first(c,'phone_e164','phone'))===px));
    const direct=(x.sms_enabled!==undefined||x.call_enabled!==undefined)?[x]:[];
    const pool=matches.length?matches:direct;
    const call=designatedCall();
    const callPhone=phoneKey(first(call,'phone_e164','phone','target_phone','contact_phone'));
    const isCall=!!call&&((px&&callPhone&&px===callPhone)||contactKey(call)===k);
    const hasSms=pool.some(c=>c.sms_enabled===true);
    return{
      call:isCall,
      sms:hasSms,
      cerca:(x.__linkIds||[]).length>0||pool.some(c=>c.has_cerca===true)||!!(x.owner_user_id||x.target_user_id)
    };
  };

  roleBadges=function(x){
    const r=syncedRoleFor(x),out=[];
    if(r.call)out.push('<span class="contactRole call">📞 Contacto de llamada</span>');
    if(r.sms)out.push('<span class="contactRole sms">✉️ Contacto SMS</span>');
    if(r.cerca)out.push('<span class="contactRole cerca">🔔 Alerta CERCA</span>');
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
    await networkCall('sync_contacts',{contacts:clean});
    await loadNetwork();renderSide();renderBelow();
  };

  addContactModal=function(){
    const w=modal('<h2>Agregar contacto</h2><p>Podés usar la misma persona para llamada y SMS. Solo una persona puede ser el contacto de llamada.</p><button id="pickPhoneContact" class="btn light block">Elegir de mis contactos</button><label>Nombre</label><input id="contactName" class="field" autocomplete="name"><label>Teléfono</label><input id="contactPhone" class="field" inputmode="tel" autocomplete="tel" placeholder="+54 9 11..."><label>Función</label><select id="contactRole" class="select"><option value="sms">Contacto SMS</option><option value="call_sms">Llamada + SMS</option></select><button id="saveContactBtn" class="btn primary block" style="margin-top:16px">Guardar contacto</button>');
    const pick=$('pickPhoneContact');
    if(!navigator.contacts?.select)pick.style.display='none';
    else pick.onclick=async()=>{try{const r=await navigator.contacts.select(['name','tel'],{multiple:false});const p=r?.[0];if(p){$('contactName').value=p.name?.[0]||'';$('contactPhone').value=p.tel?.[0]||''}}catch{}};
    $('saveContactBtn').onclick=async()=>{
      const name=$('contactName').value.trim(),phone=$('contactPhone').value.trim(),role=$('contactRole').value;
      if(name.length<2)return toast('Ingresá el nombre del contacto.','error');
      if(phoneKey(phone).length<8)return toast('Ingresá un teléfono válido.','error');
      const list=syncedPayload();
      const key=phoneKey(phone);
      let target=list.find(x=>phoneKey(x.phone)===key);
      const wantsCall=role==='call_sms';
      if(target){
        if(wantsCall)list.forEach(x=>{x.call_enabled=phoneKey(x.phone)===key});
        target.name=name||target.name;
        target.sms_enabled=true;
        if(wantsCall)target.call_enabled=true;
      }else{
        if(list.length>=4)return toast('Ya tenés 4 contactos configurados.','error');
        if(wantsCall)list.forEach(x=>{x.call_enabled=false});
        list.push({slot:list.length+1,name,phone,sms_enabled:true,call_enabled:wantsCall,medical_access:'never'});
      }
      try{await saveSyncedContacts(list);w.remove();toast(target?'Funciones del contacto actualizadas.':'Contacto agregado.')}catch(e){toast(e.message,'error')}
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
      if(is)x.sms_enabled=true;
    });
    if(!found)return toast('No encontré ese contacto.','error');
    try{await saveSyncedContacts(list);toast('Ahora recibe llamada y SMS.')}catch(e){toast(e.message,'error')}
  };

  linkCard=function(x){
    const linkIds=(x.__linkIds||[]).filter(Boolean);
    const linkId=linkIds.join(',');
    const n=x.display_name||x.full_name||x.name||x.email||'Contacto CERCA';
    const rel=x.relationship||x.relation||'Red CERCA';
    const med=x.medical_access||'never';
    const phone=first(x,'phone_e164','phone','target_phone','contact_phone');
    const roles=syncedRoleFor(x);
    const roleAction=phone&&!roles.call?'<button class="btn light sm" data-make-call="'+esc(phone)+'">Usar para llamada + SMS</button>':'';
    const smsStatus=roles.sms
      ?'<div class="infoNote" style="margin-top:10px;padding:10px 12px"><strong>SMS de emergencia</strong><br>📍 Ubicación: incluida siempre.<br>🩺 Información útil: '+(med==='never'?'no incluida.':'incluida.')+'</div>'
      :'';
    const showMedical=(roles.sms||roles.cerca)&&phone;
    const medLabel=roles.sms&&roles.cerca?'Información útil (SMS + CERCA)':roles.sms?'Información útil en el SMS':'Información útil en CERCA';
    const medicalBlock=showMedical
      ?'<label style="margin-top:10px">'+medLabel+'</label><select class="select" data-medical="'+esc(linkId)+'" data-phone="'+esc(phone)+'"><option value="never" '+(med==='never'?'selected':'')+'>No compartir</option><option value="emergency" '+(med==='emergency'?'selected':'')+'>Incluir durante una emergencia</option><option value="always" '+(med==='always'?'selected':'')+'>Siempre autorizada</option></select>'
      :'';
    const removeAction=linkId
      ?'<button class="btn light sm" data-remove="'+esc(linkId)+'">Quitar de la red</button>'
      :(phone?'<button class="btn light sm" data-remove-contact="'+esc(phone)+'">Quitar contacto</button>':'');
    return'<div class="item"><div class="itemTop"><div><h3>'+esc(n)+'</h3><p>'+esc(rel)+(phone?' · '+esc(phone):'')+'</p>'+roleBadges(x)+'</div>'+(roles.cerca?'<span class="badge">'+(med==='never'?'Info privada':med==='emergency'?'Solo en emergencia':'Info autorizada')+'</span>':'')+'</div>'+smsStatus+medicalBlock+'<div class="itemActions">'+roleAction+removeAction+'</div></div>';
  };

  setMedicalAccess=async function(ids,value,phone){
    const linkList=String(ids||'').split(',').filter(Boolean);
    try{
      for(const id of linkList)await networkCall('set_medical_access',{link_id:id,medical_access:value});
      if(phone){
        const contacts=syncedPayload();
        const key=phoneKey(phone);
        const target=contacts.find(x=>phoneKey(x.phone)===key);
        if(target){
          target.medical_access=value;
          await saveSyncedContacts(contacts);
        }else{
          await loadNetwork();renderSide();renderBelow();
        }
      }else{
        await loadNetwork();renderSide();renderBelow();
      }
      toast(value==='never'?'Información útil desactivada para este contacto.':'Información útil incluida para este contacto.');
    }catch(e){toast(e.message,'error');refreshAll()}
  };

  const originalRenderNetwork=renderNetwork;
  renderNetwork=function(el){
    originalRenderNetwork(el);
    const p=el.querySelector('.sectionHead p');
    if(p)p.textContent='Configurá 1 contacto de llamada. Esa misma persona y los demás también pueden recibir SMS. Todos los SMS de emergencia incluyen tu ubicación.';
    el.querySelectorAll('[data-medical]').forEach(s=>s.onchange=()=>setMedicalAccess(s.dataset.medical,s.value,s.dataset.phone));
  };
})();