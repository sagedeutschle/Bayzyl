import assert from 'node:assert/strict';
import {createHmac} from 'node:crypto';

// Preserve the browser's HTTPS document origin without requiring CI DNS or a test
// certificate. Forward its actual form body and origin unchanged to the real gate.
export async function checkPlaytestForm(browser,localBase,key){
 const ctx=await browser.newContext(),page=await ctx.newPage();
 const origin='https://prismet.xyz',path='/shorepact/play/';
 let sentOrigin,code,location,cookie;
 try{
  await page.route(origin+'/**',async route=>{
   const req=route.request();
   if(req.method()==='GET'&&code===303)return route.fulfill({status:200,body:'admitted'});
   const hdr=await req.allHeaders();
   const response=await fetch(localBase+path,{method:req.method(),body:req.postData()??undefined,headers:req.method()==='POST'?{'origin':hdr.origin??'','content-type':hdr['content-type']}:undefined,redirect:'manual'});
   if(req.method()==='POST'){sentOrigin=hdr.origin;code=response.status;cookie=response.headers.get('set-cookie');location=response.headers.get('location')}
   return route.fulfill({status:response.status,headers:Object.fromEntries(response.headers),body:await response.text()});
  });
  await page.goto(origin+path);await page.getByLabel('Playtest pass').fill(createHmac('sha256',key).update('invitation:v1').digest('base64url').slice(0,22));
  await Promise.all([page.waitForResponse(r=>r.request().method()==='POST'),page.getByRole('button',{name:'Enter the playtest'}).click()]);
  assert.equal(sentOrigin,origin);assert.equal(code,303);assert.equal(location,'./');assert.match(cookie,/HttpOnly/);
  console.log('Shorepact: actual browser form submits its origin and gets an admission cookie.');
 }finally{await ctx.close()}
}
