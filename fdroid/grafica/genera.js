// Rigenera le immagini PNG del repository F-Droid dalle sorgenti in questa cartella.
// Uso: node fdroid/grafica/genera.js (serve Playwright con Chromium).
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const qui = __dirname;
const app = path.join(qui, '..', 'metadata', 'dev.antidroidvirus', 'it', 'images');

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  for (const [sorgente, larghezza, altezza, destinazioni] of [
    ['icona.svg', 512, 512, [path.join(app, 'icon.png'), path.join(qui, '..', 'icon.png')]],
    ['banner.html', 1024, 500, [path.join(app, 'featureGraphic.png')]],
  ]) {
    await page.setViewportSize({ width: larghezza, height: altezza });
    if (sorgente.endsWith('.svg')) {
      const svg = fs.readFileSync(path.join(qui, sorgente), 'utf8');
      await page.setContent(`<body style="margin:0;background:transparent">${svg}</body>`);
    } else {
      await page.goto('file://' + path.join(qui, sorgente));
    }
    for (const destinazione of destinazioni) {
      await page.screenshot({ path: destinazione, omitBackground: true });
    }
  }
  await browser.close();
})();
