const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const outDir = path.join(root, 'build');
const watch = process.argv.includes('--watch');

const copyStaticFiles = () => {
  const packageJson = JSON.parse(
    fs.readFileSync(path.join(root, 'package.json'), 'utf8')
  );
  const manifest = JSON.parse(
    fs.readFileSync(path.join(root, 'src', 'manifest.json'), 'utf8')
  );
  const images = ['icon34.png', 'icon34-inactive.png', 'icon128.png'];

  fs.mkdirSync(outDir, { recursive: true });
  fs.writeFileSync(
    path.join(outDir, 'manifest.json'),
    JSON.stringify({
      ...manifest,
      description: packageJson.description,
      version: packageJson.version,
    })
  );
  fs.copyFileSync(
    path.join(root, 'src', 'pages', 'Content', 'content.styles.css'),
    path.join(outDir, 'content.styles.css')
  );
  images.forEach((name) => {
    fs.copyFileSync(
      path.join(root, 'src', 'assets', 'img', name),
      path.join(outDir, name)
    );
  });
};

const assetFileNames = (asset) =>
  asset.name && asset.name.endsWith('.css') ? 'popup.css' : '[name][extname]';

const run = async () => {
  const { build } = await import('vite');
  fs.rmSync(outDir, { recursive: true, force: true });
  copyStaticFiles();

  const shared = {
    configFile: false,
    publicDir: false,
    logLevel: 'info',
    resolve: {
      alias: {
        react: 'preact/compat',
        'react-dom': 'preact/compat',
      },
    },
    build: {
      outDir,
      emptyOutDir: false,
      minify: 'esbuild',
      sourcemap: false,
      target: 'chrome109',
      watch: watch ? {} : null,
    },
  };

  await build({
    ...shared,
    root: path.join(root, 'src', 'pages', 'Popup'),
    base: './',
    build: {
      ...shared.build,
      rollupOptions: {
        input: path.join(root, 'src', 'pages', 'Popup', 'popup.html'),
        output: {
          entryFileNames: 'popup.bundle.js',
          chunkFileNames: '[name].js',
          assetFileNames,
        },
      },
    },
  });

  const entries = {
    background: path.join(root, 'src', 'pages', 'Background', 'index.ts'),
    contentScript: path.join(root, 'src', 'pages', 'Content', 'index.ts'),
    netflix: path.join(root, 'src', 'pages', 'Netflix', 'index.ts'),
  };

  for (const [name, entry] of Object.entries(entries)) {
    await build({
      ...shared,
      root,
      build: {
        ...shared.build,
        lib: {
          entry,
          name: `RedMedia${name}`,
          formats: ['iife'],
          fileName: () => `${name}.bundle.js`,
        },
        rollupOptions: {
          output: {
            assetFileNames,
          },
        },
      },
    });
  }
};

run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
