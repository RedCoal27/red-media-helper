const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const root = path.resolve(__dirname, '..');

test('package and extension manifest versions stay synchronized', () => {
  const pkg = require('../package.json');
  const manifest = require('../src/manifest.json');
  const helperSource = fs.readFileSync(path.join(root, 'utils/HelperLauncher.cs'), 'utf8');
  const serverSource = fs.readFileSync(path.join(root, 'utils/ytdlp-server.js'), 'utf8');
  assert.equal(manifest.version, pkg.version);
  assert.equal(manifest.manifest_version, 3);
  assert.match(helperSource, new RegExp(`AppVersion = "${pkg.version.replaceAll('.', '\\.')}"`));
  assert.match(serverSource, new RegExp(`HELPER_VERSION[^\n]+${pkg.version.replaceAll('.', '\\.')}`));
});

test('release workflow publishes every optimized artifact', () => {
  const workflow = fs.readFileSync(path.join(root, '.github/workflows/release.yml'), 'utf8');
  for (const name of [
    'red-media-extension-unpacked.zip',
    'Red-Media-Mobile-arm64.apk',
    'Red-Media-Mobile-arm32.apk',
    'Red-Media-Mobile-x86_64.apk',
    'Red-Media-Mobile-universal.apk',
  ]) {
    assert.match(workflow + fs.readFileSync(path.join(root, 'utils/package-release.ps1'), 'utf8'), new RegExp(name.replaceAll('.', '\\.')));
  }
});
