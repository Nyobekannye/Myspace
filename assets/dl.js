;(function () {
  // Penangkap unduhan blob:/data: — berjalan di SETIAP frame sebelum skrip halaman.
  // Prinsip "jejak nol": tidak ada properti/Symbol baru di window, fungsi pengganti tidak punya .prototype
  // (method shorthand), toString() tetap "[native code]" via mask() dari spoof.js, dan click ditimpa di
  // prototype tempat aslinya berada (HTMLElement), bukan di HTMLAnchorElement (agar hasOwnProperty tetap sama).
  try {
    var NAME = '__DL_BRIDGE__';
    var MASK_KEY = Symbol.for('sesi.mask');
    var mask = window[MASK_KEY];
    try { delete window[MASK_KEY]; } catch (e) {}
    if (typeof mask !== 'function') mask = function (fn) { return fn; };

    var br = window[NAME];
    if (!br) return;
    try { delete window[NAME]; } catch (e) {}
    if (NAME in window) { try { Object.defineProperty(window, NAME, { value: undefined, enumerable: false, configurable: true }); } catch (e) {} }

    var CHUNK = 512 * 1024;                 // 512 KB base64 per panggilan jembatan
    var blobs = new Map();                  // blob:url → Blob (tetap dipegang meski halaman revoke)
    var pendingRevoke = new Map();

    // ---- registry blob: catat setiap createObjectURL, tunda revoke 90 dtk ----
    var oCreate = URL.createObjectURL, oRevoke = URL.revokeObjectURL;
    var urlFns = {
      createObjectURL(o) {
        var u = oCreate.apply(URL, arguments);
        try { if (o instanceof Blob) blobs.set(u, o); } catch (e) {}
        return u;
      },
      revokeObjectURL(u) {
        if (blobs.has(u)) {
          if (!pendingRevoke.has(u)) pendingRevoke.set(u, setTimeout(function () { blobs.delete(u); pendingRevoke.delete(u); try { oRevoke.call(URL, u); } catch (e) {} }, 90000));
          return;
        }
        return oRevoke.apply(URL, arguments);
      }
    };
    URL.createObjectURL = mask(urlFns.createObjectURL, oCreate);
    URL.revokeObjectURL = mask(urlFns.revokeObjectURL, oRevoke);

    function nameFrom(url, hint) {
      if (hint) return String(hint);
      try { var p = new URL(url, location.href).pathname.split('/').pop(); if (p && /\.[a-z0-9]{1,5}$/i.test(p)) return decodeURIComponent(p); } catch (e) {}
      return '';
    }

    // ---- kirim Blob ke Java dalam potongan ----
    function send(blob, name, mime) {
      var id = String(Date.now()) + Math.random().toString(36).slice(2);
      mime = blob.type || mime || '';
      br.begin(id, name || '', mime, blob.size);
      var off = 0, raw = Math.floor(CHUNK * 3 / 4);
      function next() {
        if (off >= blob.size) { br.end(id); return; }
        var part = blob.slice(off, Math.min(blob.size, off + raw));
        var fr = new FileReader();
        fr.onload = function () { var s = fr.result; br.chunk(id, s.substring(s.indexOf(',') + 1)); off += raw; next(); };
        fr.onerror = function () { br.fail('Gagal membaca blob'); };
        fr.readAsDataURL(part);
      }
      next();
    }

    // Ambil blob dari url: registry → fetch di frame ini → gambar yang memakai url tsb (canvas)
    function grab(url, name, mime) {
      var b = blobs.get(url);
      if (b) { send(b, name, mime); return; }
      fetch(url).then(function (r) { return r.blob(); }).then(function (bl) { send(bl, name, mime); })
        .catch(function () {
          var el = document.querySelector('img[src="' + url + '"]');
          if (el && el.naturalWidth) {
            try {
              var c = document.createElement('canvas'); c.width = el.naturalWidth; c.height = el.naturalHeight;
              c.getContext('2d').drawImage(el, 0, 0);
              c.toBlob(function (bl) { if (bl) send(bl, name || 'gambar.png', 'image/png'); else br.fail('Canvas kosong'); }, 'image/png');
              return;
            } catch (e) {}
          }
          br.fail('Blob sudah dilepas halaman');
        });
    }
    // Pemicu dari Java (DownloadListener blob:) lewat CustomEvent — tanpa properti global apa pun.
    document.addEventListener(NAME, function (ev) {
      var d = ev.detail || {};
      if (typeof d.u === 'string') { ev.preventDefault(); grab(d.u, d.n || '', d.m || ''); }
    }, true);

    // ---- hook 1: klik <a download> / <a href="blob:"> (termasuk a.click() tanpa append ke DOM) ----
    function handleAnchor(a, ev) {
      try {
        var href = a.href || '';
        var isBlob = href.indexOf('blob:') === 0;
        var hasDl = a.hasAttribute && a.hasAttribute('download');
        if (!isBlob && !(hasDl && href.indexOf('data:') === 0)) return false;
        if (ev) { ev.preventDefault(); ev.stopImmediatePropagation && ev.stopImmediatePropagation(); }
        if (isBlob) grab(href, nameFrom(href, a.getAttribute('download')), a.type);
        else br.dataUrl(href, a.getAttribute('download') || '');
        return true;
      } catch (e) { return false; }
    }
    document.addEventListener('click', function (ev) {
      var a = ev.target && ev.target.closest ? ev.target.closest('a[href]') : null;
      if (a) handleAnchor(a, ev);
    }, true);
    // click() asli berada di HTMLElement.prototype — timpa di sana (bukan di HTMLAnchorElement.prototype)
    var owner = HTMLAnchorElement.prototype;
    while (owner && !Object.prototype.hasOwnProperty.call(owner, 'click')) owner = Object.getPrototypeOf(owner);
    if (owner) {
      var oClick = owner.click;
      var elFns = {
        click() {
          if (this instanceof HTMLAnchorElement && !this.isConnected && handleAnchor(this, null)) return;
          return oClick.apply(this, arguments);   // anchor yang terpasang di DOM akan lewat listener capture di atas
        }
      };
      owner.click = mask(elFns.click, oClick);
    }

    // ---- hook 2: window.open(blob:) ----
    var oOpen = window.open;
    var winFns = {
      open() {
        var u = arguments[0];
        try { if (typeof u === 'string' && u.indexOf('blob:') === 0) { grab(u, nameFrom(u), ''); return null; } } catch (e) {}
        return oOpen.apply(window, arguments);
      }
    };
    window.open = mask(winFns.open, oOpen);
  } catch (e) {}
})();
