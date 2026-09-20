const API='https://bhgmcqpjrlbsuaptfois.supabase.co/functions/v1/tu-transfer-api';
const WA='5491135947019';
const KEY='tu_transfer_admin_token';
const $=id=>document.getElementById(id);
const fmt=n=>new Intl.NumberFormat('es-AR',{style:'currency',currency:'ARS',maximumFractionDigits:0}).format(Number(n||0));
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'}[c]));

async function call(body,admin=false){
  const h={'Content-Type':'application/json'};
  if(admin) h['x-admin-token']=sessionStorage.getItem(KEY)||'';
  const r=await fetch(API,{method:'POST',headers:h,body:JSON.stringify(body)});
  const d=await r.json().catch(()=>({}));
  if(!r.ok) throw new Error(d.error||'No pude completar la operación.');
  return d;
}

function publicApp(){
  document.title='Tu Transfer · Cotizá tu viaje';
  $('topPill').textContent='Traslados privados';
  $('app').innerHTML=`
  <div class="hero">
    <section class="card formCard">
      <h1>Tu traslado, cotizado al instante.</h1>
      <p class="lead">Elegí origen y destino, indicá cuándo viajás y calculamos automáticamente el valor estimado del traslado.</p>

      <div class="auto" data-t="origin">
        <label>Desde dónde salís</label>
        <span class="inputIcon">📍</span>
        <input id="origin" class="field withIcon" autocomplete="off" placeholder="Ej: Nordelta, Tigre">
        <div id="originSuggestions" class="suggestions"></div>
        <div class="locationRow"><button id="locationBtn" class="btn light sm">◎ Usar mi ubicación</button></div>
        <div class="helper">Empezá a escribir y seleccioná la dirección correcta.</div>
      </div>

      <div class="auto" data-t="destination">
        <label>A dónde vas</label>
        <span class="inputIcon">🏁</span>
        <input id="destination" class="field withIcon" autocomplete="off" placeholder="Ej: Aeropuerto Internacional de Ezeiza">
        <div id="destinationSuggestions" class="suggestions"></div>
      </div>

      <div class="grid3">
        <div><label>Fecha</label><input id="date" type="date" class="field"></div>
        <div><label>Hora</label><input id="time" type="time" class="field"></div>
        <div><label>Pasajeros</label><select id="passengers" class="select">${[1,2,3,4,5,6,7,8].map(x=>`<option>${x}</option>`).join('')}</select></div>
      </div>

      <div class="grid2">
        <div><label>Nombre</label><input id="name" class="field" autocomplete="name" placeholder="Tu nombre"></div>
        <div>
          <label>Teléfono</label>
          <input id="phone" class="field" inputmode="tel" autocomplete="tel" placeholder="11 5555 5555">
          <div id="benefitBox" class="benefitBox">🎁 <strong>Beneficio Tu Transfer:</strong> al completar este viaje recibís 10% OFF para el próximo.</div>
        </div>
      </div>

      <button id="quoteBtn" class="btn primary block" style="margin-top:18px">Calcular viaje</button>
      <div id="error" class="error"></div>
      <div id="success" class="success"></div>

      <div id="result" class="result">
        <div class="resultTop">
          <div>
            <div class="caption">VALOR ESTIMADO</div>
            <div id="oldPrice" class="oldPrice"></div>
            <div id="price" class="price">$0</div>
            <div id="discountLine" class="discountLine"></div>
          </div>
          <button id="confirmBtn" class="btn primary">Confirmar por WhatsApp</button>
        </div>
        <div class="meta"><span id="km" class="chip"></span><span id="mins" class="chip"></span><span id="pax" class="chip"></span></div>
        <div id="summary" class="summary"></div>
        <div id="loyaltyBanner" class="loyaltyBanner">🎁 <strong>Tu próximo viaje puede tener 10% OFF.</strong><span>Cuando este viaje quede realizado, el beneficio se activa por 30 días y queda asociado a tu teléfono. Máximo de descuento: $10.000.</span></div>
        <div class="small muted" style="margin-top:10px">Al confirmar registramos la solicitud y abrimos WhatsApp con los datos listos para enviar.</div>
      </div>
    </section>

    <aside class="card heroCopy">
      <div>
        <h2>Viajar también es parte del plan.</h2>
        <p>Coordiná tu traslado de forma simple, con una cotización clara y beneficios personales por volver a reservar con Tu Transfer.</p>
        <div class="trust"><span><i></i>Zona Norte y CABA</span><span><i></i>Aeropuertos</span><span><i></i>Beneficios por volver</span></div>
      </div>
      <div class="art"><div class="road"></div><div class="dot a"></div><div class="dot b"></div><div class="float a"><small>Tu próximo viaje</small><strong>Puede tener 10% OFF</strong></div><div class="float b"><small>Tu Transfer</small><strong>Simple · directo · cómodo</strong></div></div>
    </aside>
  </div>`;
  initPublic();
}

function initPublic(){
  const sel={origin:null,destination:null};
  const timers={};
  let quote=null;
  $('date').value=new Date().toISOString().slice(0,10);

  const err=t=>{$('success').style.display='none';$('error').textContent=t;$('error').style.display='block'};
  const clear=()=>{$('error').style.display=$('success').style.display='none'};

  async function search(q){
    const r=await fetch('https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1&countrycodes=ar&accept-language=es&limit=6&q='+encodeURIComponent(q));
    if(!r.ok) throw Error('No pude buscar direcciones.');
    return r.json();
  }
  async function reverse(lat,lon){
    const r=await fetch(`https://nominatim.openstreetmap.org/reverse?format=jsonv2&accept-language=es&lat=${lat}&lon=${lon}`);
    return r.json();
  }
  function hide(t){$(t+'Suggestions').style.display='none'}
  function render(t,list){
    const b=$(t+'Suggestions');
    if(!list.length){b.innerHTML='<div class="small muted" style="padding:10px">No encontré opciones.</div>';b.style.display='block';return}
    b.innerHTML=list.map((x,i)=>`<div class="suggestion" data-t="${t}" data-i="${i}"><strong>${esc(x.display_name.split(',').slice(0,2).join(','))}</strong><span>${esc(x.display_name.split(',').slice(2).join(','))}</span></div>`).join('');
    b.dataset.items=JSON.stringify(list.map(x=>({lat:+x.lat,lon:+x.lon,label:x.display_name})));
    b.style.display='block';
  }

  ['origin','destination'].forEach(t=>{
    $(t).oninput=()=>{
      sel[t]=null;clearTimeout(timers[t]);const q=$(t).value.trim();
      if(q.length<3) return hide(t);
      timers[t]=setTimeout(async()=>{try{render(t,await search(q))}catch{render(t,[])}},300);
    };
    $(t).onblur=()=>setTimeout(()=>hide(t),180);
  });

  document.onclick=e=>{
    const row=e.target.closest('.suggestion');
    if(row){
      const t=row.dataset.t,a=JSON.parse($(t+'Suggestions').dataset.items||'[]'),x=a[+row.dataset.i];
      if(x){sel[t]=x;$(t).value=x.label;hide(t)}
      return;
    }
    if(!e.target.closest('.auto')){hide('origin');hide('destination')}
  };

  $('locationBtn').onclick=()=>{
    clear();
    if(!navigator.geolocation) return err('Este navegador no permite usar tu ubicación.');
    const b=$('locationBtn');b.disabled=true;b.textContent='Buscando ubicación…';
    navigator.geolocation.getCurrentPosition(async p=>{
      const lat=p.coords.latitude,lon=p.coords.longitude;
      let label=`Mi ubicación actual (${lat.toFixed(5)}, ${lon.toFixed(5)})`;
      sel.origin={lat,lon,label};$('origin').value=label;
      try{const x=await reverse(lat,lon);label=x.display_name||label;sel.origin={lat,lon,label};$('origin').value=label}catch{}
      b.disabled=false;b.textContent='◎ Usar mi ubicación';
    },e=>{
      b.disabled=false;b.textContent='◎ Usar mi ubicación';
      err(e.code===1?'Necesito permiso de ubicación. Habilitalo en el navegador e intentá nuevamente.':'No pude detectar tu ubicación.');
    },{enableHighAccuracy:true,timeout:12000,maximumAge:60000});
  };

  async function resolve(t){
    if(sel[t]) return sel[t];
    const q=$(t).value.trim();
    if(!q) throw Error('Completá origen y destino.');
    const a=await search(q);
    if(!a.length) throw Error('No encontré una de las direcciones. Elegí una opción de la lista.');
    return {lat:+a[0].lat,lon:+a[0].lon,label:a[0].display_name};
  }

  let benefitTimer;
  async function checkBenefit(){
    clearTimeout(benefitTimer);
    benefitTimer=setTimeout(async()=>{
      const phone=$('phone').value.trim();
      if(phone.replace(/\D/g,'').length<8){
        $('benefitBox').innerHTML='🎁 <strong>Beneficio Tu Transfer:</strong> al completar este viaje recibís 10% OFF para el próximo.';
        $('benefitBox').classList.remove('active');
        return;
      }
      try{
        const r=await call({action:'check_benefit',customer_phone:phone});
        if(r.available){
          const exp=new Date(r.benefit.expires_at).toLocaleDateString('es-AR');
          $('benefitBox').innerHTML=`🎁 <strong>¡Tenés ${r.benefit.discount_percent}% OFF disponible!</strong> Se aplicará automáticamente. Válido hasta ${exp}, tope ${fmt(r.benefit.max_discount)}.`;
          $('benefitBox').classList.add('active');
        }else{
          $('benefitBox').innerHTML='🎁 <strong>Beneficio Tu Transfer:</strong> al completar este viaje recibís 10% OFF para el próximo.';
          $('benefitBox').classList.remove('active');
        }
      }catch{}
    },350);
  }
  $('phone').addEventListener('input',checkBenefit);
  $('phone').addEventListener('blur',checkBenefit);

  $('quoteBtn').onclick=async()=>{
    clear();$('result').style.display='none';
    const b=$('quoteBtn');b.disabled=true;b.textContent='Calculando…';
    try{
      const [o,d]=await Promise.all([resolve('origin'),resolve('destination')]);sel.origin=o;sel.destination=d;
      const q=await call({action:'quote',origin:o,destination:d,customer_phone:$('phone').value.trim()});
      quote={origin:o,destination:d,km:q.km,minutes:q.minutes,price:q.customer_price,listPrice:q.list_price,discount:q.discount,benefit:q.benefit};
      $('price').textContent=fmt(q.customer_price);
      $('oldPrice').textContent=q.discount?fmt(q.list_price):'';
      $('discountLine').innerHTML=q.discount?`🎁 Beneficio personal aplicado: <strong>-${fmt(q.discount)}</strong>`:'';
      $('km').textContent=`${q.km.toFixed(1)} km`;
      $('mins').textContent=`${q.minutes} min aprox.`;
      const p=+$('passengers').value;$('pax').textContent=`${p} ${p===1?'pasajero':'pasajeros'}`;
      $('summary').innerHTML=`<strong>Origen:</strong> ${esc(o.label)}<br><strong>Destino:</strong> ${esc(d.label)}`;
      $('loyaltyBanner').innerHTML=q.discount
        ? `✅ <strong>Usaste tu beneficio personal en este viaje.</strong><span>Cuando completes este traslado volverás a generar 10% OFF para tu próximo viaje, válido por 30 días.</span>`
        : `🎁 <strong>Tu próximo viaje puede tener 10% OFF.</strong><span>Cuando este viaje quede realizado, el beneficio se activa por 30 días y queda asociado a tu teléfono. Máximo de descuento: $10.000.</span>`;
      $('result').style.display='block';
    }catch(e){err(e.message)}finally{b.disabled=false;b.textContent='Calcular viaje'}
  };

  $('confirmBtn').onclick=async()=>{
    if(!quote) return;
    clear();
    if(!$('date').value||!$('time').value) return err('Elegí fecha y hora antes de confirmar.');
    if(!$('name').value.trim()||!$('phone').value.trim()) return err('Completá nombre y teléfono antes de confirmar.');
    const b=$('confirmBtn');b.disabled=true;b.textContent='Registrando…';
    const pop=window.open('about:blank','_blank');
    try{
      const p={action:'create_trip',origin:quote.origin,destination:quote.destination,travel_date:$('date').value,travel_time:$('time').value,passenger_count:+$('passengers').value,customer_name:$('name').value.trim(),customer_phone:$('phone').value.trim()};
      const r=await call(p),t=r.trip,code=String(t.id).split('-')[0].toUpperCase();
      let priceText=`Valor estimado: ${fmt(t.customer_price)}`;
      if(Number(t.coupon_discount)>0) priceText=`Valor sin beneficio: ${fmt(t.customer_price_list)}\nBeneficio Tu Transfer: -${fmt(t.coupon_discount)}\nValor final: ${fmt(t.customer_price)}`;
      const msg=`Hola, quiero confirmar este viaje de Tu Transfer.\n\nReserva: ${code}\nNombre: ${p.customer_name}\nTeléfono: ${p.customer_phone}\nPasajeros: ${p.passenger_count}\nFecha: ${p.travel_date}\nHora: ${p.travel_time}\n\nOrigen: ${quote.origin.label}\nDestino: ${quote.destination.label}\nDistancia: ${Number(t.distance_km).toFixed(1)} km\n${priceText}\n\nQuedo a la espera de la confirmación. Gracias.`;
      const url=`https://wa.me/${WA}?text=${encodeURIComponent(msg)}`;
      $('success').textContent='Solicitud registrada. Abriendo WhatsApp…';$('success').style.display='block';
      if(pop) pop.location.href=url; else location.href=url;
    }catch(e){if(pop)pop.close();err(e.message)}finally{b.disabled=false;b.textContent='Confirmar por WhatsApp'}
  };
}
