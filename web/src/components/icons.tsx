import type { SVGProps } from 'react';

type P = SVGProps<SVGSVGElement> & { size?: number };

const base = (size: number, rest: SVGProps<SVGSVGElement>) => ({
  width: size,
  height: size,
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 2,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
  'aria-hidden': true,
  ...rest,
});

export const ChevronLeft = ({ size = 22, ...r }: P) => (
  <svg {...base(size, r)}>
    <path d="M15 5l-7 7 7 7" />
  </svg>
);

export const Lock = ({ size = 20, ...r }: P) => (
  <svg {...base(size, r)}>
    <rect x="4.5" y="10.5" width="15" height="10" rx="2.5" />
    <path d="M8 10.5V7.5a4 4 0 0 1 8 0v3" />
  </svg>
);

export const ArrowRight = ({ size = 22, ...r }: P) => (
  <svg {...base(size, r)}>
    <path d="M4.5 12h15M13 5.5l6.5 6.5-6.5 6.5" />
  </svg>
);

export const Check = ({ size = 12, ...r }: P) => (
  <svg {...base(size, { strokeWidth: 3, ...r })}>
    <path d="M5 12.5l4.5 4.5L19 7.5" />
  </svg>
);

export const Pencil = ({ size = 18, ...r }: P) => (
  <svg {...base(size, { strokeWidth: 1.6, ...r })}>
    <path d="M16.5 3.5l4 4L8 20H4v-4L16.5 3.5z" />
    <path d="M14 6l4 4" />
  </svg>
);

export const Plus = ({ size = 30, ...r }: P) => (
  <svg {...base(size, { strokeWidth: 2.2, ...r })}>
    <path d="M12 4v16M4 12h16" />
  </svg>
);

export const Close = ({ size = 14, ...r }: P) => (
  <svg {...base(size, { strokeWidth: 2.4, ...r })}>
    <path d="M6 6l12 12M18 6L6 18" />
  </svg>
);

export const PaperPlane = ({ size = 14, ...r }: P) => (
  <svg {...base(size, { strokeWidth: 1.6, ...r })}>
    <path d="M21 3L10 14" />
    <path d="M21 3l-7 18-4-7-7-4 18-7z" />
  </svg>
);
