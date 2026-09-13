const test = require("node:test");
const assert = require("node:assert/strict");
const {
  chooseBestCandidate,
  extractMediaCandidates,
  isAllowedMediaUrl,
  isDolaHostUrl,
  normalizeUrl,
  rewriteBody,
  safeFilename,
  stripFaceFilterParams,
} = require("../src/media-utils.js");

test("extracts nested media URLs from play-info responses", () => {
  const response = {
    data: {
      play_info: {
        main_url: "https://cdn.example/video-1080.mp4?token=abc\\u0026x=1",
        cover_url: "https://cdn.example/cover.jpg",
      },
    },
  };
  const candidates = extractMediaCandidates(response, "https://www.dola.com");
  assert.equal(candidates.length, 2);
  assert.match(candidates[0].url, /video-1080\.mp4\?token=abc&x=1/);
});

test("prefers high-resolution MP4 over preview and HLS candidates", () => {
  const best = chooseBestCandidate([
    { url: "https://cdn.example/preview-720.mp4", width: 1280, height: 720, source: "preview_url" },
    { url: "https://cdn.example/master.m3u8", source: "play_url" },
    { url: "https://cdn.example/original-1080.mp4", width: 1920, height: 1080, source: "main_download_url" },
  ]);
  assert.equal(best.url, "https://cdn.example/original-1080.mp4");
});

test("uses numeric rendition metadata when signed URLs are opaque", () => {
  const best = chooseBestCandidate([
    { url: "https://cdn.example/v/opaque-a?token=1", width: 1280, height: 720, bitrate: 2_000_000 },
    { url: "https://cdn.example/v/opaque-b?token=2", width: 1920, height: 1080, bitrate: 5_000_000 },
  ]);
  assert.match(best.url, /opaque-b/);
});

test("rejects unsafe URLs and sanitizes filenames", () => {
  assert.equal(normalizeUrl("javascript:alert(1)"), null);
  assert.equal(isAllowedMediaUrl("https://video.ciciai.com/file.mp4"), true);
  assert.equal(isAllowedMediaUrl("https://ciciai.com.attacker.example/file.mp4"), false);
  assert.equal(safeFilename('bad:/name*?'), "bad--name--.mp4");
});

test("strips face-filter parameters from request bodies", () => {
  const { changed, value } = stripFaceFilterParams({
    prompt: "halo",
    face_filter: true,
    portrait: { mode: "on" },
    beauty_level: 3,
    retouch: "1",
  });
  assert.equal(changed, true);
  assert.deepEqual(value, { prompt: "halo" });
});

test("strips face params inside nested arrays but keeps unrelated keys", () => {
  const { changed, value } = stripFaceFilterParams({
    items: [{ id: 1 }, { id: 2, face_swap: true }],
    profile: "https://cdn.example/pic.jpg",
    config: { bitrate: "1080p" },
  });
  assert.equal(changed, true);
  assert.deepEqual(value, {
    items: [{ id: 1 }, { id: 2 }],
    profile: "https://cdn.example/pic.jpg",
    config: { bitrate: "1080p" },
  });
});

test("reports no change for untouched bodies and never mutates input", () => {
  const original = { prompt: "halo", config: { bitrate: "1080p" } };
  const result = stripFaceFilterParams(original);
  assert.equal(result.changed, false);
  assert.equal(result.value, original);

  const withFace = { prompt: "halo", config: { face_filter: true } };
  const stripped = stripFaceFilterParams(withFace);
  assert.equal(stripped.changed, true);
  assert.deepEqual(stripped.value, { prompt: "halo", config: {} });
  assert.deepEqual(withFace, { prompt: "halo", config: { face_filter: true } });
});

test("detects dola.com API hosts for request interception", () => {
  assert.equal(isDolaHostUrl("/samantha/video/do_generate", "https://www.dola.com/generate"), true);
  assert.equal(isDolaHostUrl("https://cdn.dola.com/api/x", "https://www.dola.com/"), true);
  assert.equal(isDolaHostUrl("https://evil.example/x", "https://www.dola.com/"), false);
});

test("keeps photo references while stripping filter settings", () => {
  const { changed, value } = stripFaceFilterParams({
    prompt: "halo",
    face_url: "https://cdn.dola.com/u/photo.jpg",
    face_id: "abc123",
    portrait: { url: "data:image/jpeg;base64,/9j/4AAQ" },
    face: true,
  });
  assert.equal(changed, true);
  assert.deepEqual(value, {
    prompt: "halo",
    face_url: "https://cdn.dola.com/u/photo.jpg",
    face_id: "abc123",
    portrait: { url: "data:image/jpeg;base64,/9j/4AAQ" },
  });
});

test("rewrites JSON bodies but leaves clean bodies untouched", () => {
  const body = JSON.stringify({ prompt: "x", face_filter: 1, portrait: { mode: "on" } });
  assert.equal(rewriteBody(body, "application/json"), JSON.stringify({ prompt: "x" }));
  const plain = JSON.stringify({ prompt: "x", config: { a: 1 } });
  assert.equal(rewriteBody(plain, "application/json"), plain);
});

test("rewrites urlencoded bodies", () => {
  assert.equal(rewriteBody("prompt=x&face_filter=1&beauty=high", "application/x-www-form-urlencoded"), "prompt=x");
});

test("rewrites multipart bodies but keeps the uploaded photo file", () => {
  const body = new FormData();
  body.append("photo", new File(["fakeimage"], "wajah.jpg", { type: "image/jpeg" }));
  body.append("prompt", "halo");
  body.append("beauty_level", "3");
  const out = rewriteBody(body, "multipart/form-data");
  assert.notEqual(out, body);
  assert.equal(out.has("photo"), true);
  assert.equal(out.has("prompt"), true);
  assert.equal(out.has("beauty_level"), false);
});
