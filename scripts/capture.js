const fs = require('mz/fs');
const compareImages = require('resemblejs/compareImages');
const puppeteer = require('puppeteer');

const localHost = 'http://localhost:4000';
const productionHost = 'https://jakewharton.com';

async function loadAndCapture(page, url) {
  await page.goto(url, {
    waitUntil: 'networkidle0'
  });
  return await page.screenshot({
    fullPage: true,
    omitBackground: true
  })
}

async function crawlCaptureAndCompare(path, browser, width, height) {
  const localPage = await browser.newPage();
  const productionPage = await browser.newPage();

  const viewport = {
    width: width,
    height: height
  };
  await localPage.setViewport(viewport);
  await productionPage.setViewport(viewport);

  const dir = `capture/${width}x${height}`
  await fs.mkdir(dir);

  const seen = new Set([]);
  let urls = ['/'];
  while (urls.length > 0) {
    const url = urls.shift();
    if (seen.has(url)) {
      continue;
    }
    seen.add(url);
    console.log(`Capturing ${url}`)

    const productionImage = loadAndCapture(productionPage, productionHost + url);
    const localImage = loadAndCapture(localPage, localHost + url);

    const diffResult = await compareImages(await localImage, await productionImage, {
      output: {
        errorType: 'movement',
        transparency: 0.7,
        largeImageThreshold: 0,
        outputDiff: true,
        ignoreAreasColoredWith: {
          r: 0,
          g: 0,
          b: 0,
          a: 0
        }
      },
      ignore: 'antialiasing'
    });

    if (diffResult.misMatchPercentage > 0.1) {
      console.log(`  ${diffResult.misMatchPercentage}% difference!`);
      const name = url.replace(/\//g, '') || 'index';
      await Promise.all([
        fs.writeFile(`${dir}/${name}_local.png`, await localImage),
        fs.writeFile(`${dir}/${name}_production.png`, await productionImage),
        fs.writeFile(`${dir}/${name}_diff.png`, diffResult.getBuffer())
      ]);
    } else {
      console.log(`  identical (${diffResult.misMatchPercentage}% difference)`);
    }

    const links = (await localPage.$$eval('a', nodes => nodes.map(n => n.href)))
        .filter(link => link.startsWith(localHost))
        .map(link => link.substring(localHost.length).replace(/#.*$/g, ''))
        .filter(link => !link.startsWith('/static/'));
    const uniqueLinks = [...new Set(links)]; // dedupe
    console.log(`  found links ${uniqueLinks}`)
    urls.push(...uniqueLinks);
  }
}

(async () => {
  await fs.mkdir('capture');

  const browser = await puppeteer.launch();
  await crawlCaptureAndCompare('compare', browser, 1200, 800)
  await browser.close();
})();
