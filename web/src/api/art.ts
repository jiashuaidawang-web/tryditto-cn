/**
 * Generated flat-art SVGs for mock partners and sample photos. Deliberately non-photographic and
 * non-identifiable (never use people from the research screenshots).
 */
import type { AvatarSpec, SceneKind } from './types';

const uri = (svg: string) => `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;

function hairBack(spec: AvatarSpec): string {
  const c = spec.hairColor;
  switch (spec.hair) {
    case 'long':
      return `<path d="M58 92c0-34 18-54 42-54s42 20 42 54v70H58z" fill="${c}"/>`;
    case 'bob':
      return `<path d="M56 96c0-36 19-58 44-58s44 22 44 58v28c0 6-5 10-11 10H67c-6 0-11-4-11-10z" fill="${c}"/>`;
    case 'curly':
      return `<g fill="${c}">${[
        [70, 70],
        [86, 56],
        [104, 52],
        [122, 58],
        [134, 74],
        [64, 90],
        [138, 92],
        [60, 110],
        [140, 110],
      ]
        .map(([x, y]) => `<circle cx="${x}" cy="${y}" r="17"/>`)
        .join('')}</g>`;
    default:
      return '';
  }
}

function hairFront(spec: AvatarSpec): string {
  const c = spec.hairColor;
  switch (spec.hair) {
    case 'short':
      return `<path d="M64 92c-2-30 16-50 38-50 24 0 38 18 36 46-8-12-22-18-38-18-14 0-28 8-36 22z" fill="${c}"/>`;
    case 'bun':
      return `<circle cx="100" cy="40" r="16" fill="${c}"/><path d="M64 94c0-30 16-48 36-48s36 18 36 48c-10-14-22-20-36-20s-26 6-36 20z" fill="${c}"/>`;
    case 'bob':
    case 'long':
      return `<path d="M64 96c0-30 16-50 36-50s36 20 36 50c-6-16-20-26-36-26-12 0-24 6-30 14z" fill="${c}"/>`;
    case 'curly':
      return `<path d="M68 88c4-22 18-34 32-34s28 12 32 34c-10-10-20-14-32-14s-22 4-32 14z" fill="${c}"/>`;
  }
}

export function avatarSvg(spec: AvatarSpec, size = 200): string {
  const [c1, c2] = spec.bg;
  const glasses = spec.glasses
    ? `<g fill="none" stroke="#1d1d1f" stroke-width="3"><circle cx="86" cy="102" r="10"/><circle cx="114" cy="102" r="10"/><path d="M96 102h8"/></g>`
    : '';
  const earring = spec.earring ? `<circle cx="66" cy="118" r="3.5" fill="#ffd166"/>` : '';
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200" width="${size}" height="${size}">
<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="${c1}"/><stop offset="1" stop-color="${c2}"/></linearGradient></defs>
<rect width="200" height="200" fill="url(#g)"/>
<circle cx="160" cy="38" r="22" fill="#fff" opacity=".35"/>
${hairBack(spec)}
<path d="M40 200c4-34 28-54 60-54s56 20 60 54z" fill="${spec.shirt}"/>
<rect x="90" y="128" width="20" height="24" rx="8" fill="${spec.skin}"/>
<ellipse cx="100" cy="102" rx="34" ry="38" fill="${spec.skin}"/>
<ellipse cx="66" cy="106" rx="6" ry="9" fill="${spec.skin}"/><ellipse cx="134" cy="106" rx="6" ry="9" fill="${spec.skin}"/>
${hairFront(spec)}
<circle cx="86" cy="103" r="3.6" fill="#2a2a2a"/><circle cx="114" cy="103" r="3.6" fill="#2a2a2a"/>
<ellipse cx="80" cy="116" rx="7" ry="4" fill="#ff8fa3" opacity=".45"/><ellipse cx="120" cy="116" rx="7" ry="4" fill="#ff8fa3" opacity=".45"/>
<path d="M90 122c6 6 14 6 20 0" fill="none" stroke="#7a3b2e" stroke-width="3" stroke-linecap="round"/>
${glasses}${earring}
</svg>`;
}

const SCENES: Record<SceneKind, [string, string, string]> = {
  // [background, accent, detail]
  coffee: ['#f4e1c1', '#8d5a3b', `<rect x="62" y="84" width="64" height="66" rx="10" fill="#fff"/><path d="M126 98c18 0 18 30 0 30" fill="none" stroke="#fff" stroke-width="9"/><rect x="66" y="88" width="56" height="16" rx="6" fill="#8d5a3b"/><path d="M80 66c-6-10 6-14 0-24M96 66c-6-10 6-14 0-24" stroke="#c9a27e" stroke-width="4" fill="none" stroke-linecap="round"/><ellipse cx="96" cy="156" rx="52" ry="8" fill="#d9bf98"/>`],
  books: ['#cfe8ff', '#3a5a98', `<rect x="46" y="70" width="22" height="86" rx="3" fill="#e76f51"/><rect x="70" y="58" width="20" height="98" rx="3" fill="#2a9d8f"/><rect x="92" y="76" width="24" height="80" rx="3" fill="#e9c46a"/><rect x="118" y="64" width="18" height="92" rx="3" transform="rotate(8 127 110)" fill="#264653"/><rect x="36" y="156" width="128" height="8" rx="3" fill="#8d6e63"/>`],
  film: ['#ffe5ec', '#b5179e', `<rect x="40" y="62" width="120" height="76" rx="8" fill="#22223b"/><g fill="#fff">${[48, 66, 84, 102, 120, 138].map((x) => `<rect x="${x}" y="68" width="10" height="8" rx="2"/><rect x="${x}" y="124" width="10" height="8" rx="2"/>`).join('')}</g><rect x="52" y="82" width="44" height="36" rx="4" fill="#ffafcc"/><rect x="104" y="82" width="44" height="36" rx="4" fill="#bde0fe"/>`],
  mountain: ['#bde0fe', '#457b9d', `<circle cx="146" cy="56" r="16" fill="#ffd166"/><path d="M20 160l50-74 30 40 24-30 56 64z" fill="#457b9d"/><path d="M70 86l12 18-12-4-10 6z" fill="#fff"/>`],
  cat: ['#fde2e4', '#6d6875', `<ellipse cx="100" cy="126" rx="44" ry="34" fill="#f4a261"/><circle cx="100" cy="88" r="28" fill="#f4a261"/><path d="M76 72l4-22 16 16zM124 72l-4-22-16 16z" fill="#f4a261"/><circle cx="90" cy="88" r="3.5" fill="#222"/><circle cx="110" cy="88" r="3.5" fill="#222"/><path d="M96 98l4 3 4-3" stroke="#222" stroke-width="2.5" fill="none"/><path d="M144 132c18-4 22-26 8-30" stroke="#f4a261" stroke-width="10" fill="none" stroke-linecap="round"/>`],
  guitar: ['#e9f5db', '#606c38', `<ellipse cx="88" cy="126" rx="30" ry="28" fill="#bc6c25"/><ellipse cx="104" cy="98" rx="22" ry="20" fill="#bc6c25"/><circle cx="96" cy="112" r="9" fill="#3a2d1d"/><rect x="108" y="36" width="10" height="70" rx="3" transform="rotate(28 113 71)" fill="#3a2d1d"/>`],
  bike: ['#ffedd8', '#e85d04', `<g fill="none" stroke="#2b2d42" stroke-width="7"><circle cx="64" cy="128" r="26"/><circle cx="140" cy="128" r="26"/><path d="M64 128l28-44h36l12 44M92 84l20 44h28"/></g><rect x="84" y="74" width="20" height="7" rx="3" fill="#e85d04"/>`],
  sunset: ['#ffd6a5', '#f3722c', `<rect y="110" width="200" height="90" fill="#577590"/><circle cx="100" cy="112" r="34" fill="#f94144"/><rect y="110" width="200" height="6" fill="#ffd6a5" opacity=".7"/><g stroke="#ffd6a5" stroke-width="3" opacity=".6"><path d="M50 132h40M110 146h50M60 160h30"/></g>`],
  noodles: ['#fff1e6', '#d62828', `<path d="M40 104h120c0 40-26 58-60 58s-60-18-60-58z" fill="#d62828"/><g stroke="#f6bd60" stroke-width="5" fill="none" stroke-linecap="round"><path d="M64 104c0-30 10-30 12 0M84 104c0-34 10-34 12 0M104 104c0-30 10-30 12 0"/></g><path d="M120 40l20 64M134 36l16 66" stroke="#6b4226" stroke-width="5" stroke-linecap="round"/>`],
  camera: ['#e0fbfc', '#3d5a80', `<rect x="44" y="72" width="112" height="76" rx="12" fill="#3d5a80"/><rect x="72" y="60" width="36" height="16" rx="5" fill="#3d5a80"/><circle cx="100" cy="110" r="26" fill="#e0fbfc"/><circle cx="100" cy="110" r="16" fill="#293241"/><circle cx="140" cy="86" r="5" fill="#ee6c4d"/>`],
  gamepad: ['#e2ece9', '#7b2cbf', `<rect x="40" y="82" width="120" height="56" rx="28" fill="#7b2cbf"/><path d="M70 100v20M60 110h20" stroke="#fff" stroke-width="6" stroke-linecap="round"/><circle cx="126" cy="102" r="6" fill="#ffd166"/><circle cx="140" cy="116" r="6" fill="#06d6a0"/>`],
  plant: ['#f1faee', '#2d6a4f', `<path d="M70 120h60l-8 40H78z" fill="#e76f51"/><path d="M100 120c0-30-30-40-40-30 10 4 30 10 40 30zM100 120c0-36 26-50 40-40-12 6-32 14-40 40zM100 120V70" stroke="#2d6a4f" stroke-width="4" fill="#52b788"/>`],
};

export function sceneSvg(kind: SceneKind, size = 200): string {
  const [bg, , detail] = SCENES[kind];
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200" width="${size}" height="${size}"><rect width="200" height="200" fill="${bg}"/>${detail}</svg>`;
}

export const avatarUri = (spec: AvatarSpec) => uri(avatarSvg(spec));
export const sceneUri = (kind: SceneKind) => uri(sceneSvg(kind));

/** "Me" sample photos for the demo profile. */
export const SAMPLE_ME_AVATAR: AvatarSpec = { bg: ['#90e0ef', '#caf0f8'], skin: '#efc9a8', hair: 'short', hairColor: '#231815', shirt: '#ffb703' };
export const samplePhotoUris = () => [avatarUri(SAMPLE_ME_AVATAR), sceneUri('bike')];
