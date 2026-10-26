// Deterministically adapt accepted Phase 4E reference metadata and images for native use.
// Run with the workspace Node runtime and sharp installed. Original assets are never changed.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),vm=require('node:vm'),cp=require('node:child_process');
const sharp=require('sharp');
const root=path.resolve(__dirname,'../..'), original='android-app/android/app/src/main/assets/www/';
const gitBytes=p=>cp.execFileSync('git',['show','HEAD:'+p],{cwd:root,maxBuffer:20*1024*1024});
const html=gitBytes(original+'index.html').toString('utf8');
function literal(marker){const start=html.indexOf(marker)+marker.length; if(start<marker.length)throw Error(marker);let depth=0,quote='',escape=false;for(let i=start;i<html.length;i++){const c=html[i];if(quote){if(escape)escape=false;else if(c==='\\')escape=true;else if(c===quote)quote='';}else if(c==='"'||c==="'")quote=c;else if(c==='{')depth++;else if(c==='}'&&--depth===0)return vm.runInNewContext('('+html.slice(start,i+1)+')',Object.create(null),{timeout:1000});}throw Error('Unclosed literal');}
const lib=literal('const LIB='),assets=literal('const ASSETS='),fit=literal('const FIT='),remap=literal('const REMAP='),guides=literal('const GUIDE=');
Object.assign(assets,literal('Object.assign(ASSETS,'));
const out=path.join(root,'native-android/app/src/main/assets/native-library');fs.mkdirSync(out,{recursive:true});
(async()=>{const entries=[];for(const [id,item] of Object.entries(lib)){const a=assets[remap[id]||item.asset];let image=null;if(a){const source=gitBytes(original+a.file);if(crypto.createHash('sha256').update(source).digest('hex')!==a.sha256)throw Error('Image provenance mismatch '+a.file);const name=id+'.png';await sharp(source,{density:120}).resize({width:640,height:420,fit:'inside',withoutEnlargement:true}).flatten({background:'#ffffff'}).png().toFile(path.join(out,name));image={...a,nativeFile:'native-library/'+name,changes:a.changes+'; rasterized for native display without changing content',fit:fit[id]||['context','Context photograph, not a technique demonstration']};}entries.push({...item,image});}
for(const [id,g]of Object.entries(guides))entries.push({id,n:g.n,t:g.d,eq:g.eq.join(', ')||'None',level:g.lv,pattern:g.p,purpose:g.why,tech:g.cues,reg:g.easy,prog:g.ready,mistake:g.safe,guide:true});
fs.writeFileSync(path.join(out,'catalog.json'),JSON.stringify({schema:1,sourceHtmlSha256:crypto.createHash('sha256').update(Buffer.from(html)).digest('hex'),entries},null,2)+'\n');console.log(entries.length+' entries; original asset bytes preserved');})();

