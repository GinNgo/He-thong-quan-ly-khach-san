/* Raster Mermaid SVG assets with Chromium so foreignObject labels are preserved. */
const fs = require('fs');
const path = require('path');
const { chromium } = require(path.join(__dirname, '..', '..', 'frontend', 'node_modules', 'playwright'));

const root = path.join(__dirname, '..', '..');
const sourceDir = path.join(root, 'docs', 'thesis-assets', 'diagrams');
const outputDir = path.join(sourceDir, 'png');

function viewBoxSize(svg) {
  const match = svg.match(/viewBox="[^\s]+\s+[^\s]+\s+([\d.]+)\s+([\d.]+)"/);
  return match ? { width: Number(match[1]), height: Number(match[2]) } : { width: 1600, height: 900 };
}

async function main() {
  fs.mkdirSync(outputDir, { recursive: true });
  const browser = await chromium.launch({ headless: true });
  try {
    const page = await browser.newPage({ deviceScaleFactor: 2 });
    for (const name of fs.readdirSync(sourceDir).filter((file) => file.endsWith('.svg')).sort()) {
      const svg = fs.readFileSync(path.join(sourceDir, name), 'utf8');
      const size = viewBoxSize(svg);
      await page.setContent(`<!doctype html><html><head><style>html,body{margin:0;padding:0;background:transparent}svg{display:block!important;width:${size.width}px!important;height:${size.height}px!important;max-width:none!important}</style></head><body>${svg}</body></html>`);
      await page.locator('svg').first().screenshot({
        path: path.join(outputDir, `${path.basename(name, '.svg')}.png`),
        animations: 'disabled',
      });
      process.stdout.write(`Rendered ${name} -> png/${path.basename(name, '.svg')}.png\n`);
    }
  } finally {
    await browser.close();
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
