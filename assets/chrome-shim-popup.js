
(function () {
  if (window.__whempyShim) return;
  var CTX = 'popup';
  var B = window.__WhempyBridge;
  if (!B) { console.warn('[Whempy shim] bridge missing'); return; }
  var MANIFEST = {"manifest_version": 3, "name": "Sesi MAX MODE", "short_name": "MAX MODE", "version": "2.9", "description": "Sesi MAX MODE by Whempy & Dhon \u2014 Dola companion: every video 30s as ONE single clip (never 2), max HD, no watermark. Aggressive single-clip enforcer. Auto-download optional (Settings).", "icons": {"16": "icon16.png", "48": "icon48.png", "128": "icon128.png"}, "permissions": ["sidePanel", "storage", "cookies", "downloads", "offscreen", "tabs", "webNavigation", "alarms", "declarativeNetRequest", "declarativeNetRequestWithHostAccess"], "side_panel": {"default_path": "popup.html"}, "host_permissions": ["*://*.dola.com/*", "*://*.seaart.ai/*", "*://*.byteintl.com/*", "*://*.ibytedtos.com/*", "https://dola.com/*", "https://www.dola.com/*", "<all_urls>"], "background": {"service_worker": "background.js"}, "content_scripts": [{"matches": ["*://*.dola.com/*", "*://*.seaart.ai/*", "https://dola.com/*", "https://www.dola.com/*"], "js": ["media-extractor.js", "inject.js", "single-clip-enforcer.js"], "run_at": "document_start", "world": "MAIN"}, {"matches": ["*://*.dola.com/*", "*://*.seaart.ai/*", "https://dola.com/*", "https://www.dola.com/*"], "css": ["dock-polish.css"], "js": ["content.js", "raw-watermark-free-downloader.js", "site-polish.js"], "run_at": "document_start"}], "action": {"default_title": "Sesi MAX MODE", "default_icon": {"16": "icon16.png", "48": "icon48.png", "128": "icon128.png"}, "default_popup": "popup.html"}, "web_accessible_resources": [{"resources": ["brand-icon.svg", "icon16.png", "icon48.png", "icon128.png"], "matches": ["*://*.dola.com/*", "*://*.seaart.ai/*", "https://dola.com/*", "https://www.dola.com/*"]}], "author": "Whempy & Dhon", "homepage_url": "https://wa.me/628889841098", "minimum_chrome_version": "114"};
  var pending = {}, seq = 0, lastError;

  function uid() { return CTX + '-' + (++seq) + '-' + Date.now().toString(36); }
  function mkEvent() {
    var ls = [];
    return {
      _ls: ls,
      addListener: function (f) { if (typeof f === 'function' && ls.indexOf(f) < 0) ls.push(f); },
      removeListener: function (f) { var i = ls.indexOf(f); if (i >= 0) ls.splice(i, 1); },
      hasListener: function (f) { return ls.indexOf(f) >= 0; },
      hasListeners: function () { return ls.length > 0; },
      _fire: function () { var a = arguments; ls.slice().forEach(function (f) { try { f.apply(null, a); } catch (e) { console.warn('[Whempy shim] listener error', e); } }); }
    };
  }
  // Callback-or-promise helper. work(resolve, reject) runs synchronously.
  function cbOrPromise(cb, work) {
    var p = new Promise(work);
    if (typeof cb === 'function') {
      p.then(function (v) { lastError = undefined; try { cb(v); } finally { lastError = undefined; } },
             function (e) { lastError = { message: String(e && e.message || e) }; try { cb(undefined); } finally { lastError = undefined; } });
      return undefined;
    }
    return p;
  }
  function finish(p, value, err) {
    if (p.cb) { lastError = err ? { message: err } : undefined; try { p.cb(value); } finally { lastError = undefined; } }
    else if (err) p.reject(new Error(err)); else p.resolve(value);
  }
  function send(fire, cb) {
    var id = uid();
    var p = { cb: typeof cb === 'function' ? cb : null };
    var promise = new Promise(function (res, rej) { p.resolve = res; p.reject = rej; });
    pending[id] = p;
    setTimeout(function () { if (pending[id]) { delete pending[id]; finish(p, undefined, 'The message port closed before a response was received.'); } }, 180000);
    try { fire(id); } catch (e) { delete pending[id]; finish(p, undefined, String(e)); }
    if (p.cb) { promise.catch(function () {}); return undefined; }
    return promise;
  }
  function matchPattern(pat, url) {
    if (pat === '<all_urls>') return true;
    try { return new RegExp('^' + String(pat).replace(/[.+^${}()|[\]\\]/g, '\\$&').replace(/\*/g, '.*') + '$', 'i').test(url); } catch (e) { return false; }
  }
  function stubNs(extra) {
    var t = extra || {};
    return new Proxy(t, { get: function (o, k) {
      if (k in o) return o[k];
      if (typeof k !== 'string') return undefined;
      if (/^on[A-Z]/.test(k)) { o[k] = mkEvent(); return o[k]; }
      o[k] = function () { var cb = arguments[arguments.length - 1]; if (typeof cb === 'function') { setTimeout(function () { cb(undefined); }, 0); return undefined; } return Promise.resolve(undefined); };
      return o[k];
    } });
  }

  // ---------- runtime ----------
  var runtime = {
    id: 'whempy-maxmode',
    onMessage: mkEvent(), onInstalled: mkEvent(), onStartup: mkEvent(), onConnect: mkEvent(), onSuspend: mkEvent(), onUpdateAvailable: mkEvent(), onMessageExternal: mkEvent(),
    getManifest: function () { return JSON.parse(JSON.stringify(MANIFEST)); },
    getURL: function (path) { return B.getURL(String(path || '')); },
    getPlatformInfo: function (cb) { return cbOrPromise(cb, function (r) { r({ os: 'android', arch: 'arm64', nacl_arch: 'arm64' }); }); },
    sendMessage: function () {
      var a = Array.prototype.slice.call(arguments), msg, cb;
      if (a.length > 1 && typeof a[0] === 'string' && typeof a[1] !== 'function') a.shift();
      msg = a[0];
      for (var i = 1; i < a.length; i++) if (typeof a[i] === 'function') cb = a[i];
      return send(function (id) { B.sendMessage(CTX, JSON.stringify(msg === undefined ? null : msg), id); }, cb);
    },
    connect: function () { throw new Error('runtime.connect not supported'); },
    reload: function () { B.reload(); },
    openOptionsPage: function (cb) { return cbOrPromise(cb, function (r) { B.openPopup(); r(); }); }
  };
  Object.defineProperty(runtime, 'lastError', { get: function () { return lastError; }, configurable: true });

  // ---------- storage ----------
  function readAll() { try { return JSON.parse(B.storageGet() || '{}'); } catch (e) { return {}; } }
  var storageChanged = mkEvent();
  var local = {
    onChanged: mkEvent(),
    get: function (keys, cb) {
      if (typeof keys === 'function') { cb = keys; keys = null; }
      return cbOrPromise(cb, function (res) {
        var all = readAll(), out = {};
        if (keys == null) out = all;
        else if (typeof keys === 'string') { if (keys in all) out[keys] = all[keys]; }
        else if (Array.isArray(keys)) keys.forEach(function (k) { if (k in all) out[k] = all[k]; });
        else if (typeof keys === 'object') Object.keys(keys).forEach(function (k) { out[k] = (k in all) ? all[k] : keys[k]; });
        res(out);
      });
    },
    set: function (items, cb) { return cbOrPromise(cb, function (res) { B.storageSet(JSON.stringify(items || {})); res(); }); },
    remove: function (keys, cb) { if (typeof keys === 'string') keys = [keys]; return cbOrPromise(cb, function (res) { B.storageRemove(JSON.stringify(keys || [])); res(); }); },
    clear: function (cb) { return cbOrPromise(cb, function (res) { B.storageClear(); res(); }); },
    getBytesInUse: function (k, cb) { return cbOrPromise(typeof k === 'function' ? k : cb, function (r) { r(B.storageGet().length); }); },
    setAccessLevel: function (o, cb) { return cbOrPromise(cb, function (r) { r(); }); },
    QUOTA_BYTES: 10485760
  };
  var storage = { local: local, sync: local, session: local, managed: { get: local.get, onChanged: mkEvent() }, onChanged: storageChanged };

  // ---------- tabs ----------
  var tabs = {
    onUpdated: mkEvent(), onActivated: mkEvent(), onRemoved: mkEvent(), onCreated: mkEvent(), onReplaced: mkEvent(),
    query: function (info, cb) {
      return cbOrPromise(cb, function (res) {
        var list = JSON.parse(B.tabsQuery());
        if (info && info.url) { var pats = [].concat(info.url); list = list.filter(function (t) { return pats.some(function (p) { return matchPattern(p, t.url || ''); }); }); }
        if (info && info.active === false) list = [];
        res(list);
      });
    },
    get: function (id, cb) { return cbOrPromise(cb, function (res) { res(JSON.parse(B.tabsQuery())[0]); }); },
    getCurrent: function (cb) { return cbOrPromise(cb, function (res) { res(CTX === 'page' ? JSON.parse(B.tabsQuery())[0] : undefined); }); },
    sendMessage: function (tabId, msg, opts, cb) {
      if (typeof opts === 'function') { cb = opts; opts = undefined; }
      return send(function (id) { B.tabsSendMessage(CTX, String(tabId), JSON.stringify(msg === undefined ? null : msg), id); }, cb);
    },
    create: function (p, cb) { return cbOrPromise(cb, function (res) { B.openUrl((p && p.url) || 'https://www.dola.com/'); res({ id: 1, url: p && p.url, active: true, windowId: 1 }); }); },
    update: function (id, p, cb) { if (typeof id === 'object') { cb = p; p = id; } return cbOrPromise(cb, function (res) { if (p && p.url) B.openUrl(p.url); else B.focusTab(); res({ id: 1, active: true, windowId: 1 }); }); },
    reload: function (id, p, cb) { if (typeof id === 'function') cb = id; if (typeof p === 'function') cb = p; return cbOrPromise(cb, function (res) { B.reloadTab(); res(); }); },
    remove: function (id, cb) { return cbOrPromise(cb, function (res) { res(); }); },
    executeScript: function () { var cb = arguments[arguments.length - 1]; return cbOrPromise(typeof cb === 'function' ? cb : undefined, function (r) { r([]); }); }
  };

  // ---------- downloads ----------
  var downloads = {
    onChanged: mkEvent(), onCreated: mkEvent(), onErased: mkEvent(), onDeterminingFilename: mkEvent(),
    download: function (o, cb) {
      return cbOrPromise(cb, function (res, rej) {
        var r = B.download(JSON.stringify(o || {}));
        if (String(r).indexOf('ERR:') === 0) rej(new Error(String(r).slice(4)));
        else res(Number(r));
      });
    },
    search: function (q, cb) { return cbOrPromise(cb, function (res) { res(JSON.parse(B.downloadSearch(JSON.stringify(q || {})))); }); },
    cancel: function (id, cb) { return cbOrPromise(cb, function (res) { B.downloadCancel(String(id)); res(); }); },
    pause: function (id, cb) { return cbOrPromise(cb, function (res) { res(); }); },
    resume: function (id, cb) { return cbOrPromise(cb, function (res) { res(); }); },
    erase: function (q, cb) { return cbOrPromise(cb, function (res) { res(JSON.parse(B.downloadErase(JSON.stringify(q || {})))); }); },
    removeFile: function (id, cb) { return cbOrPromise(cb, function (res) { B.downloadRemoveFile(String(id)); res(); }); },
    open: function (id) { B.downloadOpen(String(id)); },
    show: function (id) { B.downloadOpen(String(id)); },
    showDefaultFolder: function () { B.downloadOpen('0'); },
    getFileIcon: function (id, o, cb) { return cbOrPromise(typeof o === 'function' ? o : cb, function (r) { r(undefined); }); },
    setShelfEnabled: function () {}, setUiOptions: function (o, cb) { return cbOrPromise(cb, function (r) { r(); }); }
  };

  // ---------- alarms ----------
  var alarmTimers = {}, alarmInfo = {}, onAlarm = mkEvent();
  var alarms = {
    onAlarm: onAlarm,
    create: function (name, info) {
      if (typeof name === 'object') { info = name; name = ''; }
      info = info || {}; alarms.clear(name);
      var now = Date.now();
      var first = info.when ? info.when - now : (info.delayInMinutes != null ? info.delayInMinutes * 60000 : (info.periodInMinutes || 1) * 60000);
      alarmInfo[name] = { name: name, scheduledTime: now + Math.max(0, first), periodInMinutes: info.periodInMinutes };
      function fire() { alarmInfo[name].scheduledTime = Date.now() + (info.periodInMinutes || 0) * 60000; onAlarm._fire({ name: name, scheduledTime: Date.now(), periodInMinutes: info.periodInMinutes }); if (info.periodInMinutes) alarmTimers[name] = setTimeout(fire, info.periodInMinutes * 60000); else { delete alarmTimers[name]; delete alarmInfo[name]; } }
      alarmTimers[name] = setTimeout(fire, Math.max(0, first));
    },
    get: function (name, cb) { if (typeof name === 'function') { cb = name; name = ''; } return cbOrPromise(cb, function (r) { r(alarmInfo[name]); }); },
    getAll: function (cb) { return cbOrPromise(cb, function (r) { r(Object.keys(alarmInfo).map(function (k) { return alarmInfo[k]; })); }); },
    clear: function (name, cb) { if (typeof name === 'function') { cb = name; name = ''; } var had = !!alarmTimers[name]; clearTimeout(alarmTimers[name]); delete alarmTimers[name]; delete alarmInfo[name]; return cbOrPromise(cb, function (r) { r(had); }); },
    clearAll: function (cb) { Object.keys(alarmTimers).forEach(function (k) { clearTimeout(alarmTimers[k]); }); alarmTimers = {}; alarmInfo = {}; return cbOrPromise(cb, function (r) { r(true); }); }
  };

  // ---------- cookies ----------
  var cookies = stubNs({
    onChanged: mkEvent(),
    get: function (d, cb) { return cbOrPromise(cb, function (r) { var all = B.cookiesGet(String(d.url || '')); var m = null; String(all || '').split(';').forEach(function (c) { var i = c.indexOf('='); var n = c.slice(0, i).trim(); if (n === d.name) m = { name: n, value: c.slice(i + 1).trim(), domain: '', path: '/', secure: true, httpOnly: false, session: true, hostOnly: false, storeId: '0' }; }); r(m); }); },
    getAll: function (d, cb) { return cbOrPromise(cb, function (r) { var all = B.cookiesGet(String(d.url || 'https://www.dola.com/')); var out = []; String(all || '').split(';').forEach(function (c) { var i = c.indexOf('='); if (i < 0) return; var n = c.slice(0, i).trim(); if (!d.name || d.name === n) out.push({ name: n, value: c.slice(i + 1).trim(), domain: d.domain || '', path: '/', secure: true, httpOnly: false, session: true, hostOnly: false, storeId: '0' }); }); r(out); }); },
    set: function (d, cb) { return cbOrPromise(cb, function (r) { B.cookiesSet(String(d.url || ''), String(d.name) + '=' + String(d.value == null ? '' : d.value) + '; path=' + (d.path || '/')); r(d); }); }
  });

  var chromeShim = {
    runtime: runtime, storage: storage, tabs: tabs, downloads: downloads, alarms: alarms, cookies: cookies,
    webNavigation: stubNs(), declarativeNetRequest: stubNs({ MAX_NUMBER_OF_DYNAMIC_RULES: 5000, getDynamicRules: function (cb) { return cbOrPromise(cb, function (r) { r([]); }); }, getSessionRules: function (cb) { return cbOrPromise(cb, function (r) { r([]); }); } }),
    windows: stubNs({ WINDOW_ID_CURRENT: -2, getCurrent: function (o, cb) { return cbOrPromise(typeof o === 'function' ? o : cb, function (r) { r({ id: 1, focused: true, type: 'normal' }); }); }, getAll: function (o, cb) { return cbOrPromise(typeof o === 'function' ? o : cb, function (r) { r([{ id: 1, focused: true, type: 'normal' }]); }); } }),
    scripting: stubNs(), permissions: stubNs({ contains: function (p, cb) { return cbOrPromise(cb, function (r) { r(true); }); } }),
    notifications: stubNs(), contextMenus: stubNs(), i18n: { getMessage: function (k) { return k; }, getUILanguage: function () { return navigator.language; }, getAcceptLanguages: function (cb) { return cbOrPromise(cb, function (r) { r([navigator.language]); }); } },
    extension: { getURL: runtime.getURL, inIncognitoContext: false }
    // NOTE: offscreen, sidePanel, action intentionally undefined → extension falls back to mobile mode.
  };

  var target = (typeof window.chrome === 'object' && window.chrome) ? window.chrome : {};
  Object.keys(chromeShim).forEach(function (k) { try { Object.defineProperty(target, k, { value: chromeShim[k], configurable: true, writable: true, enumerable: true }); } catch (e) { target[k] = chromeShim[k]; } });
  try { Object.defineProperty(window, 'chrome', { value: target, configurable: true, writable: true }); } catch (e) { window.chrome = target; }
  if (CTX !== 'page') { window.browser = target; window.importScripts = function () {}; }
  if (CTX === 'popup') { window.close = function () { B.closePopup(); }; }

  window.__whempyShim = {
    ctx: CTX,
    deliverMessage: function (msgJson, senderJson, cbId, origin) {
      var msg = JSON.parse(msgJson), sender = JSON.parse(senderJson);
      var ls = runtime.onMessage._ls.slice();
      if (!ls.length) { B.respond(cbId, origin, '__NOHANDLER__'); return; }
      var responded = false, keepOpen = false;
      function sendResponse(r) { if (responded) return; responded = true; try { B.respond(cbId, origin, JSON.stringify(r === undefined ? null : r)); } catch (e) { B.respond(cbId, origin, 'null'); } }
      ls.forEach(function (f) {
        try {
          var ret = f(msg, sender, sendResponse);
          if (ret === true) keepOpen = true;
          else if (ret && typeof ret.then === 'function') { keepOpen = true; ret.then(sendResponse, function () { sendResponse(undefined); }); }
        } catch (e) { console.warn('[Whempy shim] onMessage handler error', e); }
      });
      if (!responded && !keepOpen) B.respond(cbId, origin, '__NOHANDLER__');
    },
    deliverResponse: function (cbId, json) {
      var p = pending[cbId]; if (!p) return; delete pending[cbId];
      if (json === null || json === undefined) finish(p, undefined, 'Could not establish connection. Receiving end does not exist.');
      else { var v; try { v = JSON.parse(json); } catch (e) { v = undefined; } finish(p, v === null ? undefined : v); }
    },
    fireStorageChanged: function (json) { var ch = JSON.parse(json); storageChanged._fire(ch, 'local'); local.onChanged._fire(ch); },
    fireDownloadChanged: function (json) { downloads.onChanged._fire(JSON.parse(json)); },
    fireDownloadCreated: function (json) { downloads.onCreated._fire(JSON.parse(json)); },
    fireTabUpdated: function (json) { var t = JSON.parse(json); tabs.onUpdated._fire(1, { status: 'complete', url: t.url }, t); }
  };
  if (CTX === 'background') {
    setTimeout(function () { runtime.onInstalled._fire({ reason: 'install' }); runtime.onStartup._fire(); try { B.bgReady(); } catch (e) {} }, 0);
  }
})();
