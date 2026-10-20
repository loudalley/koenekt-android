/* Runs only in an authenticated workspace page. No JavaScript/native bridge. */
(() => {
  if (!window.__koenektMobile) {
    window.__koenektMobile = true;
    const blobs = new Map();
    const create = URL.createObjectURL.bind(URL);
    const revoke = URL.revokeObjectURL.bind(URL);
    URL.createObjectURL = blob => { const url = create(blob); blobs.set(url, blob); return url; };
    URL.revokeObjectURL = url => { setTimeout(() => { blobs.delete(url); revoke(url); }, 60000); };
    window.__koenektExportBlob = async (url, name) => {
      try {
        const blob = blobs.get(url) || await (await fetch(url)).blob();
        if (blob.size > 20 * 1024 * 1024) throw new Error('Select a smaller report range (maximum 20 MB).');
        const reader = new FileReader();
        reader.onload = () => {
          let filename = name || 'koenekt-report';
          const extensions = {'application/pdf':'.pdf', 'text/csv':'.csv', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet':'.xlsx'};
          const suffix = extensions[blob.type.split(';')[0]];
          if (suffix && !filename.toLowerCase().endsWith(suffix)) filename += suffix;
          window.__koenektDownload = {name:filename, mime:blob.type || 'application/octet-stream', data:String(reader.result).split(',')[1]};
          location.href = 'koenekt-export://' + window.__koenektNonce;
        };
        reader.onerror = () => { window.__koenektDownload = {error:'The export could not be read.'}; location.href = 'koenekt-export://' + window.__koenektNonce; };
        reader.readAsDataURL(blob);
      } catch (e) {
        window.__koenektDownload = {error:e.message};
        location.href = 'koenekt-export://' + window.__koenektNonce;
      }
    };
    const click = HTMLAnchorElement.prototype.click;
    HTMLAnchorElement.prototype.click = function () {
      if (this.href.startsWith('blob:')) { window.__koenektExportBlob(this.href, this.download); return; }
      return click.call(this);
    };
    document.addEventListener('click', event => {
      const logout = event.target.closest && event.target.closest('#logout');
      if (logout) {
        event.preventDefault(); event.stopImmediatePropagation();
        location.href = 'koenekt-signout://' + window.__koenektNonce;
        return;
      }
      const a = event.target.closest && event.target.closest('a[href^="blob:"]');
      if (a) { event.preventDefault(); window.__koenektExportBlob(a.href, a.download); }
    }, true);
    const style = document.createElement('style');
    // The app supplies its own title bar and navigation, so the website's header is hidden inside the app.
    style.textContent = 'header.kp-header{display:none!important}body{background:#f6f8f5}@media(max-width:640px){input,select,textarea{font-size:16px!important}button,.b{min-height:42px}.modal-bg{padding:12px!important}.modal{max-width:100%!important}main{padding:12px!important}table{display:block;overflow-x:auto;max-width:100%}.waiting{grid-template-columns:minmax(0,1fr)!important}}';
    document.head.appendChild(style);
    window.__koenektSelectTab = () => {
      if (!location.pathname.startsWith('/licences')) return;
      const tab = location.hash.slice(1);
      if (['machines','licences','customers','updates','activity'].includes(tab)) {
        const button = document.querySelector('#tabs button[data-tab="' + tab + '"]');
        if (button) button.click();
      }
    };
    window.addEventListener('hashchange', window.__koenektSelectTab);
  }
  // The existing admin page has tabs, but does not read URL fragments itself.
  window.__koenektSelectTab();
})();
