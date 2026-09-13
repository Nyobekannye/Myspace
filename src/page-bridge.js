(function () {
  "use strict";

  const EVENT_NAME = "axiom-dola-media";
  const RELEVANT_PATH = /(?:get_play_info|get_video_share_info|get_without_watermark|\/video\/|video_gen)/i;
  const MEDIA_URL = /^https?:\/\//i;
  const MEDIA_HINT = /(?:play|download|video|media|main|backup|origin|source).*(?:url|uri)|(?:url|uri)/i;

  function emit(candidate) {
    if (!candidate || !MEDIA_URL.test(candidate.url || "")) return;
    window.dispatchEvent(new CustomEvent(EVENT_NAME, { detail: candidate }));
  }

  function numericValue(object, keys) {
    for (const key of keys) {
      const value = Number(object?.[key]);
      if (Number.isFinite(value) && value > 0) return value;
    }
    return 0;
  }

  function inspect(value, requestUrl, path = "root", depth = 0, metadata = {}) {
    if (depth > 10 || value == null) return;
    if (typeof value === "string") {
      if (MEDIA_URL.test(value) && (/\.(?:mp4|webm|mov|m3u8)(?:$|[?#])/i.test(value) || MEDIA_HINT.test(path))) {
        emit({
          url: value.replaceAll("\\u0026", "&"),
          source: `${requestUrl} → ${path}`,
          ...metadata,
        });
      }
      return;
    }
    if (Array.isArray(value)) {
      value.forEach((item, index) => inspect(item, requestUrl, `${path}[${index}]`, depth + 1, metadata));
    } else if (typeof value === "object") {
      const localMetadata = {
        width: numericValue(value, ["width", "video_width", "play_width"]),
        height: numericValue(value, ["height", "video_height", "play_height"]),
        bitrate: numericValue(value, ["bitrate", "bit_rate", "video_bitrate"]),
        quality: String(value.quality || value.definition || value.format || ""),
      };
      const combined = {
        ...metadata,
        ...Object.fromEntries(Object.entries(localMetadata).filter(([, item]) => item)),
      };
      Object.entries(value).forEach(([key, item]) => inspect(item, requestUrl, `${path}.${key}`, depth + 1, combined));
    }
  }

  const nativeFetch = window.fetch;
  window.fetch = async function (...args) {
    const response = await nativeFetch.apply(this, args);
    const requestUrl = String(response.url || args[0] || "");
    const contentType = response.headers.get("content-type") || "";
    if (RELEVANT_PATH.test(requestUrl) && /json/i.test(contentType)) {
      response.clone().json().then(data => inspect(data, requestUrl)).catch(() => {});
    }
    return response;
  };

  const nativeOpen = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function (method, url, ...rest) {
    this.__axiomDolaUrl = String(url);
    this.addEventListener("load", function () {
      if (!RELEVANT_PATH.test(this.__axiomDolaUrl || "")) return;
      try {
        const data = typeof this.response === "object" && this.response !== null
          ? this.response
          : JSON.parse(this.responseText);
        inspect(data, this.responseURL || this.__axiomDolaUrl);
      } catch {}
    }, { once: true });
    return nativeOpen.call(this, method, url, ...rest);
  };
})();
