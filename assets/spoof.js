(function () {
  var G = typeof window !== 'undefined' ? window : self;
  var IS_WORKER = typeof window === 'undefined';
  if (G.__sesiSpoofed) return;
  try { Object.defineProperty(G, '__sesiSpoofed', { value: true, enumerable: false }); } catch (e) {}
  var P = __PROFILE__;
  var SRC = '__SELF_SOURCE__'; // kode ini sendiri (untuk disuntik ke Worker)

  function def(obj, key, val) {
    // definisikan di prototype (seperti properti native), bukan di instance
    var NavProto = G.Navigator ? Navigator.prototype : (G.WorkerNavigator ? WorkerNavigator.prototype : null);
    var target = (obj === navigator && NavProto) ? NavProto : obj;
    var g = function () { return val; };
    try { Object.defineProperty(target, key, { get: g, set: undefined, configurable: true, enumerable: true }); } catch (e) {}
    return g;
  }
  // Sembunyikan jejak "fungsi native yang dibungkus"
  var nativeToString = Function.prototype.toString;
  var wrapped = new WeakMap();
  function mask(fn, orig) { wrapped.set(fn, orig); return fn; }
  try {
    Function.prototype.toString = mask(function () {
      var o = wrapped.get(this); return nativeToString.call(o || this);
    }, nativeToString);
  } catch (e) {}
  // Bagikan mask() ke dl.js (dijalankan tepat setelah skrip ini, sinkron, sebelum JS halaman) — dl.js langsung menghapusnya.
  if (!IS_WORKER) try { Object.defineProperty(G, Symbol.for('sesi.mask'), { value: mask, configurable: true, enumerable: false }); } catch (e) {}

  /* ---------- navigator ---------- */
  def(navigator, 'webdriver', false);
  if (P.lang) {
    var langs = P.langs.split(',').map(function (x) { return x.split(';')[0].trim(); }).filter(Boolean);
    def(navigator, 'language', P.lang);
    def(navigator, 'languages', Object.freeze(langs));
  }
  if (P.desktop) {
    def(navigator, 'maxTouchPoints', 0);
    def(navigator, 'platform', 'Linux x86_64');
    if (!IS_WORKER) {
      // desktop tidak punya touch: hilangkan jejak TouchEvent
      ['ontouchstart', 'ontouchend', 'ontouchmove', 'ontouchcancel'].forEach(function (k) {
        try { delete window[k]; } catch (e) {}
        try { delete Window.prototype[k]; } catch (e) {}
        try { delete document[k]; } catch (e) {}
        try { delete Document.prototype[k]; } catch (e) {}
        try { delete HTMLElement.prototype[k]; } catch (e) {}
        try { delete Element.prototype[k]; } catch (e) {}
      });
      ['TouchEvent', 'Touch', 'TouchList'].forEach(function (k) { try { delete window[k]; } catch (e) {} });
    }
    if (IS_WORKER) { /* tidak ada DOM */ } else {
    // Mode desktop: abaikan meta viewport mobile, paksa layout selebar 1024px (seperti Chrome "Situs desktop")
    var fixVp = function () {
      try {
        var m = document.querySelector('meta[name="viewport"]');
        if (!m) { m = document.createElement('meta'); m.name = 'viewport'; (document.head || document.documentElement).appendChild(m); }
        m.setAttribute('content', 'width=1024, initial-scale=' + (Math.min(1, screen.width / 1024)).toFixed(3));
      } catch (e) {}
    };
    if (document.readyState !== 'loading') fixVp(); else document.addEventListener('DOMContentLoaded', fixVp, { once: true });
    }
  }
  def(navigator, 'hardwareConcurrency', P.cores);
  def(navigator, 'deviceMemory', P.memGb);

  // userAgentData: hanya di-override kalau WebView tidak mendukung setUserAgentMetadata native
  if (P.jsUAD) {
    var brands = [
      { brand: 'Chromium', version: P.major },
      { brand: 'Google Chrome', version: P.major },
      { brand: 'Not/A)Brand', version: '24' }
    ];
    var full = [
      { brand: 'Chromium', version: P.full },
      { brand: 'Google Chrome', version: P.full },
      { brand: 'Not/A)Brand', version: '24.0.0.0' }
    ];
    var MOB = !P.desktop, PLAT = P.desktop ? 'Linux' : 'Android';
    var low = { brands: brands, mobile: MOB, platform: PLAT };
    var uad = {
      get brands() { return brands; }, get mobile() { return MOB; }, get platform() { return PLAT; },
      getHighEntropyValues: mask(function (hints) {
        var all = P.desktop
          ? { architecture: 'x86', bitness: '64', model: '', platformVersion: '6.5.0', uaFullVersion: P.full, fullVersionList: full, wow64: false, formFactors: ['Desktop'] }
          : { architecture: '', bitness: '', model: P.model, platformVersion: P.androidVer + '.0.0', uaFullVersion: P.full, fullVersionList: full, wow64: false, formFactors: ['Mobile'] };
        var out = { brands: brands, mobile: MOB, platform: PLAT };
        (hints || []).forEach(function (h) { if (h in all) out[h] = all[h]; });
        return Promise.resolve(out);
      }, function getHighEntropyValues() {}),
      toJSON: mask(function () { return low; }, function toJSON() {})
    };
    try { Object.setPrototypeOf(uad, (G.NavigatorUAData && NavigatorUAData.prototype) || Object.prototype); } catch (e) {}
    def(navigator, 'userAgentData', uad);
  }

  /* ---------- window.chrome (tidak ada di WebView, ada di Chrome asli) ---------- */
  if (!IS_WORKER && !window.chrome) {
    var chrome = {
      app: { isInstalled: false, InstallState: { DISABLED: 'disabled', INSTALLED: 'installed', NOT_INSTALLED: 'not_installed' },
             RunningState: { CANNOT_RUN: 'cannot_run', READY_TO_RUN: 'ready_to_run', RUNNING: 'running' },
             getDetails: function () { return null; }, getIsInstalled: function () { return false; }, runningState: function () { return 'cannot_run'; } },
      runtime: { OnInstalledReason: { CHROME_UPDATE: 'chrome_update', INSTALL: 'install', SHARED_MODULE_UPDATE: 'shared_module_update', UPDATE: 'update' },
                 PlatformOs: { ANDROID: 'android', CROS: 'cros', LINUX: 'linux', MAC: 'mac', OPENBSD: 'openbsd', WIN: 'win' },
                 connect: function () {}, sendMessage: function () {}, id: undefined },
      loadTimes: function () { var t = performance.timing; return { requestTime: t.navigationStart / 1000, startLoadTime: t.fetchStart / 1000,
                 commitLoadTime: t.responseStart / 1000, finishDocumentLoadTime: t.domContentLoadedEventEnd / 1000, finishLoadTime: t.loadEventEnd / 1000,
                 firstPaintTime: 0, firstPaintAfterLoadTime: 0, navigationType: 'Other', wasFetchedViaSpdy: true, wasNpnNegotiated: true,
                 npnNegotiatedProtocol: 'h2', wasAlternateProtocolAvailable: false, connectionInfo: 'h2' }; },
      csi: function () { var t = performance.timing; return { startE: t.navigationStart, onloadT: t.domContentLoadedEventEnd, pageT: performance.now(), tran: 15 }; }
    };
    try { Object.defineProperty(window, 'chrome', { value: chrome, writable: true, configurable: true, enumerable: true }); } catch (e) {}
  }

  /* ---------- Canvas noise deterministik per sesi ---------- */
  var seed = P.seed >>> 0;
  function rnd(i) { var x = (seed ^ (i * 2654435761)) >>> 0; x = ((x ^ (x >>> 16)) * 0x45d9f3b) >>> 0; x = ((x ^ (x >>> 16)) * 0x45d9f3b) >>> 0; return (x ^ (x >>> 16)) >>> 0; }
  function noise(data) {
    var n = data.length >> 2; if (n < 64) return;
    var count = 24;
    for (var k = 0; k < count; k++) {
      var px = rnd(k) % n, idx = px * 4, ch = rnd(k + 1000) % 3;
      var v = data[idx + ch] + ((rnd(k + 2000) & 1) ? 1 : -1);
      data[idx + ch] = v < 0 ? 0 : v > 255 ? 255 : v;
    }
  }
  var _getImageData = null;
  if (G.CanvasRenderingContext2D) {
    var C2D = CanvasRenderingContext2D.prototype; _getImageData = C2D.getImageData;
    C2D.getImageData = mask(function () { var d = _getImageData.apply(this, arguments); try { noise(d.data); } catch (e) {} return d; }, _getImageData);
  }
  if (G.OffscreenCanvasRenderingContext2D) {
    var OC2D = OffscreenCanvasRenderingContext2D.prototype, _ogid = OC2D.getImageData;
    OC2D.getImageData = mask(function () { var d = _ogid.apply(this, arguments); try { noise(d.data); } catch (e) {} return d; }, _ogid);
    if (!_getImageData) _getImageData = _ogid;
  }
  function noisyCopy(cv) {
    try {
      var ctx = cv.getContext('2d'); if (!ctx || !cv.width || !cv.height) return cv;
      var d = _getImageData.call(ctx, 0, 0, cv.width, cv.height); noise(d.data);
      var c2 = IS_WORKER ? new OffscreenCanvas(cv.width, cv.height) : document.createElement('canvas');
      c2.width = cv.width; c2.height = cv.height;
      c2.getContext('2d').putImageData(d, 0, 0); return c2;
    } catch (e) { return cv; }
  }
  if (G.HTMLCanvasElement) {
    var HC = HTMLCanvasElement.prototype, _toDataURL = HC.toDataURL, _toBlob = HC.toBlob;
    HC.toDataURL = mask(function () { return _toDataURL.apply(noisyCopy(this), arguments); }, _toDataURL);
    HC.toBlob = mask(function () { return _toBlob.apply(noisyCopy(this), arguments); }, _toBlob);
  }
  if (G.OffscreenCanvas && OffscreenCanvas.prototype.convertToBlob) {
    var OCP = OffscreenCanvas.prototype, _ctb = OCP.convertToBlob;
    OCP.convertToBlob = mask(function () { return _ctb.apply(noisyCopy(this), arguments); }, _ctb);
  }

  /* ---------- AudioContext fingerprint noise ---------- */
  try {
    var AB = AudioBuffer.prototype, _gcd = AB.getChannelData;
    AB.getChannelData = mask(function () {
      var arr = _gcd.apply(this, arguments);
      if (!arr.__sesi) { try { Object.defineProperty(arr, '__sesi', { value: 1 }); } catch (e) {}
        var step = Math.max(1, (arr.length / 64) | 0);
        for (var i = 0; i < arr.length; i += step) arr[i] += ((rnd(i) % 1000) - 500) * 1e-8; }
      return arr;
    }, _gcd);
  } catch (e) {}

  /* ---------- WebGL: tetap pakai GPU asli (konsisten), hanya rapikan debug ext ---------- */
  try {
    if (!G.WebGLRenderingContext) throw 0;
    var WG = WebGLRenderingContext.prototype, _gp = WG.getParameter;
    WG.getParameter = mask(function (p) {
      if (p === 37445) return P.gpuVendor;   // UNMASKED_VENDOR_WEBGL
      if (p === 37446) return P.gpuRenderer; // UNMASKED_RENDERER_WEBGL
      return _gp.apply(this, arguments);
    }, _gp);
    if (G.WebGL2RenderingContext) {
      var W2 = WebGL2RenderingContext.prototype, _gp2 = W2.getParameter;
      W2.getParameter = mask(function (p) {
        if (p === 37445) return P.gpuVendor;
        if (p === 37446) return P.gpuRenderer;
        return _gp2.apply(this, arguments);
      }, _gp2);
    }
  } catch (e) {}
  /* ---------- Zona waktu sesi (harus cocok dengan negara VPN) ---------- */
  if (P.tz) try {
    var TZ = P.tz;
    var realOffsetOf = Date.prototype.getTimezoneOffset;
    var dtfCache = {};
    function dtf(tz) {
      if (!dtfCache[tz]) dtfCache[tz] = new Intl.DateTimeFormat('en-US', { timeZone: tz, hourCycle: 'h23',
        year: 'numeric', month: 'numeric', day: 'numeric', hour: 'numeric', minute: 'numeric', second: 'numeric' });
      return dtfCache[tz];
    }
    // offset (menit, tanda seperti getTimezoneOffset) untuk instan t di zona TZ
    function tzOffset(t) {
      try {
        var parts = dtf(TZ).formatToParts(new Date(t)), o = {};
        parts.forEach(function (p) { o[p.type] = p.value; });
        var asUTC = Date.UTC(+o.year, +o.month - 1, +o.day, +o.hour % 24, +o.minute, +o.second);
        return Math.round((Math.floor(t / 1000) * 1000 - asUTC) / 60000);
      } catch (e) { return realOffsetOf.call(new Date(t)); }
    }
    function shift(d) { // Date yang waktu lokal aslinya = waktu TZ untuk instan d
      var t = d.getTime(); if (isNaN(t)) return d;
      return new Date(t - (tzOffset(t) - realOffsetOf.call(d)) * 60000);
    }
    var DP = Date.prototype;
    DP.getTimezoneOffset = mask(function () { var t = this.getTime(); return isNaN(t) ? NaN : tzOffset(t); }, realOffsetOf);
    ['getFullYear', 'getMonth', 'getDate', 'getDay', 'getHours', 'getMinutes', 'getSeconds', 'getMilliseconds', 'getYear'].forEach(function (k) {
      var orig = DP[k]; if (!orig) return;
      DP[k] = mask(function () { return orig.call(shift(this)); }, orig);
    });
    function pad(n, w) { n = String(n); while (n.length < (w || 2)) n = '0' + n; return n; }
    function offStr(t) { var m = -tzOffset(t), s = m < 0 ? '-' : '+'; m = Math.abs(m); return s + pad(m / 60 | 0) + pad(m % 60); }
    function tzName(t, style) {
      try { var f = new Intl.DateTimeFormat('en-US', { timeZone: TZ, timeZoneName: style }).formatToParts(new Date(t));
        for (var i = 0; i < f.length; i++) if (f[i].type === 'timeZoneName') return f[i].value; } catch (e) {} return TZ;
    }
    var DAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'], MONS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    var _toString = DP.toString, _toTimeString = DP.toTimeString, _toDateString = DP.toDateString;
    function dateStr(d) { return DAYS[DP.getDay.call(d)] + ' ' + MONS[DP.getMonth.call(d)] + ' ' + pad(DP.getDate.call(d)) + ' ' + DP.getFullYear.call(d); }
    function timeStr(d) { var t = d.getTime(); return pad(DP.getHours.call(d)) + ':' + pad(DP.getMinutes.call(d)) + ':' + pad(DP.getSeconds.call(d)) + ' GMT' + offStr(t) + ' (' + tzName(t, 'long') + ')'; }
    DP.toString = mask(function () { if (isNaN(this.getTime())) return _toString.call(this); return dateStr(this) + ' ' + timeStr(this); }, _toString);
    DP.toDateString = mask(function () { if (isNaN(this.getTime())) return _toDateString.call(this); return dateStr(this); }, _toDateString);
    DP.toTimeString = mask(function () { if (isNaN(this.getTime())) return _toTimeString.call(this); return timeStr(this); }, _toTimeString);
    // Intl: default timeZone = TZ
    var _DTF = Intl.DateTimeFormat;
    var NDTF = function DateTimeFormat(loc, opt) {
      opt = opt ? Object.assign({}, opt) : {};
      if (!opt.timeZone) opt.timeZone = TZ;
      return new _DTF(loc, opt);
    };
    NDTF.prototype = _DTF.prototype; NDTF.supportedLocalesOf = _DTF.supportedLocalesOf;
    Intl.DateTimeFormat = mask(NDTF, _DTF);
    try { Object.defineProperty(_DTF.prototype, 'constructor', { value: NDTF, writable: true, configurable: true, enumerable: false }); } catch (e) {}
    ['toLocaleString', 'toLocaleDateString', 'toLocaleTimeString'].forEach(function (k) {
      var orig = DP[k];
      DP[k] = mask(function (loc, opt) { opt = opt ? Object.assign({}, opt) : {}; if (!opt.timeZone) opt.timeZone = TZ; return orig.call(this, loc, opt); }, orig);
    });
  } catch (e) {}

  /* ---------- WebRTC: hanya kandidat relay (TURN) — tidak ada bocor IP lokal/publik via STUN ---------- */
  try {
    var RPC = G.RTCPeerConnection || G.webkitRTCPeerConnection;
    if (RPC) {
      var relayCfg = function (c) { c = c ? Object.assign({}, c) : {}; c.iceTransportPolicy = 'relay'; return c; };
      var NRPC = function RTCPeerConnection(cfg, constraints) { return new RPC(relayCfg(cfg), constraints); };
      NRPC.prototype = RPC.prototype;
      try { NRPC.generateCertificate = RPC.generateCertificate.bind(RPC); } catch (e) {}
      var _setCfg = RPC.prototype.setConfiguration;
      if (_setCfg) RPC.prototype.setConfiguration = mask(function (c) { return _setCfg.call(this, relayCfg(c)); }, _setCfg);
      G.RTCPeerConnection = mask(NRPC, RPC);
      if (G.webkitRTCPeerConnection) G.webkitRTCPeerConnection = G.RTCPeerConnection;
    }
  } catch (e) {}

  /* ---------- Suntik spoof yang sama ke Worker / SharedWorker (fingerprint di worker harus konsisten) ----------
     Aman-CSP: * blob: → gabung [SRC, Blob asli] jadi Blob baru (tanpa importScripts; izin CSP-nya sama dengan worker asli).
               * http(s): → blob worker + importScripts, HANYA jika CSP halaman mengizinkan blob worker (diuji lewat probe);
                 kalau tidak, diteruskan apa adanya. Pembungkus lama membuat Cloudflare Turnstile gagal (script-src tanpa blob:)
               * data:/lainnya → diteruskan apa adanya. */
  if (!IS_WORKER && SRC && SRC.indexOf('__SELF_') !== 0) try {
    var blobReg = new Map();                       // blob:url → Blob (untuk worker dari blob)
    var _cou = URL.createObjectURL, _rou = URL.revokeObjectURL;
    URL.createObjectURL = mask(function createObjectURL(o) {
      var u = _cou.apply(URL, arguments);
      try { if (o instanceof Blob) { blobReg.set(u, o); if (blobReg.size > 200) blobReg.delete(blobReg.keys().next().value); } } catch (e) {}
      return u;
    }, _cou);
    URL.revokeObjectURL = mask(function revokeObjectURL(u) { blobReg.delete(u); return _rou.apply(URL, arguments); }, _rou);

    var blobWorkerBlocked = false;
    try {
      document.addEventListener('securitypolicyviolation', function (ev) {
        var d = (ev.violatedDirective || ev.effectiveDirective || '');
        if ((d.indexOf('worker-src') === 0 || d.indexOf('child-src') === 0 || d.indexOf('script-src') === 0) && String(ev.blockedURI).indexOf('blob') === 0) blobWorkerBlocked = true;
      }, true);
      var probeUrl = _cou.call(URL, new Blob([''], { type: 'text/javascript' }));
      var probe = new Worker(probeUrl);
      probe.onerror = function () { blobWorkerBlocked = true; };
      setTimeout(function () { try { probe.terminate(); } catch (e) {} try { _rou.call(URL, probeUrl); } catch (e) {} }, 50);
    } catch (e) { blobWorkerBlocked = true; }

    function wrapWorker(Orig, name) {
      if (!Orig) return;
      var W = function (url, opts) {
        try {
          var abs = new URL(String(url), location.href).href;
          var isModule = opts && opts.type === 'module';
          if (abs.indexOf('blob:') === 0) {
            var orig = blobReg.get(abs);
            if (!orig || isModule) return new Orig(url, opts);
            return new Orig(_cou.call(URL, new Blob([SRC + '\n', orig], { type: orig.type || 'text/javascript' })), opts);
          }
          if (blobWorkerBlocked || !/^https?:/.test(abs)) return new Orig(url, opts);
          var code = isModule ? SRC + '\nimport ' + JSON.stringify(abs) + ';'
                              : SRC + '\nimportScripts(' + JSON.stringify(abs) + ');';
          return new Orig(_cou.call(URL, new Blob([code], { type: 'text/javascript' })), opts);
        } catch (e) { return new Orig(url, opts); }
      };
      W.prototype = Orig.prototype;
      G[name] = mask(W, Orig);
    }
    wrapWorker(G.Worker, 'Worker');
    wrapWorker(G.SharedWorker, 'SharedWorker');
  } catch (e) {}
})();