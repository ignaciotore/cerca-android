(()=>{
  // Regla CERCA: puede haber un solo contacto de llamada, pero ese mismo
  // contacto también puede recibir SMS. Los roles no son excluyentes.
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
      // Convertir en contacto de llamada no le quita SMS.
      if(is)x.sms_enabled=true;
    });
    if(!found)return toast('No encontré ese contacto.','error');
    try{await saveSyncedContacts(list);toast('Ahora recibe llamada y SMS.')}catch(e){toast(e.message,'error')}
  };

  const originalRenderNetwork=renderNetwork;
  renderNetwork=function(el){
    originalRenderNetwork(el);
    const p=el.querySelector('.sectionHead p');
    if(p)p.textContent='Configurá 1 contacto de llamada. Ese mismo contacto y los demás también pueden recibir SMS. Si usan CERCA, además reciben la alerta dentro de la app.';
  };
})();