// Hero phone match cards (Ditto `images/austin/cards/*.webp`, 387×491): an AI-generated collage of a fictional
// person with a white "your date with" card on top. Renders 2× JPEGs into public/landing/cards/{id}-{lang}.jpg.
// Usage: node scripts/landing/build-cards.mjs
import { createRequire } from 'node:module';
import { mkdirSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

process.env.PLAYWRIGHT_BROWSERS_PATH = '/Users/null/Library/Caches/ms-playwright';
const require = createRequire('/Users/null/.npm/_npx/9833c18b2d85bc59/node_modules/');
const { chromium } = require('playwright');

const here = dirname(fileURLToPath(import.meta.url));
const out = join(here, '../../public/landing/cards');
mkdirSync(out, { recursive: true });

const CARDS = [
  {
    id: 'zihan',
    zh: {
      kicker: '你的约会对象',
      name: '子涵',
      lead: '子涵就是你在找的那个人！',
      body: [
        '他身上有种踏实可靠的劲儿：爬完一整天的山，回家还能给你炒两个拿手菜，吃完还有力气聊到深夜。家里有只叫「橘子」的胖橘猫，被他宠上了天。',
        '如果你想找一个真诚、知道自己要什么、懂得享受简单日子（好饭和好伴）的人，子涵已经先走了几步。',
      ],
    },
    en: {
      kicker: 'Your date with',
      name: 'Jason',
      lead: "Jason is the perfect date you're looking for!",
      body: [
        "He's got that steady, dependable energy: a full day on the trail, then home to cook you something good, and still up for a deep talk after. His orange cat Mango is very, very spoiled.",
        'If you want someone real who knows what he wants and loves the simple things (good food, good company), Jason is a few steps ahead.',
      ],
    },
  },
  {
    id: 'yifan',
    zh: {
      kicker: '你的约会对象',
      name: '一凡',
      lead: '一凡就是你在找的那个人！',
      body: [
        '抱起吉他就能开唱，踩上滑板就能出发。速写本走到哪画到哪，宿舍楼下的猫都被她画过一遍。',
        '她有主见、爱笑，跟她聊天永远不会冷场。想找一个能一起疯、也能一起安静待着的人？一凡就是答案。',
      ],
    },
    en: {
      kicker: 'Your date with',
      name: 'Emily',
      lead: 'Emily is the perfect date you are looking for!',
      body: [
        "Guitar in hand, she'll start a song; board under her feet, she's already gone. Her sketchbook goes everywhere, and every campus cat has been drawn at least once.",
        "Opinionated, funny and never short of things to say. Want someone to be wild with and quiet with? It's Emily.",
      ],
    },
  },
  {
    id: 'jiayi',
    zh: {
      kicker: '你的约会对象',
      name: '嘉怡',
      lead: '嘉怡就是你在找的那个人！',
      body: [
        '她自带电音现场的能量：深夜听 live、拍立得拍满一整本、超大份冰淇淋从不剩。周末最常见的画面，是牵着柯基「豆豆」满公园跑。',
        '她直接、好笑，什么话题都接得住。如果你在找一个能把平常日子过成派对的人，系好安全带，嘉怡来了。',
      ],
    },
    en: {
      kicker: 'Your date with',
      name: 'Alex',
      lead: 'Alex is the perfect date you are looking for!',
      body: [
        "She's electric: late-night gigs, a polaroid album filled in a week, and jumbo sundaes with zero shame. Most weekends you'll find her chasing her corgi Bean around the park.",
        "Direct, hilarious and down for any topic. If you're after someone who turns ordinary days into a party, buckle up. It's Alex.",
      ],
    },
  },
];

const W = 387;
const H = 491;

const esc = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;');

function html(bg, c, lang) {
  const zh = lang === 'zh';
  return `<!doctype html><html lang="${zh ? 'zh-CN' : 'en'}"><head><meta charset="utf-8"><style>
  html,body{margin:0;width:${W}px;height:${H}px;overflow:hidden}
  body{background:url(data:image/jpeg;base64,${bg}) center/cover no-repeat;
    font-family:${zh ? "'PingFang SC','Hiragino Sans GB'" : "'Poppins','Helvetica Neue'"},-apple-system,sans-serif;color:#111;
    -webkit-font-smoothing:antialiased}
  .card{position:absolute;left:73px;top:88px;width:250px;box-sizing:border-box;padding:18px 18px 20px;
    background:#fff;border-radius:10px;box-shadow:0 2px 10px rgba(0,0,0,.18)}
  .k{font-size:15px;font-weight:600;line-height:1.2}
  .n{font-size:${zh ? 27 : 25}px;font-weight:800;line-height:1.15;margin:2px 0 ${zh ? 16 : 18}px;letter-spacing:${zh ? '1px' : '-.2px'}}
  .l{font-size:${zh ? 13 : 12.5}px;font-weight:700;line-height:1.35;margin-bottom:9px}
  p{margin:0 0 9px;font-size:${zh ? 10.6 : 10.4}px;line-height:${zh ? 1.62 : 1.42};color:#6b6b72}
  p:last-child{margin:0}
  </style></head><body><div class="card"><div class="k">${esc(c.kicker)}</div><div class="n">${esc(c.name)}</div>
  <div class="l">${esc(c.lead)}</div>${c.body.map((p) => `<p>${esc(p)}</p>`).join('')}</div></body></html>`;
}

const browser = await chromium.launch({ channel: 'chrome', headless: true });
const page = await browser.newPage({ viewport: { width: W, height: H }, deviceScaleFactor: 2 });
for (const card of CARDS) {
  const bg = readFileSync(join(here, 'card-src', `${card.id}.jpg`)).toString('base64');
  for (const lang of ['zh', 'en']) {
    await page.setContent(html(bg, card[lang], lang));
    const file = join(out, `${card.id}-${lang}.jpg`);
    await page.screenshot({ path: file, type: 'jpeg', quality: 84 });
    const over = await page.evaluate(() => document.querySelector('.card').getBoundingClientRect().bottom);
    console.log(file, over > 470 ? `card bottom ${over}px: text too long` : 'ok');
  }
}
await browser.close();
