import { chromium } from 'playwright-core';
const browser = await chromium.launch({headless:true,executablePath:'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'});
const page = await browser.newPage({viewport:{width:390,height:844},deviceScaleFactor:1});
await page.goto('http://127.0.0.1:5173/',{waitUntil:'networkidle'});
await page.screenshot({path:'../docs/screenshots/pwa-mobile.png'});
await page.setViewportSize({width:1440,height:900});
await page.reload({waitUntil:'networkidle'});
await page.screenshot({path:'../docs/screenshots/pwa-desktop.png'});
await browser.close();
