const express=require('express');
const path=require('path');
const app=express();
const PORT=process.env.PORT||3000;
const BACKEND='https://yduoxeqgxolkzvjexlqk.supabase.co';
const BACKEND_KEY='sb_publishable_XtjPnBnjESZwcUnUUAPybg_Y6LqivaD';

app.use('/api',async(req,res)=>{
  try{
    const target=BACKEND+req.originalUrl.replace(/^\/api/,'');
    const headers={apikey:BACKEND_KEY,Accept:req.headers.accept||'application/json'};
    if(req.headers.authorization)headers.Authorization=req.headers.authorization;
    if(req.headers['content-type'])headers['Content-Type']=req.headers['content-type'];
    if(req.headers.prefer)headers.Prefer=req.headers.prefer;
    const init={method:req.method,headers};
    if(!['GET','HEAD'].includes(req.method)){
      const chunks=[];for await(const chunk of req)chunks.push(chunk);
      init.body=Buffer.concat(chunks);
    }
    const r=await fetch(target,init);
    res.status(r.status);
    r.headers.forEach((v,k)=>{if(!['content-encoding','content-length','transfer-encoding','connection'].includes(k.toLowerCase()))res.setHeader(k,v)});
    const buf=Buffer.from(await r.arrayBuffer());
    res.send(buf);
  }catch(e){res.status(502).json({error:'No pudimos conectar con CERCA.'})}
});
const root=path.join(__dirname,'cerca-web');
app.use(express.static(root,{setHeaders(res,file){if(file.endsWith('sw.js'))res.setHeader('Cache-Control','no-cache, no-store, must-revalidate')}}));
app.get('*',(req,res)=>res.sendFile(path.join(root,'index.html')));
app.listen(PORT,()=>console.log('CERCA Web escuchando en '+PORT));