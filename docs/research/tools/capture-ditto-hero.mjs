import { chromium } from 'playwright';
import { spawn } from 'node:child_process';
import fs from 'fs';
import path from 'path';

const OUT = '/Users/null/IdeaProjects/AI/tryditto-cn/docs/research/assets/ditto-hero';
const FFMPEG = `${process.env.HOME}/Library/Caches/ms-playwright/ffmpeg_mac12_special-1010/ffmpeg-mac`;

const INIT = `(() => {
  window.__heroSamples = [];
  let lastSig = '';
  let lastStrip = 0;
  const tick = () => {
    const t = performance.now();
    const hero = document.querySelector('.austin-hero');
    if (!hero) {
      window.__heroSamples.push({ t: Math.round(t), missing: 1 });
    } else {
      const chs = [...hero.querySelectorAll('h1 .ch')];
      const phone = hero.querySelector('.phone');
      const orbit = hero.querySelector('.dove-orbit');
      const dove = hero.querySelector('.dove');
      const thread = hero.querySelector('.thread');
      const ocs = orbit ? getComputedStyle(orbit) : null;
      const dcs = dove ? getComputedStyle(dove) : null;
      const pcs = phone ? getComputedStyle(phone) : null;
      const sig = thread ? [...thread.children].map(el => el.className + ':' + (el.innerText || '').replace(/\\s+/g, ' ').trim().slice(0, 90)).join('|') : '';
      const sample = {
        t: Math.round(t),
        op: chs.map(el => Math.round(parseFloat(getComputedStyle(el).opacity) * 100)),
        up: phone && phone.classList.contains('up') ? 1 : 0,
        pt: pcs ? pcs.transform : '',
        po: pcs ? Math.round(parseFloat(pcs.opacity) * 100) : null,
        so: hero.querySelector('.phone-slot') ? Math.round(parseFloat(getComputedStyle(hero.querySelector('.phone-slot')).opacity) * 100) : null,
        frame: dcs ? dcs.getPropertyValue('--frame').trim() : '',
        bgx: dcs ? dcs.backgroundPositionX : '',
        x: ocs ? ocs.getPropertyValue('--x').trim() : '',
        y: ocs ? ocs.getPropertyValue('--y').trim() : '',
        rot: ocs ? ocs.getPropertyValue('--r').trim() : '',
        sc: ocs ? ocs.getPropertyValue('--s').trim() : '',
        behind: orbit && orbit.classList.contains('behind') ? 1 : 0,
      };
      if (sig !== lastSig) {
        sample.sig = sig;
        lastSig = sig;
      }
      if (dove) {
        const r = dove.getBoundingClientRect();
        sample.box = [Math.round(r.x), Math.round(r.y), Math.round(r.width), Math.round(r.height)];
      }
      if (t - lastStrip > 480) {
        lastStrip = t;
        sample.strips = ['mountains', 'campus', 'river'].map(cls => {
          const el = hero.querySelector('.strip.' + cls);
          if (!el) return { cls, missing: 1 };
          const anims = typeof el.getAnimations === 'function' ? el.getAnimations() : [];
          const pan = anims.find(a => String(a.animationName || '').includes('pan')) || null;
          const img = el.querySelector('img');
          const rect = el.getBoundingClientRect();
          return {
            cls,
            w: Math.round(rect.width),
            h: Math.round(rect.height),
            top: Math.round(rect.top),
            z: getComputedStyle(el).zIndex,
            tiles: el.querySelectorAll('.tile').length,
            src: img ? (img.currentSrc || img.src) : '',
            nw: img ? img.naturalWidth : 0,
            nh: img ? img.naturalHeight : 0,
            iw: img ? Math.round(img.getBoundingClientRect().width) : 0,
            dur: getComputedStyle(el).animationDuration,
            name: getComputedStyle(el).animationName,
            rate: pan ? Number(pan.playbackRate.toFixed(3)) : null,
            ct: pan && pan.currentTime != null ? Math.round(Number(pan.currentTime)) : null,
            play: getComputedStyle(el).animationPlayState,
          };
        });
        const h1 = hero.querySelector('h1');
        if (h1 && !window.__fontOnce) {
          window.__fontOnce = 1;
          const cs = getComputedStyle(h1);
          const letters = chs.map(el => {
            const r = el.getBoundingClientRect();
            const st = getComputedStyle(el);
            return { ch: el.textContent, x: Math.round(r.x), w: Math.round(r.width * 10) / 10, ls: st.letterSpacing, op: st.opacity };
          });
          sample.font = { family: cs.fontFamily, size: cs.fontSize, ls: cs.letterSpacing, lh: cs.lineHeight, weight: cs.fontWeight, letters };
        }
      }
      window.__heroSamples.push(sample);
    }
    if (performance.now() < 20500) setTimeout(tick, 50);
  };
  tick();
})();`;

function shotTargets(full) {
  if (!full) return [0, 800, 1400, 2000, 3000, 4500];
  const times = [];
  for (let t = 0; t <= 3000; t += 100) times.push(t);
  for (let t = 3250; t <= 10000; t += 250) times.push(t);
  for (let t = 11000; t <= 20000; t += 1000) times.push(t);
  return times;
}

function runFfmpeg(args) {
  return new Promise((resolve, reject) => {
    const child = spawn(FFMPEG, args, { stdio: ['ignore', 'ignore', 'pipe'] });
    let err = '';
    child.stderr.on('data', d => { err += d.toString(); });
    child.on('close', code => code === 0 ? resolve() : reject(new Error(err.slice(-800))));
  });
}

async function extractFrames(videoPath, offsetSec, times, prefix) {
  const fps = 25;
  const dir = path.join('/tmp', `ditto-frames-${prefix}`);
  fs.rmSync(dir, { recursive: true, force: true });
  fs.mkdirSync(dir, { recursive: true });
  await runFfmpeg(['-y', '-i', videoPath, '-start_number', '0', path.join(dir, 'f_%04d.png')]);
  const rows = [];
  for (const t of times) {
    const at = Math.max(0, offsetSec + t / 1000);
    const n = Math.round(at * fps);
    const src = path.join(dir, `f_${String(n).padStart(4, '0')}.png`);
    const file = path.join(OUT, `${prefix}-t${t}.png`);
    if (fs.existsSync(src)) fs.copyFileSync(src, file);
    rows.push({
      t,
      file: path.basename(file),
      at: Number(at.toFixed(3)),
      frame: n,
      exists: fs.existsSync(file),
      bytes: fs.existsSync(file) ? fs.statSync(file).size : 0,
    });
  }
  fs.rmSync(dir, { recursive: true, force: true });
  return rows;
}

async function capture(spec) {
  const browser = await chromium.launch({
    channel: 'chrome',
    headless: true,
    args: ['--disable-dev-shm-usage'],
  });
  const context = await browser.newContext({
    viewport: spec.viewport,
    deviceScaleFactor: 1,
    reducedMotion: spec.reduced ? 'reduce' : 'no-preference',
    serviceWorkers: 'block',
    locale: 'en-US',
    recordVideo: { dir: path.join(OUT, '_video_raw', spec.name), size: spec.viewport },
  });
  const page = await context.newPage();
  const cdp = await context.newCDPSession(page);
  await cdp.send('Network.setCacheDisabled', { cacheDisabled: true });
  const net = [];
  page.on('response', res => {
    const url = res.url().split('#')[0];
    net.push({
      wall: Date.now(),
      status: res.status(),
      type: res.request().resourceType(),
      url,
    });
  });
  await page.addInitScript(INIT.replace('performance.now() < 20500', `performance.now() < ${spec.durationMs + 500}`));
  const pageCreated = Date.now();
  const beforeGoto = Date.now();
  await page.goto('https://tryditto.com/', { waitUntil: 'commit', timeout: 45000 });
  const timeOrigin = await page.evaluate(() => performance.timeOrigin);
  await page.waitForFunction((ms) => performance.now() > ms, spec.durationMs, { timeout: spec.durationMs + 20000 });
  const endPerf = await page.evaluate(() => performance.now());
  const endWall = Date.now();
  const meta = await page.evaluate(() => {
    const n = performance.getEntriesByType('navigation')[0];
    const hero = document.querySelector('.austin-hero');
    const h1 = hero?.querySelector('h1');
    const cs = h1 ? getComputedStyle(h1) : null;
    const fonts = [...document.fonts].filter(f => /spencer|bitter/i.test(f.family)).map(f => ({ family: f.family, status: f.status, weight: f.weight }));
    return {
      reduced: matchMedia('(prefers-reduced-motion: reduce)').matches,
      inner: [innerWidth, innerHeight],
      dpr: devicePixelRatio,
      ua: navigator.userAgent,
      heroClass: hero?.className || '',
      font: cs ? { family: cs.fontFamily, size: cs.fontSize, ls: cs.letterSpacing, lh: cs.lineHeight, weight: cs.fontWeight } : null,
      fonts,
      letters: h1 ? [...h1.querySelectorAll('.ch')].map(el => {
        const r = el.getBoundingClientRect();
        const st = getComputedStyle(el);
        return { ch: el.textContent, x: Math.round(r.x * 10) / 10, w: Math.round(r.width * 10) / 10, ls: st.letterSpacing };
      }) : [],
      nav: n && {
        type: n.type,
        duration: n.duration,
        responseStart: n.responseStart,
        responseEnd: n.responseEnd,
        domContentLoaded: n.domContentLoadedEventEnd,
        load: n.loadEventEnd,
        transferSize: n.transferSize,
        encodedBodySize: n.encodedBodySize,
        decodedBodySize: n.decodedBodySize,
      },
    };
  });
  const samples = await page.evaluate(() => window.__heroSamples);
  const video = page.video();
  await context.close();
  const rawVideo = await video.path();
  const destVideo = path.join(OUT, `${spec.name}.webm`);
  fs.copyFileSync(rawVideo, destVideo);
  await browser.close();
  const offsetMs = timeOrigin - beforeGoto;
  const frames = await extractFrames(destVideo, offsetMs / 1000, shotTargets(!spec.reduced && spec.durationMs >= 20000), spec.name);
  const payload = {
    name: spec.name,
    viewport: spec.viewport,
    reduced: !!spec.reduced,
    pageCreated,
    beforeGoto,
    timeOrigin,
    offsetMs,
    endPerf,
    endWall,
    video: path.basename(destVideo),
    videoBytes: fs.statSync(destVideo).size,
    meta,
    frames,
    net: net.map(row => ({ ...row, perf: Math.round(row.wall - timeOrigin) })),
    samples,
  };
  fs.writeFileSync(path.join(OUT, `${spec.name}-samples.json`), JSON.stringify(payload));
  console.log(JSON.stringify({
    name: spec.name,
    offsetMs,
    endPerf: Math.round(endPerf),
    samples: samples.length,
    frames: frames.filter(f => f.exists && f.bytes > 0).length,
    videoBytes: payload.videoBytes,
    nav: meta.nav,
    reduced: meta.reduced,
    font: meta.font,
  }));
}

const which = process.argv[2] || 'desktop';
const specs = {
  desktop: { name: 'desktop', viewport: { width: 1440, height: 900 }, durationMs: 20000, reduced: false },
  mobile: { name: 'mobile', viewport: { width: 390, height: 844 }, durationMs: 20000, reduced: false },
  reduced: { name: 'reduced-desktop', viewport: { width: 1440, height: 900 }, durationMs: 6000, reduced: true },
};
if (!specs[which]) {
  console.error('unknown spec');
  process.exit(1);
}
await capture(specs[which]);
