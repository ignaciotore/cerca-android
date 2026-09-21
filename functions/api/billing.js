const SUPABASE_URL = "https://yduoxeqgxolkzvjexlqk.supabase.co";
const SUPABASE_KEY = "sb_publishable_XtjPnBnjESZwcUnUUAPybg_Y6LqivaD";
const PRICE = 25000;
const REASON = "CERCA Premium";
const BACK_URL = "https://cerca-seguridad.pages.dev/?subscription=return";

const json = (data,status=200)=>new Response(JSON.stringify(data),{
  status,
  headers:{
    "content-type":"application/json; charset=utf-8",
    "cache-control":"no-store",
    "access-control-allow-origin":"https://cerca-seguridad.pages.dev",
    "access-control-allow-headers":"authorization,content-type",
    "access-control-allow-methods":"GET,POST,OPTIONS"
  }
});

async function supabaseUser(request){
  const auth=request.headers.get("authorization")||"";
  if(!auth.toLowerCase().startsWith("bearer "))throw new Error("UNAUTHORIZED");
  const r=await fetch(SUPABASE_URL+"/auth/v1/user",{headers:{apikey:SUPABASE_KEY,authorization:auth}});
  if(!r.ok)throw new Error("UNAUTHORIZED");
  const user=await r.json();
  return {user,auth};
}

async function profileTrial(auth,userId){
  const r=await fetch(SUPABASE_URL+"/rest/v1/profiles?user_id=eq."+encodeURIComponent(userId)+"&select=trial_ends_at",{
    headers:{apikey:SUPABASE_KEY,authorization:auth}
  });
  if(!r.ok)return null;
  const a=await r.json();
  return Array.isArray(a)&&a[0]?.trial_ends_at?a[0].trial_ends_at:null;
}

async function mp(env,path,options={}){
  const token=env.MERCADOPAGO_ACCESS_TOKEN;
  if(!token)throw new Error("MP_NOT_CONFIGURED");
  const r=await fetch("https://api.mercadopago.com"+path,{
    ...options,
    headers:{
      authorization:"Bearer "+token,
      "content-type":"application/json",
      ...(options.headers||{})
    }
  });
  const text=await r.text();
  let data={};try{data=JSON.parse(text||"{}")}catch{data={raw:text}}
  if(!r.ok)throw new Error(data.message||data.error||("Mercado Pago HTTP "+r.status));
  return data;
}

async function findSubscription(env,email,userId){
  const data=await mp(env,"/preapproval/search?payer_email="+encodeURIComponent(email));
  const list=Array.isArray(data?.results)?data.results:[];
  return list
    .filter(x=>String(x.external_reference||"")===String(userId)&&String(x.reason||"").toLowerCase().includes("cerca"))
    .sort((a,b)=>Date.parse(b.date_created||0)-Date.parse(a.date_created||0))[0]||null;
}

function normalize(sub){
  if(!sub)return null;
  return {
    id:sub.id||null,
    status:sub.status||"unknown",
    active:sub.status==="authorized",
    next_payment_date:sub.next_payment_date||null,
    init_point:sub.init_point||null,
    reason:sub.reason||REASON
  };
}

export async function onRequestOptions(){return json({ok:true})}

export async function onRequestGet({request,env}){
  try{
    const {user,auth}=await supabaseUser(request);
    const trial_ends_at=await profileTrial(auth,user.id);
    if(!env.MERCADOPAGO_ACCESS_TOKEN){
      return json({configured:false,price:PRICE,currency:"ARS",trial_ends_at,subscription:null});
    }
    const sub=await findSubscription(env,user.email,user.id);
    return json({configured:true,price:PRICE,currency:"ARS",trial_ends_at,subscription:normalize(sub)});
  }catch(e){
    if(e.message==="UNAUTHORIZED")return json({error:"unauthorized"},401);
    return json({error:e.message||"billing_error"},500);
  }
}

export async function onRequestPost({request,env}){
  try{
    const {user,auth}=await supabaseUser(request);
    if(!env.MERCADOPAGO_ACCESS_TOKEN)return json({configured:false,error:"mercadopago_not_configured"},503);
    const existing=await findSubscription(env,user.email,user.id);
    if(existing?.status==="authorized")return json({ok:true,already_active:true,subscription:normalize(existing)});
    if(existing?.status==="pending"&&existing?.init_point)return json({ok:true,checkout_url:existing.init_point,subscription:normalize(existing)});
    const trial_ends_at=await profileTrial(auth,user.id);
    const auto_recurring={frequency:1,frequency_type:"months",transaction_amount:PRICE,currency_id:"ARS"};
    const trialMs=trial_ends_at?Date.parse(trial_ends_at):0;
    if(Number.isFinite(trialMs)&&trialMs>Date.now()+5*60*1000)auto_recurring.start_date=new Date(trialMs).toISOString();
    const body={
      reason:REASON,
      external_reference:String(user.id),
      payer_email:user.email,
      back_url:BACK_URL,
      auto_recurring
    };
    const sub=await mp(env,"/preapproval",{method:"POST",body:JSON.stringify(body)});
    return json({ok:true,checkout_url:sub.init_point||null,subscription:normalize(sub)});
  }catch(e){
    if(e.message==="UNAUTHORIZED")return json({error:"unauthorized"},401);
    if(e.message==="MP_NOT_CONFIGURED")return json({configured:false,error:"mercadopago_not_configured"},503);
    return json({error:e.message||"billing_error"},500);
  }
}
