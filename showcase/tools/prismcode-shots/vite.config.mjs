// Builds PrismCode's renderer as a plain web page so it can be screenshotted without Electron.
// The real preload bridge is replaced by mock-bridge.js (demo data, scripted agents).
import { resolve } from 'node:path';
const PC = process.env.PRISMCODE || '/home/user/prismcode';
const { default: react } = await import(`${PC}/node_modules/@vitejs/plugin-react/dist/index.js`);
export default {
  root: `${PC}/src/renderer`,
  base: './',
  logLevel: 'warn',
  resolve: { alias: { '@renderer': `${PC}/src/renderer/src`, '@shared': `${PC}/src/shared` } },
  plugins: [react(), {
    name: 'mock-bridge',
    transformIndexHtml: (html) => html.replace('</head>', '  <link rel="preload" href="./fonts/JetBrainsMono.woff2" as="font" type="font/woff2" crossorigin>\n    <link rel="stylesheet" href="./shot-overrides.css">\n  </head>').replace('<div id="root"></div>', '<div id="root"></div>\n    <script src="./mock-bridge.js"></script>'),
  }],
  build: { outDir: resolve(import.meta.dirname, 'dist'), emptyOutDir: true, chunkSizeWarningLimit: 10000 },
};
