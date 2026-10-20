// Exercise the actual shipped export script, including synchronous URL revocation.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync(require('node:path').join(__dirname, '../app/src/main/assets/mobile.js'), 'utf8');
let serial = 0, tabClicks = 0;
const nativeBlobs = new Map(), timers = [], listeners = {};
class Anchor {
  constructor(href, download) { this.href = href; this.download = download; }
  click() { throw new Error('Blob reached unhandled Android download path'); }
}
class Reader {
  readAsDataURL(blob) {
    blob.arrayBuffer().then(bytes => {
      this.result = 'data:' + blob.type + ';base64,' + Buffer.from(bytes).toString('base64');
      this.onload();
    });
  }
}
const context = {
  Map, Error, String, HTMLAnchorElement:Anchor, FileReader:Reader,
  addEventListener:(name, fn)=>listeners['window:'+name]=fn,
  URL:{
    createObjectURL(blob) { const id = 'blob:https://people.koenekt.co.za/' + (++serial); nativeBlobs.set(id, blob); return id; },
    revokeObjectURL(id) { nativeBlobs.delete(id); }
  },
  fetch:async url => { if (!nativeBlobs.has(url)) throw new Error('Revoked too early'); return {blob:async()=>nativeBlobs.get(url)}; },
  setTimeout:fn=>timers.push(fn),
  location:{pathname:'/licences/', hash:'#updates', href:''},
  document:{
    createElement:()=>({}), head:{appendChild(){}}, addEventListener:(name, fn)=>listeners[name]=fn,
    querySelector:query => query.includes('updates') ? {click:()=>tabClicks++} : null
  }
};
context.window = context;
context.__koenektNonce = 'document-token';
vm.createContext(context);
async function tick() { for (let i=0; i<8; i++) await new Promise(resolve=>setImmediate(resolve)); }
(async()=>{
  vm.runInContext(source, context);
  assert.equal(tabClicks, 1, 'Reporter shortcut selects the actual existing tab');
  const blob = new Blob(['staff,name\n1,Synthetic Person'], {type:'text/csv'});
  const url = context.URL.createObjectURL(blob);
  new Anchor(url, 'attendance.csv').click();
  context.URL.revokeObjectURL(url);
  await tick();
  assert.equal(context.location.href, 'koenekt-export://document-token');
  assert.equal(context.__koenektDownload.name, 'attendance.csv');
  assert.equal(Buffer.from(context.__koenektDownload.data, 'base64').toString(), 'staff,name\n1,Synthetic Person');
  timers.splice(0).forEach(fn=>fn());
  assert.equal(nativeBlobs.size, 0, 'Revoked blob is eventually released');
  await context.__koenektExportBlob(context.URL.createObjectURL(new Blob(['pdf'], {type:'application/pdf'})), 'payroll');
  await tick();
  assert.equal(context.__koenektDownload.name, 'payroll.pdf');
  await context.__koenektExportBlob(context.URL.createObjectURL(new Blob([new Uint8Array(20*1024*1024+1)])), 'large');
  assert.match(context.__koenektDownload.error, /smaller report range/);
  vm.runInContext(source, context);
  assert.equal(tabClicks, 2, 'Repeated injection does not lose tab navigation');
  listeners['window:hashchange']();
  assert.equal(tabClicks, 3, 'Native shortcuts still work for a same-document fragment change');
  let stopped = false;
  listeners.click({target:{closest:selector=>selector === '#logout' ? {} : null},preventDefault(){},stopImmediatePropagation(){stopped=true;}});
  assert.equal(stopped, true, 'Website sign-out cannot invoke the global licence logout');
  assert.equal(context.location.href, 'koenekt-signout://document-token');
  console.log('Export checks passed: blob content, immediate revoke, cleanup, MIME extension, size bound, tab selection and reinjection.');
})().catch(e=>{console.error(e);process.exitCode=1;});
