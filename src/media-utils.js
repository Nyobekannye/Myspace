(function (root, factory) {
  const api = factory();
  root.AxiomDolaUtils = api;
  if (typeof module === "object" && module.exports) module.exports = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  const MEDIA_EXTENSION = /\.(?:mp4|webm|mov|m3u8)(?:$|[?#])/i;
  const MEDIA_KEY = /(?:play|download|video|media|main|backup|origin|source).*(?:url|uri)|(?:url|uri)/i;
  const FACE_FILTER_TERMS = /(?:beaut(?:y|ify)|retouch|whiten|smooth|face_?filter|face_?swap|face_?enhance|face\s*filter|face\s*swap|filter.*(?:face|portrait|wajah)|tukar\s*wajah|filter\s*wajah)/i;
  const FACE_REFERENCE_TERMS = /(?:face|portrait|wajah|avatar|foto|photo|pict(?:ure)?|img|image).*(?:id|url|uri|key|path|src|file)/i;
  const FACE_BROAD_TERMS = /\bface\b|\bwajah\b|portrait/i;
  const ALLOWED_HOST_SUFFIXES = [
    "dola.com",
    "ciciai.com",
    "ibytedtos.com",
    "byteoversea.com",
  ];

  function normalizeUrl(value, baseUrl) {
    if (typeof value !== "string" || !/^https?:\/\//i.test(value)) return null;
    try {
      const url = new URL(value.replaceAll("\\u0026", "&"), baseUrl);
      if (!/^https?:$/.test(url.protocol)) return null;
      return url.href;
    } catch {
      return null;
    }
  }

  function extractMediaCandidates(value, baseUrl, output = [], path = "root", depth = 0) {
    if (depth > 10 || output.length >= 250 || value == null) return output;
    if (typeof value === "string") {
      const url = normalizeUrl(value, baseUrl);
      if (url && (MEDIA_EXTENSION.test(url) || MEDIA_KEY.test(path))) {
        output.push({ url, source: path });
      }
      return output;
    }
    if (Array.isArray(value)) {
      value.forEach((item, index) => extractMediaCandidates(item, baseUrl, output, `${path}[${index}]`, depth + 1));
      return output;
    }
    if (typeof value === "object") {
      Object.entries(value).forEach(([key, item]) => {
        extractMediaCandidates(item, baseUrl, output, `${path}.${key}`, depth + 1);
      });
    }
    return output;
  }

  function isAllowedMediaUrl(value) {
    const normalized = normalizeUrl(value);
    if (!normalized) return false;
    const hostname = new URL(normalized).hostname.toLowerCase();
    return ALLOWED_HOST_SUFFIXES.some(suffix => hostname === suffix || hostname.endsWith(`.${suffix}`));
  }

  function candidateScore(candidate) {
    const url = candidate.url.toLowerCase();
    const source = String(candidate.source || "").toLowerCase();
    const quality = String(candidate.quality || "").toLowerCase();
    const hints = `${url} ${source} ${quality}`;
    const pixels = Math.max(0, Number(candidate.width) || 0) * Math.max(0, Number(candidate.height) || 0);
    const bitrate = Math.max(0, Number(candidate.bitrate) || 0);
    let score = Math.min(pixels, 20_000_000);
    score += Math.min(bitrate / 5, 2_000_000);
    if (/2160|4k|uhd/.test(hints)) score += 9_000_000;
    else if (/1440|2k/.test(hints)) score += 6_000_000;
    else if (/1080|full.?hd|\bhd\b/.test(hints)) score += 4_000_000;
    else if (/720/.test(hints)) score += 2_000_000;
    if (/\.mp4(?:$|[?#])/.test(url)) score += 1_000_000;
    if (/download|origin|source|main/.test(source)) score += 300_000;
    if (/watermark|preview|thumb|cover/.test(hints)) score -= 3_000_000;
    if (/\.m3u8(?:$|[?#])/.test(url)) score -= 500_000;
    return score;
  }

  function chooseBestCandidate(candidates) {
    const unique = new Map();
    for (const candidate of candidates || []) {
      const url = normalizeUrl(candidate && candidate.url);
      if (!url) continue;
      const normalized = { ...candidate, url };
      const previous = unique.get(url);
      if (!previous || candidateScore(normalized) > candidateScore(previous)) unique.set(url, normalized);
    }
    return [...unique.values()].sort((a, b) => candidateScore(b) - candidateScore(a))[0] || null;
  }

  function safeFilename(value) {
    const clean = String(value || "dola-video-hd")
      .replace(/[<>:"/\\|?*\u0000-\u001f]/g, "-")
      .replace(/\s+/g, " ")
      .trim()
      .slice(0, 120);
    return `${clean || "dola-video-hd"}.mp4`;
  }

  function isMediaValue(value) {
    return (
      typeof value === "string" &&
      (/^https?:\/\//i.test(value) || /^data:/i.test(value) || value.length > 200)
    ) || (
      typeof File !== "undefined" && value instanceof File
    ) || (
      typeof Blob !== "undefined" && value instanceof Blob
    );
  }

  function holdsMediaReference(item, depth = 0) {
    if (depth > 6) return false;
    if (Array.isArray(item)) return item.some(child => holdsMediaReference(child, depth + 1));
    if (!item || typeof item !== "object") return false;
    return Object.entries(item).some(([key, child]) =>
      FACE_REFERENCE_TERMS.test(key) || isMediaValue(child) || holdsMediaReference(child, depth + 1)
    );
  }

  function stripFaceFilterParams(value, depth = 0) {
    if (depth > 20) return { changed: false, value };
    if (Array.isArray(value)) {
      let changed = false;
      const next = value.map(item => {
        const result = stripFaceFilterParams(item, depth + 1);
        changed = changed || result.changed;
        return result.value;
      });
      return changed ? { changed, value: next } : { changed: false, value };
    }
    if (value && typeof value === "object") {
      let changed = false;
      const next = {};
      for (const [key, item] of Object.entries(value)) {
        if (FACE_REFERENCE_TERMS.test(key)) {
          const result = stripFaceFilterParams(item, depth + 1);
          if (result.changed) changed = true;
          next[key] = result.value;
          continue;
        }
        if (FACE_FILTER_TERMS.test(key) && !isMediaValue(item)) {
          changed = true;
          continue;
        }
        if (FACE_BROAD_TERMS.test(key) && !isMediaValue(item) && !holdsMediaReference(item)) {
          changed = true;
          continue;
        }
        const result = stripFaceFilterParams(item, depth + 1);
        if (result.changed) changed = true;
        next[key] = result.value;
      }
      return changed ? { changed, value: next } : { changed: false, value };
    }
    return { changed: false, value };
  }

  function rewriteBody(body, contentType) {
    if (typeof body === "string") {
      const trimmed = body.trim();
      if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
        try {
          const parsed = JSON.parse(trimmed);
          const result = stripFaceFilterParams(parsed);
          return result.changed ? JSON.stringify(result.value) : body;
        } catch {
          return body;
        }
      }
      if (/[?&][^=]+=/.test(trimmed)) {
        try {
          const source = new URLSearchParams(trimmed);
          const next = new URLSearchParams();
          let changed = false;
          for (const [key, value] of source) {
            const strip = FACE_FILTER_TERMS.test(key) ||
              (FACE_BROAD_TERMS.test(key) && !isMediaValue(value));
            if (strip) {
              changed = true;
              continue;
            }
            next.append(key, value);
          }
          return changed ? next.toString() : body;
        } catch {
          return body;
        }
      }
      return body;
    }
    if (typeof FormData !== "undefined" && typeof body?.entries === "function") {
      try {
        const next = new FormData();
        let changed = false;
        for (const [key, value] of body.entries()) {
          const strip = !isMediaValue(value) && (
            FACE_FILTER_TERMS.test(key) || (FACE_BROAD_TERMS.test(key) && !isMediaValue(value))
          );
          if (strip) {
            changed = true;
            continue;
          }
          next.append(key, value);
        }
        return changed ? next : body;
      } catch {
        return body;
      }
    }
    return body;
  }

  function isDolaHostUrl(value, baseUrl) {
    if (typeof value !== "string") return false;
    try {
      return /(^|\.)dola\.com$/i.test(new URL(value.replaceAll("\\u0026", "&"), baseUrl).hostname);
    } catch {
      return false;
    }
  }

  return {
    candidateScore,
    chooseBestCandidate,
    extractMediaCandidates,
    isAllowedMediaUrl,
    isDolaHostUrl,
    normalizeUrl,
    rewriteBody,
    safeFilename,
    stripFaceFilterParams,
  };
});
