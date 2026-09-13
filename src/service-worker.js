importScripts("media-utils.js");

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message?.type !== "AXIOM_DOLA_DOWNLOAD") return false;

  let url;
  try {
    url = new URL(message.url);
    if (url.protocol !== "https:") throw new Error("Unduhan wajib menggunakan HTTPS");
    if (!AxiomDolaUtils.isAllowedMediaUrl(url.href)) throw new Error("Host media tidak dikenali");
  } catch (error) {
    sendResponse({ ok: false, error: error.message });
    return false;
  }

  chrome.downloads.download({
    url: url.href,
    filename: AxiomDolaUtils.safeFilename(message.filename?.replace(/\.mp4$/i, "")),
    saveAs: true,
    conflictAction: "uniquify",
  }).then(id => sendResponse({ ok: true, id }))
    .catch(error => sendResponse({ ok: false, error: error.message }));
  return true;
});
