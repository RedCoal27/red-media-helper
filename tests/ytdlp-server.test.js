const assert = require('node:assert/strict');
const test = require('node:test');
const {
  buildExtractedMediaOptions,
  buildOptions,
  getAudioTracks,
  parseYtDlpLine,
  selectRequestedFormat,
} = require('../utils/ytdlp-server');

test('detects HLS and direct media candidates', () => {
  const options = buildExtractedMediaOptions(
    ['https://cdn.example/master.m3u8', 'https://cdn.example/video.mp4'],
    'https://example.test/watch'
  );
  assert.equal(options[0].formatId, 'bestvideo+bestaudio/best');
  assert.equal(options[1].formatId, 'direct');
  assert.equal(options[0].referer, 'https://example.test/watch');
});

test('builds selectors for chosen audio languages', () => {
  assert.equal(
    selectRequestedFormat('fallback', 'bv*[height<=1080]', ['en', 'fr'], true),
    'bv*[height<=1080]+en+fr/bv*[height<=1080]+bestaudio/best'
  );
  assert.match(selectRequestedFormat('fallback', 'bv*', [], true), /\+bestaudio/);
});

test('keeps one high quality audio format per language', () => {
  const tracks = getAudioTracks([
    { format_id: 'en-low', acodec: 'aac', vcodec: 'none', language: 'en', abr: 64 },
    { format_id: 'en-best', acodec: 'aac', vcodec: 'none', language: 'en', abr: 128 },
    { format_id: 'fr', acodec: 'aac', vcodec: 'none', language: 'fr', abr: 96 },
  ]);
  assert.deepEqual(tracks.map((track) => track.formatId).sort(), ['en-best', 'fr']);
});

test('returns a concise format list with audio-only support', () => {
  const formats = [2160, 1440, 1080, 720, 480, 360].map((height) => ({
    format_id: `v${height}`,
    height,
    ext: 'mp4',
    vcodec: 'avc1',
    acodec: 'none',
  }));
  formats.push({ format_id: 'en', ext: 'm4a', vcodec: 'none', acodec: 'aac', language: 'en', abr: 128 });
  const options = buildOptions({ formats }, 'https://youtube.com/watch?v=test');
  assert.equal(options.filter((option) => !option.audioOnly).length, 5);
  assert.equal(options.at(-1).audioOnly, true);
  assert.ok(options[0].videoSelector);
});

test('reports per-file and overall multi-track progress', () => {
  const job = { part: 1, partsTotal: 3, seenDestinations: new Set() };
  parseYtDlpLine('[download] Destination: video.webm', job);
  const first = parseYtDlpLine('[download] 50.0% of 10MiB at 2MiB/s', job);
  parseYtDlpLine('[download] Destination: audio-en.m4a', job);
  const second = parseYtDlpLine('[download] 50.0% of 2MiB at 1MiB/s', job);
  const merge = parseYtDlpLine('[Merger] Merging formats into "output.mkv"', job);
  assert.equal(first.stageLabel, 'Downloading file 1/3');
  assert.ok(second.percent > first.percent);
  assert.equal(merge.stage, 'processing');
  assert.equal(merge.percent, 95);
});
