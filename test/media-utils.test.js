const test = require("node:test");
const assert = require("node:assert/strict");
const {
  chooseBestCandidate,
  extractMediaCandidates,
  isAllowedMediaUrl,
  normalizeUrl,
  safeFilename,
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
