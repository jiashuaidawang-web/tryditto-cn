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

export const ChevronRight = ({ size = 18, ...r }: P) => (
  <svg {...base(size, r)}>
    <path d="M9 5l7 7-7 7" />
  </svg>
);

export const ChevronDown = ({ size = 22, ...r }: P) => (
  <svg {...base(size, r)}>
    <path d="M5 9l7 7 7-7" />
  </svg>
);

export const Sparkles = ({ size = 24, ...r }: P) => (
  <svg {...base(size, { strokeWidth: 1.7, ...r })}>
    <path d="M10 3.5l1.6 4.6a3 3 0 0 0 1.8 1.8L18 11.5l-4.6 1.6a3 3 0 0 0-1.8 1.8L10 19.5l-1.6-4.6a3 3 0 0 0-1.8-1.8L2 11.5l4.6-1.6a3 3 0 0 0 1.8-1.8z" />
    <path d="M19 2.5v4M17 4.5h4M19.5 16v3M18 17.5h3" />
  </svg>
);

export const MapPin = ({ size = 24, ...r }: P) => (
  <svg {...base(size, { strokeWidth: 1.7, ...r })}>
    <path d="M12 21s-7-6.2-7-11.5a7 7 0 0 1 14 0C19 14.8 12 21 12 21z" />
    <circle cx="12" cy="9.5" r="2.5" />
  </svg>
);

export const UserIcon = ({ size = 24, ...r }: P) => (
  <svg {...base(size, { strokeWidth: 1.7, ...r })}>
    <circle cx="12" cy="8" r="4" />
    <path d="M4.5 20.5c.8-4 3.8-6 7.5-6s6.7 2 7.5 6" />
  </svg>
);

export const MenuIcon = ({ size = 22, ...r }: P) => (
  <svg {...base(size, { strokeWidth: 2, ...r })}>
    <path d="M4 7h16M4 12h16M4 17h16" />
  </svg>
);

/** Cupid arrow through a heart (Ditto's Invite pill). */
export const HeartArrow = ({ size = 22, ...r }: P) => (
  <svg {...base(size, { strokeWidth: 1.7, ...r })}>
    <path d="M14.5 6.5c1.4-1.6 4-1.5 5.1.4 1 1.6.5 3.6-.9 4.8L13 17l-1.2-1" />
    <path d="M14.5 6.5c-1.5-1.5-4-1.3-5 .5" />
    <path d="M3 21l11-11M3 21v-3.5M3 21h3.5M14 10l2.5-.5.5-2.5" />
  </svg>
);

export const MessageCircle = ({ size = 16, ...r }: P) => (
  <svg {...base(size, { strokeWidth: 1.7, ...r })}>
    <path d="M4 5.5h16v10H9l-5 4z" />
  </svg>
);

export const ContactCard = ({ size = 16, ...r }: P) => (
  <svg {...base(size, { strokeWidth: 1.7, ...r })}>
    <rect x="3.5" y="4.5" width="17" height="16" rx="2" />
    <circle cx="12" cy="11" r="2.5" />
    <path d="M8 17c.6-1.8 2.1-2.8 4-2.8s3.4 1 4 2.8M9 2.5v3M15 2.5v3" />
  </svg>
);

export const Mail = ({ size = 18, ...r }: P) => (
  <svg {...base(size, { strokeWidth: 1.7, ...r })}>
    <rect x="3" y="5.5" width="18" height="13" rx="2" />
    <path d="M3.5 7l8.5 6 8.5-6" />
  </svg>
);

export const Gear = ({ size = 16, ...r }: P) => (
  <svg {...base(size, { strokeWidth: 1.7, ...r })}>
    <circle cx="12" cy="12" r="3" />
    <path d="M12 2.5v2.2M12 19.3v2.2M4.6 4.6l1.6 1.6M17.8 17.8l1.6 1.6M2.5 12h2.2M19.3 12h2.2M4.6 19.4l1.6-1.6M17.8 6.2l1.6-1.6" />
  </svg>
);

export const Search = ({ size = 18, ...r }: P) => (
  <svg {...base(size, { strokeWidth: 1.9, ...r })}>
    <circle cx="11" cy="11" r="6.5" />
    <path d="M16 16l4.5 4.5" />
  </svg>
);

export const Crosshair = ({ size = 20, ...r }: P) => (
  <svg {...base(size, { strokeWidth: 1.8, ...r })}>
    <circle cx="12" cy="12" r="6" />
    <circle cx="12" cy="12" r="2" fill="currentColor" />
    <path d="M12 2.5V6M12 18v3.5M2.5 12H6M18 12h3.5" />
  </svg>
);

export const Footprints = ({ size = 18, ...r }: P) => (
  <svg {...base(size, { strokeWidth: 1.7, ...r })}>
    <path d="M7 3.5c1.8 0 2.8 2 2.8 4.6S8.9 13 7.3 13 4.5 10.9 4.5 8.4 5.3 3.5 7 3.5zM5 15.5h4.5v1.8a2.2 2.2 0 0 1-4.5 0z" />
    <path d="M17 6.5c1.7 0 2.5 2.4 2.5 4.9S18.6 16 17 16s-2.8-2.1-2.8-4.6S15.3 6.5 17 6.5zM14.7 18.5h4.5v.7a2.2 2.2 0 0 1-4.5 0z" />
  </svg>
);

export const Train = ({ size = 18, ...r }: P) => (
  <svg {...base(size, { strokeWidth: 1.7, ...r })}>
    <rect x="5" y="3.5" width="14" height="14" rx="3" />
    <path d="M5 11h14M9 21l1.5-3.5M15 21l-1.5-3.5" />
    <circle cx="9" cy="14.3" r=".8" fill="currentColor" />
    <circle cx="15" cy="14.3" r=".8" fill="currentColor" />
  </svg>
);

export const Car = ({ size = 18, ...r }: P) => (
  <svg {...base(size, { strokeWidth: 1.7, ...r })}>
    <path d="M3.5 15.5v-3l2-4.5a2 2 0 0 1 1.8-1.2h9.4a2 2 0 0 1 1.8 1.2l2 4.5v3a1 1 0 0 1-1 1h-15a1 1 0 0 1-1-1z" />
    <circle cx="7.5" cy="16.5" r="1.8" />
    <circle cx="16.5" cy="16.5" r="1.8" />
    <path d="M4 12h16" />
  </svg>
);

export const ShieldOff = ({ size = 28, ...r }: P) => (
  <svg {...base(size, { strokeWidth: 1.7, ...r })}>
    <path d="M12 3l7.5 3v5.5c0 4.6-3.2 8.2-7.5 9.5-4.3-1.3-7.5-4.9-7.5-9.5V6z" />
    <path d="M8.5 8.5l7 7" />
  </svg>
);

export const Phone = ({ size = 16, ...r }: P) => (
  <svg {...base(size, { strokeWidth: 1.7, ...r })}>
    <path d="M6.5 3.5h3l1.5 4-2 1.3a10 10 0 0 0 6.2 6.2l1.3-2 4 1.5v3a2 2 0 0 1-2.2 2A16.5 16.5 0 0 1 4.5 5.7a2 2 0 0 1 2-2.2z" />
  </svg>
);

export const Smartphone = ({ size = 18, ...r }: P) => (
  <svg {...base(size, { strokeWidth: 1.7, ...r })}>
    <rect x="6.5" y="2.5" width="11" height="19" rx="2" />
    <path d="M11 18.5h2" />
  </svg>
);

export const FileText = ({ size = 16, ...r }: P) => (
  <svg {...base(size, { strokeWidth: 1.7, ...r })}>
    <path d="M14 3.5H6.5v17h11V7z" />
    <path d="M14 3.5V7h3.5" />
  </svg>
);

export const LogOut = ({ size = 18, ...r }: P) => (
  <svg {...base(size, { strokeWidth: 1.7, ...r })}>
    <path d="M9.5 20.5h-4a1 1 0 0 1-1-1v-15a1 1 0 0 1 1-1h4M16 16.5l4.5-4.5L16 7.5M20.5 12H9.5" />
  </svg>
);

export const PauseCircle = ({ size = 18, ...r }: P) => (
  <svg {...base(size, { strokeWidth: 1.7, ...r })}>
    <circle cx="12" cy="12" r="8.5" />
    <path d="M10 9v6M14 9v6" />
  </svg>
);

export const Trash = ({ size = 18, ...r }: P) => (
  <svg {...base(size, { strokeWidth: 1.7, ...r })}>
    <path d="M4 6.5h16M9.5 6.5V4h5v2.5M6 6.5l1 14h10l1-14M10 10.5v6M14 10.5v6" />
  </svg>
);

export const LinkIcon = ({ size = 24, ...r }: P) => (
  <svg {...base(size, { strokeWidth: 1.7, ...r })}>
    <path d="M10 14a4.5 4.5 0 0 0 6.4 0l3-3a4.5 4.5 0 0 0-6.4-6.4l-1 1" />
    <path d="M14 10a4.5 4.5 0 0 0-6.4 0l-3 3a4.5 4.5 0 0 0 6.4 6.4l1-1" />
  </svg>
);

export const Share = ({ size = 24, ...r }: P) => (
  <svg {...base(size, { strokeWidth: 1.7, ...r })}>
    <path d="M12 15V3.5M7.5 8L12 3.5 16.5 8" />
    <path d="M5 11.5V20h14v-8.5" />
  </svg>
);

export const ImageIcon = ({ size = 24, ...r }: P) => (
  <svg {...base(size, { strokeWidth: 1.7, ...r })}>
    <rect x="3.5" y="4.5" width="17" height="15" rx="2.5" />
    <circle cx="9" cy="10" r="1.8" />
    <path d="M20 16l-5-5-8.5 8.5" />
  </svg>
);

export const BookOpen = ({ size = 24, ...r }: P) => (
  <svg {...base(size, { strokeWidth: 1.7, ...r })}>
    <path d="M12 6.5c-2-1.6-5-2-8-1.5v13c3-.5 6 0 8 1.5 2-1.5 5-2 8-1.5V5c-3-.5-6-.1-8 1.5zM12 6.5v13" />
  </svg>
);

export const HelpCircle = ({ size = 22, ...r }: P) => (
  <svg {...base(size, { strokeWidth: 1.7, ...r })}>
    <circle cx="12" cy="12" r="9" />
    <path d="M9.6 9.4a2.5 2.5 0 0 1 4.8.9c0 1.7-2.4 2.1-2.4 3.7M12 17h.01" />
  </svg>
);

export const Copy = ({ size = 18, ...r }: P) => (
  <svg {...base(size, { strokeWidth: 1.7, ...r })}>
    <rect x="8.5" y="8.5" width="12" height="12" rx="2" />
    <path d="M15.5 8.5v-3a2 2 0 0 0-2-2h-8a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h3" />
  </svg>
);

export const Heart = ({ size = 22, ...r }: P) => (
  <svg {...base(size, { strokeWidth: 1.8, ...r })}>
    <path d="M12 20s-7.5-4.6-7.5-10.2A4.3 4.3 0 0 1 12 7.3a4.3 4.3 0 0 1 7.5 2.5C19.5 15.4 12 20 12 20z" />
  </svg>
);

export const Flag = ({ size = 18, ...r }: P) => (
  <svg {...base(size, { strokeWidth: 1.7, ...r })}>
    <path d="M5 21V4M5 4.5c5-2.5 9 2.5 14 0v9c-5 2.5-9-2.5-14 0" />
  </svg>
);

export const Clock = ({ size = 18, ...r }: P) => (
  <svg {...base(size, { strokeWidth: 1.7, ...r })}>
    <circle cx="12" cy="12" r="8.5" />
    <path d="M12 7.5V12l3 2" />
  </svg>
);

export const Star = ({ size = 28, ...r }: P) => (
  <svg {...base(size, { strokeWidth: 1.6, ...r })}>
    <path d="M12 3.5l2.6 5.4 5.9.8-4.3 4.1 1 5.8L12 16.8l-5.2 2.8 1-5.8-4.3-4.1 5.9-.8z" />
  </svg>
);
