// Exports the frontend catalogs the backend reads (技术方案 §3.2):
//   - api/aiditto-app/src/main/resources/questions.json      ← src/onboarding/questions.ts
//   - api/aiditto-app/src/main/resources/db/migration/R__seed_places.sql ← src/api/places.ts + mock.ts venues
//
// usage: node scripts/export-questions.mjs           (write both files)
//        node scripts/export-questions.mjs --check   (exit 1 if either file is stale)
import { build } from 'esbuild';
import { mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const WEB = join(dirname(fileURLToPath(import.meta.url)), '..');
const RES = join(WEB, '..', 'api', 'aiditto-app', 'src', 'main', 'resources');
const QUESTIONS_OUT = join(RES, 'questions.json');
const PLACES_OUT = join(RES, 'db', 'migration', 'R__seed_places.sql');
const check = process.argv.includes('--check');

// Bundle the TS sources into a temp ESM module inside node_modules so bare imports (react-router) still resolve.
const tmpDir = join(WEB, 'node_modules', '.cache', 'export-questions');
mkdirSync(tmpDir, { recursive: true });
const entry = join(tmpDir, 'entry.ts');
writeFileSync(
  entry,
  [
    `export { QUESTIONS, BRANCHES } from ${JSON.stringify(join(WEB, 'src/onboarding/questions.ts'))};`,
    `export { PLACES } from ${JSON.stringify(join(WEB, 'src/api/places.ts'))};`,
    `export { PARTNERS } from ${JSON.stringify(join(WEB, 'src/api/mock.ts'))};`,
  ].join('\n'),
);
const outFile = join(tmpDir, 'bundle.mjs');
await build({ entryPoints: [entry], bundle: true, format: 'esm', platform: 'node', packages: 'external', outfile: outFile, logLevel: 'error' });
const { QUESTIONS, PLACES, PARTNERS } = await import(pathToFileURL(outFile).href);
rmSync(tmpDir, { recursive: true, force: true });

// ─── questions.json ────────────────────────────────────────────────────────────────────────────────────
const GENDERS = [undefined, 'man', 'woman', 'nonbinary', 'other'];
const EXPECTED = [[], ['men'], ['women'], ['nonbinary'], ['everyone'], ['men', 'women']];
const contexts = [];
for (const lang of ['en', 'zh'])
  for (const isStudent of ['yes', 'no'])
    for (const gender of GENDERS) for (const expectedGender of EXPECTED) contexts.push({ lang, answers: { isStudent, gender, expectedGender } });

const optionIds = (q) => {
  const ids = new Set();
  const lists = q.optionsFor ? contexts.map((ctx) => q.optionsFor(ctx)) : [q.options ?? []];
  for (const list of lists) for (const o of list) ids.add(o.id);
  return [...ids];
};

const questions = Object.values(QUESTIONS).map((q) => {
  const out = { id: q.id, type: q.type };
  const opts = optionIds(q);
  if (opts.length) out.options = opts;
  if (q.maxSelect != null) out.maxSelect = q.maxSelect;
  if (q.maxLength != null) out.maxLength = q.maxLength;
  if (q.exclusive?.length) out.exclusive = q.exclusive;
  if (q.optional) out.optional = true;
  if (q.emailKind) out.emailKind = q.emailKind;
  if (q.allowCustom) out.allowCustom = true;
  if (q.private) out.private = true;
  if (q.hobby) out.hobby = q.hobby;
  const drop = Object.entries(q.drop ?? {}).filter(([, v]) => v).map(([k]) => k);
  if (drop.length) out.drop = drop;
  return out;
});
const questionsJson = `${JSON.stringify({ source: 'web/src/onboarding/questions.ts', questions }, null, 2)}\n`;

// ─── R__seed_places.sql ────────────────────────────────────────────────────────────────────────────────
// Approximate centres for distance estimates (技术方案 §7.2 item 5). City codes split the matching pools.
const GEO = {
  pku: ['bj', 39.9929, 116.3055],
  tsinghua: ['bj', 40.0, 116.3264],
  ruc: ['bj', 39.97, 116.313],
  bnu: ['bj', 39.9614, 116.366],
  wudaokou: ['bj', 39.9925, 116.338],
  sanlitun: ['bj', 39.933, 116.454],
  fudan: ['sh', 31.299, 121.501],
  tongji: ['sh', 31.283, 121.502],
  sjtu: ['sh', 31.025, 121.433],
  ecnu: ['sh', 31.228, 121.404],
  wukang: ['sh', 31.205, 121.438],
  zju: ['hz', 30.308, 120.086],
  nju: ['nj', 32.119, 118.958],
  whu: ['wh', 30.54, 114.36],
  sysu: ['gz', 23.096, 113.299],
  szu: ['sz', 22.533, 113.93],
  scu: ['cd', 30.63, 104.083],
  xjtu: ['xa', 34.247, 108.985],
  'boston-ma': ['intl', 42.3601, -71.0589],
  'boston-uk': ['intl', 52.9789, -0.0266],
  'boston-bar': ['intl', 49.8667, -121.4333],
  'pku-treehole': ['bj', 39.9915, 116.3105],
  'thu-lotus': ['bj', 40.0035, 116.3195],
  'ruc-mingde': ['bj', 39.9705, 116.3165],
};

const sql = (v) => {
  if (v === null || v === undefined) return 'NULL';
  if (typeof v === 'number') return String(v);
  if (typeof v === 'boolean') return v ? '1' : '0';
  return `'${String(v).replace(/\\/g, '\\\\').replace(/'/g, "''")}'`;
};
const geo = (id) => {
  if (!GEO[id]) throw new Error(`export-questions: add coordinates for place '${id}'`);
  return GEO[id];
};

const COLS = [
  'id', 'kind', 'city', 'name_zh', 'name_en', 'sub_zh', 'sub_en', 'kw', 'nearby', 'density', 'en_only', 'lat', 'lng',
  'address_zh', 'address_en', 'activity_tags', 'open_hours', 'default_activity_zh', 'default_activity_en', 'active', 'sort_order',
];
const rows = [];
for (const p of PLACES) {
  const [city, lat, lng] = geo(p.id);
  rows.push([p.id, 'area', city, p.name.zh, p.name.en, p.sub.zh, p.sub.en, p.kw, JSON.stringify(p.nearby), p.density, !!p.enOnly, lat, lng, null, null, null, null, null, null, true, rows.length]);
}
const venues = new Map();
for (const partner of PARTNERS) venues.set(partner.venue.id, partner.venue);
for (const v of venues.values()) {
  const [city, lat, lng] = geo(v.id);
  rows.push([v.id, 'venue', city, v.name.zh, v.name.en, null, null, '', '[]', 0, false, lat, lng, v.address.zh, v.address.en, '[]', null, v.activity.zh, v.activity.en, true, rows.length]);
}
const update = COLS.filter((c) => c !== 'id').map((c) => `${c} = new.${c}`).join(', ');
const placesSql = [
  '-- Generated by web/scripts/export-questions.mjs from web/src/api/places.ts and mock.ts venues. Do not edit.',
  ...rows.map((r) => `INSERT INTO place (${COLS.join(', ')}) VALUES (${r.map(sql).join(', ')}) AS new ON DUPLICATE KEY UPDATE ${update};`),
  '',
].join('\n');

// ─── write / check ─────────────────────────────────────────────────────────────────────────────────────
const outputs = [
  [QUESTIONS_OUT, questionsJson],
  [PLACES_OUT, placesSql],
];
let stale = 0;
for (const [file, content] of outputs) {
  let current = null;
  try {
    current = readFileSync(file, 'utf8');
  } catch {
    // missing counts as stale
  }
  if (check) {
    if (current !== content) {
      console.error(`stale: ${file} (run node scripts/export-questions.mjs)`);
      stale += 1;
    }
  } else if (current !== content) {
    mkdirSync(dirname(file), { recursive: true });
    writeFileSync(file, content);
    console.log(`wrote ${file}`);
  }
}
console.log(`${questions.length} questions, ${rows.length} places`);
if (stale) process.exit(1);
