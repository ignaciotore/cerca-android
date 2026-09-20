function adminApp(){
  document.title='Tu Transfer · Administración';
  $('topPill').textContent='Panel privado';
  $('app').innerHTML=`
  <section id="login" class="card login">
    <h1>Panel privado</h1><p class="muted">Ingresá tu clave para ver reservas, valores, comisiones y beneficios.</p>
    <label>Clave</label><input id="token" type="password" class="field">
    <button id="loginBtn" class="btn dark block" style="margin-top:14px">Ingresar</button><div id="loginError" class="error"></div>
  </section>

  <section id="dash" class="dash">
    <div class="dashHead"><div><h1>Viajes, comisiones y beneficios</h1><p class="muted">Control de reservas y cupones personales asociados al teléfono de cada cliente.</p></div><div class="actions"><button id="exportBtn" class="btn light">Exportar CSV</button><button id="logoutBtn" class="btn light">Salir</button></div></div>
    <div class="kpis">
      <div class="card kpi"><small>Solicitudes</small><strong id="kReq">0</strong></div>
      <div class="card kpi"><small>Venta confirmada</small><strong id="kSales">$0</strong></div>
      <div class="card kpi"><small>Comisión proyectada</small><strong id="kProj" class="green">$0</strong></div>
      <div class="card kpi"><small>Beneficios activos</small><strong id="kCoupons">0</strong></div>
    </div>
    <div class="card filters"><select id="filter" class="select"><option value="">Todos los estados</option><option>Solicitud</option><option>Confirmado</option><option>Realizado</option><option>Cancelado</option></select><input id="search" class="field" placeholder="Buscar cliente o destino"><button id="refresh" class="btn light">Actualizar</button></div>

    <div class="desktop tablewrap"><table><thead><tr><th>Fecha</th><th>Cliente</th><th>Pas.</th><th>Recorrido</th><th>Km</th><th>Tarifa chofer</th><th>Precio lista</th><th>Descuento</th><th>Precio cliente</th><th>Chofer neto</th><th>Tu comisión</th><th>Estado</th><th>Beneficio generado</th><th></th></tr></thead><tbody id="rows"></tbody></table></div>
    <div id="cards" class="cards"></div>

    <div class="couponSection">
      <div class="sectionHead"><div><h2>Beneficios personales</h2><p class="muted">Cada beneficio es de un solo uso y queda vinculado al teléfono del cliente.</p></div></div>
      <div id="couponGrid" class="couponGrid"></div>
    </div>
  </section>`;
  initAdmin();
}

function initAdmin(){
  let trips=[],coupons=[];
  const loginErr=t=>{$('loginError').textContent=t;$('loginError').style.display='block'};
  const openDash=()=>{$('login').style.display='none';$('dash').style.display='block'};
  const openLogin=()=>{$('dash').style.display='none';$('login').style.display='block'};

  async function load(){
    try{
      const d=await call({action:'admin_list'},true);trips=d.trips||[];coupons=d.coupons||[];openDash();render();
    }catch{sessionStorage.removeItem(KEY);openLogin();loginErr('Clave incorrecta o sesión vencida.')}
  }

  $('loginBtn').onclick=()=>{const t=$('token').value.trim();if(!t)return loginErr('Ingresá la clave.');sessionStorage.setItem(KEY,t);$('loginError').style.display='none';load()};
  $('token').onkeydown=e=>{if(e.key==='Enter')$('loginBtn').click()};
  $('logoutBtn').onclick=()=>{sessionStorage.removeItem(KEY);openLogin()};
  $('refresh').onclick=load;$('filter').onchange=render;$('search').oninput=render;

  function filtered(){
    const s=$('filter').value,q=$('search').value.toLowerCase();
    return trips.filter(t=>(!s||t.status===s)&&(!q||[t.customer_name,t.customer_phone,t.origin,t.destination,t.coupon_code].some(x=>String(x||'').toLowerCase().includes(q))));
  }
  function couponForTrip(id){return coupons.find(c=>c.created_from_trip===id)}
  function kpis(){
    const c=trips.filter(t=>['Confirmado','Realizado'].includes(t.status));
    $('kReq').textContent=trips.filter(t=>t.status==='Solicitud').length;
    $('kSales').textContent=fmt(c.reduce((a,t)=>a+Number(t.customer_price||0),0));
    $('kProj').textContent=fmt(c.reduce((a,t)=>a+Number(t.commission_total||0),0));
    $('kCoupons').textContent=coupons.filter(x=>x.status==='Disponible').length;
  }
  function statusSelect(t){return `<select class="select status" data-id="${t.id}"><option ${t.status==='Solicitud'?'selected':''}>Solicitud</option><option ${t.status==='Confirmado'?'selected':''}>Confirmado</option><option ${t.status==='Realizado'?'selected':''}>Realizado</option><option ${t.status==='Cancelado'?'selected':''}>Cancelado</option></select>`}
  function couponLabel(t){
    const c=couponForTrip(t.id);
    if(!c) return '<span class="muted">—</span>';
    const exp=new Date(c.expires_at).toLocaleDateString('es-AR');
    return `<span class="couponBadge ${c.status.toLowerCase()}">${esc(c.code)}</span><br><span class="small muted">${esc(c.status)} · hasta ${exp}</span>`;
  }

  function render(){
    kpis();const a=filtered();
    $('rows').innerHTML=a.map(t=>`<tr>
      <td>${esc(t.travel_date||'')}<br><span class="muted">${esc((t.travel_time||'').slice(0,5))}</span></td>
      <td><strong>${esc(t.customer_name||'-')}</strong><br><span class="muted">${esc(t.customer_phone||'')}</span></td>
      <td>${t.passenger_count}</td>
      <td>${esc(t.origin)}<br><span class="muted">→ ${esc(t.destination)}</span></td>
      <td>${Number(t.distance_km).toFixed(1)}</td>
      <td class="money">${fmt(t.driver_base_fare)}</td>
      <td class="money">${fmt(t.customer_price_list??t.customer_price)}</td>
      <td class="money discountMoney">${Number(t.coupon_discount||0)>0?'-'+fmt(t.coupon_discount):'—'}</td>
      <td class="money">${fmt(t.customer_price)}</td>
      <td class="money">${fmt(t.driver_net)}</td>
      <td class="money green">${fmt(t.commission_total)}</td>
      <td>${statusSelect(t)}</td>
      <td>${couponLabel(t)}</td>
      <td><button class="btn light sm del" data-id="${t.id}">Eliminar</button></td>
    </tr>`).join('');

    $('cards').innerHTML=a.map(t=>{
      const c=couponForTrip(t.id);
      return `<div class="card tripCard"><div class="tripTop"><div><h3>${esc(t.customer_name||'Sin nombre')}</h3><div class="small muted">${esc(t.travel_date||'')} · ${esc((t.travel_time||'').slice(0,5))} · ${t.passenger_count} pas.</div></div><strong>${fmt(t.customer_price)}</strong></div><div class="route">${esc(t.origin)}<br>→ ${esc(t.destination)}<br><span class="muted">${Number(t.distance_km).toFixed(1)} km</span></div><div class="moneyGrid"><div class="moneyBox"><small>Tarifa chofer</small><strong>${fmt(t.driver_base_fare)}</strong></div><div class="moneyBox"><small>Tu comisión</small><strong class="green">${fmt(t.commission_total)}</strong></div><div class="moneyBox"><small>Precio lista</small><strong>${fmt(t.customer_price_list??t.customer_price)}</strong></div><div class="moneyBox"><small>Descuento aplicado</small><strong>${Number(t.coupon_discount||0)>0?'-'+fmt(t.coupon_discount):'—'}</strong></div></div>${c?`<div class="couponInline">🎁 Beneficio generado: <strong>${esc(c.code)}</strong> · ${esc(c.status)}</div>`:''}<div class="cardActions">${statusSelect(t)}${c&&c.status==='Disponible'?`<button class="btn light sm sendCoupon" data-coupon="${c.id}">Enviar cupón</button>`:''}<button class="btn light sm del" data-id="${t.id}">Eliminar</button></div></div>`;
    }).join('');

    renderCoupons();
    document.querySelectorAll('.status').forEach(s=>s.onchange=async e=>{
      const id=e.target.dataset.id,status=e.target.value;
      try{
        const r=await call({action:'admin_update_status',id,status},true);
        if(r.coupon&&status==='Realizado') alert(`Beneficio generado: ${r.coupon.code}\n10% OFF · tope ${fmt(r.coupon.max_discount)} · válido 30 días.`);
        await load();
      }catch(x){alert(x.message);await load()}
    });
    document.querySelectorAll('.del').forEach(b=>b.onclick=async e=>{
      const id=e.currentTarget.dataset.id;if(!confirm('¿Eliminar este viaje?'))return;
      try{await call({action:'admin_delete',id},true);await load()}catch(x){alert(x.message)}
    });
    document.querySelectorAll('.sendCoupon').forEach(b=>b.onclick=()=>sendCoupon(b.dataset.coupon));
  }

  function renderCoupons(){
    const tripMap=new Map(trips.map(t=>[t.id,t]));
    $('couponGrid').innerHTML=coupons.length?coupons.map(c=>{
      const t=tripMap.get(c.created_from_trip);const exp=new Date(c.expires_at).toLocaleDateString('es-AR');
      return `<div class="card couponCard"><div class="couponTop"><span class="couponBadge ${c.status.toLowerCase()}">${esc(c.code)}</span><span class="small muted">${esc(c.status)}</span></div><strong>${esc(t?.customer_name||'Cliente')}</strong><div class="small muted">${esc(c.customer_phone_display||t?.customer_phone||'')}</div><div class="couponTerms">10% OFF · máximo ${fmt(c.max_discount)}<br>Válido hasta ${exp}</div>${c.status==='Disponible'?`<button class="btn light sm sendCoupon" data-coupon="${c.id}">Enviar por WhatsApp</button>`:''}</div>`;
    }).join(''):'<div class="card emptyCoupon">Todavía no hay beneficios generados. Se crean automáticamente cuando marcás un viaje como <strong>Realizado</strong>.</div>';
  }

  function sendCoupon(id){
    const c=coupons.find(x=>x.id===id);if(!c)return;
    const t=trips.find(x=>x.id===c.created_from_trip);const phone=String(c.customer_phone_display||t?.customer_phone||'').replace(/\D/g,'').slice(-10);if(phone.length<8)return alert('Ese cliente no tiene un teléfono válido.');
    const exp=new Date(c.expires_at).toLocaleDateString('es-AR');
    const msg=`¡Gracias por viajar con Tu Transfer! 🎁\n\nTenés 10% OFF en tu próximo viaje (máximo ${fmt(c.max_discount)}), válido hasta el ${exp}.\n\nCódigo: ${c.code}\n\nEl beneficio es personal, está asociado a este número de teléfono y se aplica automáticamente cuando volvés a reservar por Tu Transfer.`;
    window.open(`https://wa.me/549${phone}?text=${encodeURIComponent(msg)}`,'_blank');
  }

  $('exportBtn').onclick=()=>{
    const h=['Fecha','Hora','Cliente','Telefono','Pasajeros','Origen','Destino','Km','Tarifa chofer','Precio lista','Descuento','Precio cliente','Chofer neto','Comision neta','Estado','Cupon usado'];
    const r=trips.map(t=>[t.travel_date,(t.travel_time||'').slice(0,5),t.customer_name,t.customer_phone,t.passenger_count,t.origin,t.destination,t.distance_km,t.driver_base_fare,t.customer_price_list??t.customer_price,t.coupon_discount||0,t.customer_price,t.driver_net,t.commission_total,t.status,t.coupon_code||'']);
    const csv=[h,...r].map(x=>x.map(v=>'"'+String(v??'').replaceAll('"','""')+'"').join(';')).join('\n');
    const blob=new Blob(['\ufeff'+csv],{type:'text/csv;charset=utf-8'}),a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download='tu-transfer-viajes.csv';a.click();
  };

  if(sessionStorage.getItem(KEY)) load();
}

(location.pathname.includes('/admin')?adminApp:publicApp)();
