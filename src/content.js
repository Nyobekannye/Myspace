(function () {
  "use strict";

  const utils = globalThis.AxiomDolaUtils;
  const media = new Map();
  const FACE_TERMS = /(?:face\s*(?:filter|swap|beauty|retouch|effect)|portrait(?:\s*(?:filter|effect))?|beautify|retouch|filter\s*wajah|tukar\s*wajah|wajah)/i;
  const CONTROL_SELECTOR = "button, a, label, [role='button'], [role='menuitem'], [role='option'], [data-testid], [data-e2e]";
  const BYPASS_MESSAGE = "AXIOM_DOLA_FACE_BYPASS";
  const EXTENSION_SOURCE = "axiom-dola-ext";
  let hideFaceControls = true;
  let bypassFaceFilter = true;
  let panel;
  let status;
  let bypassState;
  let policyFrame;

  function broadcastFaceBypass() {
    try {
      window.postMessage(
        { source: EXTENSION_SOURCE, type: BYPASS_MESSAGE, enabled: bypassFaceFilter },
        location.origin
      );
    } catch {}
  }

  function addCandidate(candidate) {
    const url = utils.normalizeUrl(candidate && candidate.url, location.href);
    if (!url || !utils.isAllowedMediaUrl(url)) return;
    media.set(url, { ...media.get(url), ...candidate, url });
    updateStatus(`${media.size} sumber video ditemukan.`);
  }

  function scanVideos() {
    document.querySelectorAll("video").forEach(video => {
      const dimensions = { width: video.videoWidth, height: video.videoHeight };
      [video.currentSrc, video.src].forEach(url => addCandidate({ url, ...dimensions, source: "elemen video" }));
      video.querySelectorAll("source[src]").forEach(source => {
        addCandidate({ url: source.src, ...dimensions, source: "elemen source" });
      });
    });
  }

  function faceControl(element) {
    const label = [element.textContent, element.getAttribute("aria-label"), element.getAttribute("title")]
      .filter(Boolean).join(" ").replace(/\s+/g, " ").trim();
    return label.length > 0 && label.length <= 100 && FACE_TERMS.test(label);
  }

  function applyFaceControlPolicy(root = document) {
    if (root.matches?.(CONTROL_SELECTOR)) {
      root.classList.toggle("axiom-dola-face-hidden", hideFaceControls && faceControl(root));
    }
    root.querySelectorAll?.(CONTROL_SELECTOR).forEach(element => {
      element.classList.toggle("axiom-dola-face-hidden", hideFaceControls && faceControl(element));
    });
  }

  function scheduleFaceControlPolicy() {
    if (policyFrame) return;
    policyFrame = requestAnimationFrame(() => {
      policyFrame = 0;
      applyFaceControlPolicy();
    });
  }

  function updateStatus(message) {
    if (status) status.textContent = message;
  }

  async function downloadBest() {
    scanVideos();
    const best = utils.chooseBestCandidate([...media.values()]);
    if (!best) {
      updateStatus("Putar atau buat video dahulu, lalu coba lagi.");
      return;
    }
    if (/\.m3u8(?:$|[?#])/i.test(best.url)) {
      updateStatus("Hanya stream HLS ditemukan; buka videonya sampai URL MP4 tersedia.");
      return;
    }
    updateStatus("Memulai unduhan kualitas tertinggi…");
    const result = await chrome.runtime.sendMessage({
      type: "AXIOM_DOLA_DOWNLOAD",
      url: best.url,
      filename: utils.safeFilename(`dola-video-hd-${Date.now()}`),
    });
    updateStatus(result && result.ok ? "Unduhan dimulai." : `Gagal: ${result?.error || "URL ditolak"}`);
  }

  function createPanel() {
    if (panel || !document.documentElement) return;
    panel = document.createElement("aside");
    panel.id = "axiom-dola-panel";
    panel.innerHTML = `
      <strong>Dola HD</strong>
      <button type="button" id="axiom-dola-download">Download HD</button>
      <span id="axiom-dola-status">Menunggu video…</span>
      <span id="axiom-dola-bypass"></span>
    `;
    document.documentElement.appendChild(panel);
    status = panel.querySelector("#axiom-dola-status");
    bypassState = panel.querySelector("#axiom-dola-bypass");
    renderBypassState();
    panel.querySelector("#axiom-dola-download").addEventListener("click", downloadBest);
  }

  function renderBypassState() {
    if (bypassState && bypassFaceFilter) {
      bypassState.textContent = "Bypass filter wajah: AKTIF";
      bypassState.style.color = "#82f5b5";
    } else if (bypassState) {
      bypassState.textContent = "";
    }
  }

  window.addEventListener("axiom-dola-media", event => addCandidate(event.detail));
  document.addEventListener("play", scanVideos, true);
  document.addEventListener("click", event => {
    const blocked = event.target.closest?.(".axiom-dola-face-hidden");
    if (blocked && hideFaceControls) {
      event.preventDefault();
      event.stopImmediatePropagation();
    }
  }, true);

  chrome.storage.sync.get({ hideFaceControls: true, bypassFaceFilter: true }, settings => {
    hideFaceControls = settings.hideFaceControls;
    bypassFaceFilter = settings.bypassFaceFilter;
    broadcastFaceBypass();
    applyFaceControlPolicy();
    renderBypassState();
  });
  chrome.storage.onChanged.addListener(changes => {
    if (changes.hideFaceControls) {
      hideFaceControls = changes.hideFaceControls.newValue;
      applyFaceControlPolicy();
    }
    if (changes.bypassFaceFilter) {
      bypassFaceFilter = changes.bypassFaceFilter.newValue;
      broadcastFaceBypass();
      renderBypassState();
    }
  });

  const observer = new MutationObserver(records => {
    for (const record of records) {
      for (const node of record.addedNodes) {
        if (node.nodeType === Node.ELEMENT_NODE) applyFaceControlPolicy(node);
      }
    }
    scheduleFaceControlPolicy();
    scanVideos();
  });

  function start() {
    createPanel();
    applyFaceControlPolicy();
    scanVideos();
    observer.observe(document.documentElement, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: ["aria-label", "title", "class"],
    });
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", start, { once: true });
  else start();
})();
