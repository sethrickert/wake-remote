/**
 * Capture README screenshots of the built PWA.
 *
 * Serve dist/ first, ideally through the Wake Remote server itself so the app is at the
 * origin root exactly as it ships:
 *
 *   npm run build
 *   PYTHONPATH=../server WAKE_STATIC_DIR=$PWD/dist WAKE_PORT=8098 \
 *     WAKE_DATA_DIR=/tmp/wake python -m wake_remote.app &
 *   node qa-screenshot.mjs [baseUrl] [outDir]
 *
 * Uses playwright-core against the system Edge install, so no browser download.
 */
import {chromium} from 'playwright-core';

const base = process.argv[2] || 'http://127.0.0.1:8098/';
const out = process.argv[3] || '../docs/screenshots';
const edge = process.env.EDGE_PATH || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';

const browser = await chromium.launch({headless: true, executablePath: edge});
// deviceScaleFactor 2 so the images are not soft in the README on a retina display.
const context = await browser.newContext({viewport: {width: 430, height: 932}, deviceScaleFactor: 2});
const page = await context.newPage();

const problems = [];
page.on('console', m => { if (m.type() === 'error') problems.push(m.text()); });
page.on('pageerror', e => problems.push(String(e)));

await page.goto(base, {waitUntil: 'networkidle'});
await page.waitForTimeout(1200);
await page.screenshot({path: `${out}/pwa-mobile.png`});

await page.getByRole('button', {name: 'Set up secure key'}).click();
await page.waitForTimeout(800);
await page.screenshot({path: `${out}/pwa-enroll.png`});

await page.setViewportSize({width: 1440, height: 900});
await page.goto(base, {waitUntil: 'networkidle'});
await page.waitForTimeout(1000);
await page.screenshot({path: `${out}/pwa-desktop.png`});

console.log('title           :', await page.title());
console.log('manifest        :', await page.getAttribute('link[rel=manifest]', 'href'));
console.log('apple-touch-icon:', await page.getAttribute('link[rel=apple-touch-icon]', 'href'));
console.log('service worker  :', await page.evaluate(() => 'serviceWorker' in navigator));
console.log('console errors  :', problems.length ? problems : 'none');

await browser.close();
