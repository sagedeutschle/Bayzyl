// Package the separately built commercial distribution. Key is provided only at release time.
import {createCipheriv,randomBytes} from 'node:crypto';
import {readFileSync,writeFileSync,readdirSync} from 'node:fs';
import {gzipSync} from 'node:zlib';
import path from 'node:path';
const [source,out]=process.argv.slice(2),key=Buffer.from(process.env.SHOREPACT_SITE_KEY||'','base64');
if(!source||!out||key.length!==32)throw Error('Provide the WebBundle directory, output path, and release key.');
const index=[],chunks=[];let offset=0;
function walk(base){for(const f of readdirSync(path.join(source,base),{withFileTypes:true}).sort((a,b)=>a.name.localeCompare(b.name,'en'))){
 const p=path.posix.join(base,f.name);if(f.isDirectory()){walk(p);continue}
 if(!f.isFile()||f.name.startsWith('.')||!/^[-a-zA-Z0-9_./]+$/.test(p))throw Error('Unexpected release path: '+p);
 const raw=readFileSync(path.join(source,p)),gz=gzipSync(raw),z=gz.length+64<raw.length,data=z?gz:raw;
 index.push([p,offset,data.length,z]);chunks.push(data);offset+=data.length;
}}
walk('');if(!index.some(([p])=>p==='game/shorepact.wasm'))throw Error('Game missing.');
const listing=Buffer.from(JSON.stringify(index)),size=Buffer.alloc(4);size.writeUInt32BE(listing.length);
const iv=randomBytes(12),cipher=createCipheriv('aes-256-gcm',key,iv);
const encrypted=Buffer.concat([cipher.update(Buffer.concat([size,listing,...chunks])),cipher.final()]);
writeFileSync(out,Buffer.concat([iv,cipher.getAuthTag(),encrypted]));console.log('Packaged '+index.length+' distribution files.');
