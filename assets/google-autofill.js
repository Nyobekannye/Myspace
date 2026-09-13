(function () {
  if (window.__whempyAutofillInstalled) return;
  window.__whempyAutofillInstalled = true;

  function setNativeValue(el, value) {
    var proto = el.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
    var desc = Object.getOwnPropertyDescriptor(proto, 'value');
    if (desc && desc.set) desc.set.call(el, value); else el.value = value;
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }
  function visible(el) { return !!(el && el.offsetParent !== null && !el.disabled); }
  function findField(selectors) {
    for (var i = 0; i < selectors.length; i++) {
      var els = document.querySelectorAll(selectors[i]);
      for (var j = 0; j < els.length; j++) if (visible(els[j])) return els[j];
    }
    return null;
  }
  function clickNext(afterField) {
    var candidates = [
      '#identifierNext button', '#passwordNext button',
      'button[jsname="LgbsSe"]', 'div[role="button"][id$="Next"] button',
      'button[type="submit"]'
    ];
    for (var i = 0; i < candidates.length; i++) {
      var btn = document.querySelector(candidates[i]);
      if (visible(btn)) { btn.click(); return true; }
    }
    if (afterField && afterField.form) {
      if (afterField.form.requestSubmit) afterField.form.requestSubmit(); else afterField.form.submit();
      return true;
    }
    return false;
  }
  function tryEmail(email) {
    var el = findField(['input#identifierId', 'input[name="identifier"]', 'input[type="email"]']);
    if (!el) return false;
    setNativeValue(el, email);
    setTimeout(function () { clickNext(el); }, 250);
    return true;
  }
  function tryPassword(password) {
    var el = findField(['input[type="password"][name="Passwd"]', 'input[type="password"]']);
    if (!el) return false;
    setNativeValue(el, password);
    setTimeout(function () { clickNext(el); }, 250);
    return true;
  }

  var tries = 0, timer = null;
  function loop() {
    tries++;
    var email = window.__whempyPendingEmail, password = window.__whempyPendingPassword;
    if (!email && !password) return;
    if (password && tryPassword(password)) { window.__whempyPendingPassword = null; if (!email) window.__whempyPendingEmail = null; return; }
    if (email && tryEmail(email)) { window.__whempyPendingEmail = null; }
    if ((email && window.__whempyPendingEmail) || (password && window.__whempyPendingPassword)) {
      if (tries < 60) timer = setTimeout(loop, 350);
    }
  }
  window.__whempyFillLogin = function (email, password) {
    tries = 0; if (timer) clearTimeout(timer);
    window.__whempyPendingEmail = email || null;
    window.__whempyPendingPassword = password || null;
    loop();
  };
  document.addEventListener('DOMContentLoaded', function () { if (window.__whempyPendingEmail || window.__whempyPendingPassword) loop(); });
  new MutationObserver(function () { if (window.__whempyPendingEmail || window.__whempyPendingPassword) loop(); })
    .observe(document.documentElement, { childList: true, subtree: true });
})();
