package id.kuli.sesibrowser;

import android.webkit.JavascriptInterface;

/** Per-WebView JS interface. Every method is invoked from the WebView JS thread. */
public class Bridge {
    private final ExtRuntime rt;
    private final String ctx;
    private final android.webkit.WebView view;   // tab pemilik (ctx=page) — balasan pesan kembali ke sini
    public Bridge(ExtRuntime rt, String ctx) { this(rt, ctx, null); }
    public Bridge(ExtRuntime rt, String ctx, android.webkit.WebView view) { this.rt = rt; this.ctx = ctx; this.view = view; }

    // messaging
    @JavascriptInterface public void sendMessage(String from, String msgJson, String cbId) { rt.sendMessage(from, msgJson, cbId, view); }
    @JavascriptInterface public void tabsSendMessage(String from, String tabId, String msgJson, String cbId) { rt.tabsSendMessage(from, msgJson, cbId); }
    @JavascriptInterface public void respond(String cbId, String origin, String json) { rt.respond(cbId, origin, json); }
    @JavascriptInterface public void bgReady() { rt.onBgReady(); }

    // storage
    @JavascriptInterface public String storageGet() { return rt.storageGet(); }
    @JavascriptInterface public void storageSet(String json) { rt.storageSet(json); }
    @JavascriptInterface public void storageRemove(String keysJson) { rt.storageRemove(keysJson); }
    @JavascriptInterface public void storageClear() { rt.storageClear(); }

    // tabs / ui
    @JavascriptInterface public String tabsQuery() { return rt.tabsQuery(); }
    @JavascriptInterface public void openUrl(String url) { rt.openUrl(url); }
    @JavascriptInterface public void focusTab() { rt.closePopup(); }
    @JavascriptInterface public void reloadTab() { rt.reloadTab(); }
    @JavascriptInterface public void closePopup() { rt.closePopup(); }
    @JavascriptInterface public void openPopup() { rt.openPopup(); }
    @JavascriptInterface public void reload() { rt.reloadTab(); }
    @JavascriptInterface public String getURL(String path) { return rt.getURL(path); }

    // downloads
    @JavascriptInterface public String download(String json) { return rt.download(json); }
    @JavascriptInterface public String downloadSearch(String json) { return rt.downloadSearch(json); }
    @JavascriptInterface public void downloadCancel(String id) { rt.downloadRemove(id); }
    @JavascriptInterface public String downloadErase(String json) { return rt.downloadErase(json); }
    @JavascriptInterface public void downloadRemoveFile(String id) { rt.downloadRemove(id); }
    @JavascriptInterface public void downloadOpen(String id) { rt.downloadOpen(); }

    // cookies
    @JavascriptInterface public String cookiesGet(String url) { return rt.cookiesGet(url); }
    @JavascriptInterface public void cookiesSet(String url, String cookie) { rt.cookiesSet(url, cookie); }

    @JavascriptInterface public void log(String s) { android.util.Log.i("Whempy/" + ctx, s); }

    // saved accounts (local-only, encrypted) — used only by the popup UI
    @JavascriptInterface public String credsList() { return rt.credsList(); }
    @JavascriptInterface public String credsSave(String json) { return rt.credsSave(json); }
    @JavascriptInterface public void credsDelete(String id) { rt.credsDelete(id); }
    @JavascriptInterface public String fillLogin(String id) { return rt.fillLoginById(id); }

    // debug log (in-app diagnostics)
    @JavascriptInterface public String debugLogText() { return rt.debugLogText(); }
    @JavascriptInterface public void shareDebugLog() { rt.shareDebugLog(); }
}
