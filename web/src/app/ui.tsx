import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { useDb } from '../api';
import { AppShell } from '../components/AppShell';
import { ChevronLeft, ChevronRight, Close, MapPin, Pencil, Sparkles, UserIcon } from '../components/icons';
import { pick, t, useLang, useQuerySuffix } from '../i18n/lang';
import { useOnboarding } from '../onboarding/store';
import { DemoFab } from './DemoPanel';

export function useIsDemo(): boolean {
  const [params] = useSearchParams();
  return params.get('demo') === '1';
}

/** navigate() that keeps `?lang=` / `?demo=` across routes. */
export function useGo() {
  const navigate = useNavigate();
  const suffix = useQuerySuffix();
  return useCallback((path: string, opts?: { replace?: boolean }) => navigate(`${path}${suffix}`, opts), [navigate, suffix]);
}

/**
 * Loads through an api function and re-runs whenever the local mock stores change (demo panel jumps,
 * saves on other pages). With a real backend this becomes a query cache keyed by endpoint.
 */
export function useLive<T>(load: () => Promise<T>, deps: unknown[] = []): T | undefined {
  const [value, setValue] = useState<T>();
  const dbState = useDb();
  const answers = useOnboarding((s) => s.answers);
  const completed = useOnboarding((s) => s.completed);
  useEffect(() => {
    let live = true;
    void load().then((v) => live && setValue(v));
    return () => {
      live = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dbState, answers, completed, ...deps]);
  return value;
}

// ───────────────────────── Shell pieces ─────────────────────────

type Tab = 'ditto' | 'where' | 'you';

const TABS: { id: Tab; to: string; label: ReturnType<typeof t>; Icon: typeof Sparkles }[] = [
  { id: 'ditto', to: '/home', label: t('Ditto', '见一面'), Icon: Sparkles },
  { id: 'where', to: '/where', label: t('Where', '去哪约'), Icon: MapPin },
  { id: 'you', to: '/you', label: t('You', '我的'), Icon: UserIcon },
];

/** Bottom 3 tabs (08: Ditto / Where / You). Active white, inactive white 45%. */
export function TabBar({ active }: { active: Tab }) {
  const lang = useLang();
  const suffix = useQuerySuffix();
  return (
    <nav className="absolute inset-x-0 bottom-0 z-20 grid h-[72px] grid-cols-3 px-[15px] pb-[12px]" aria-label="Tabs">
      {TABS.map(({ id, to, label, Icon }) => (
        <Link
          key={id}
          to={`${to}${suffix}`}
          aria-current={active === id ? 'page' : undefined}
          className={`flex flex-col items-center justify-end gap-[3px] ${active === id ? 'text-white' : 'text-white/45'}`}
        >
          <Icon size={22} strokeWidth={1.7} />
          <span className="text-[11px] font-bold leading-[14px]">{pick(label, lang)}</span>
        </Link>
      ))}
    </nav>
  );
}

/** Logged-in page frame: sky background, optional tab bar, demo button when `?demo=1`. */
export function AppScreen({ tab, children }: { tab?: Tab; children: ReactNode }) {
  const demo = useIsDemo();
  return (
    <AppShell>
      {children}
      {tab && <TabBar active={tab} />}
      {demo && <DemoFab />}
    </AppShell>
  );
}

/**
 * Back + centred title. `page` (/edit-profile, /deep-info, /blocklist, /account): row centred at y=40.
 * `edit` (/onboarding/edit/*, 13 §3): row centred at y=20, title 16/500.
 */
export function TopBar({ title, onBack, variant = 'page', backLabel = 'Back' }: { title: string; onBack: () => void; variant?: 'page' | 'edit'; backLabel?: string }) {
  const top = variant === 'edit' ? 0 : 20;
  return (
    <div className="absolute inset-x-0 z-20 h-10" style={{ top }}>
      <button type="button" aria-label={backLabel} onClick={onBack} className="absolute left-[8px] top-0 grid size-10 place-items-center text-white">
        <ChevronLeft size={22} strokeWidth={2.4} />
      </button>
      <h1 className={`absolute inset-x-14 top-0 truncate text-center leading-10 text-white ${variant === 'edit' ? 'text-[16px] font-medium' : 'text-[17px] font-normal'}`}>
        {title}
      </h1>
    </div>
  );
}

/** Handwritten title whose letters arrive one by one (08 §01). */
export function DisplayTitle({ text, className = '', delay = 0.15, stagger = 0.035 }: { text: string; className?: string; delay?: number; stagger?: number }) {
  const lines = text.split('\n');
  let i = 0;
  return (
    <h1 className={`app-display ${className}`} aria-label={text.replace(/\n/g, ' ')}>
      {lines.map((line, li) => (
        <span key={li} className="block" aria-hidden>
          {Array.from(line).map((ch, ci) => {
            const n = i++;
            return (
              <motion.span
                key={ci}
                className="inline-block whitespace-pre"
                initial={{ opacity: 0, y: 10, scale: 0.9 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                transition={{ delay: delay + n * stagger, duration: 0.42, ease: [0.2, 0.9, 0.3, 1.25] }}
              >
                {ch}
              </motion.span>
            );
          })}
        </span>
      ))}
    </h1>
  );
}

/** Bottom sheet with blurred page behind (11-invite.png, 31-blocklist-add-sheet.png). */
export function Sheet({
  open,
  onClose,
  children,
  label,
  className = '',
  tone = 'light',
}: {
  open: boolean;
  onClose: () => void;
  children: ReactNode;
  label: string;
  className?: string;
  tone?: 'light' | 'dark';
}) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);
  return (
    <AnimatePresence>
      {open && (
        <motion.div
          key="sheet"
          className="absolute inset-0 z-50 flex items-end"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.2 }}
        >
          <div
            className="absolute inset-0 bg-[rgba(10,30,70,0.12)]"
            style={{ backdropFilter: 'blur(8px)', WebkitBackdropFilter: 'blur(8px)' }}
            onClick={onClose}
            aria-hidden
          />
          <motion.div
            role="dialog"
            aria-modal
            aria-label={label}
            className={`relative max-h-[92%] w-full overflow-y-auto rounded-t-[28px] ${tone === 'light' ? 'app-sheet' : 'border-t border-white/10 bg-[rgba(10,25,40,0.86)] text-white backdrop-blur-xl'} ${className}`}
            initial={{ y: '100%' }}
            animate={{ y: 0 }}
            exit={{ y: '100%' }}
            transition={{ type: 'spring', damping: 34, stiffness: 340 }}
          >
            {children}
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}

export function SheetClose({ onClick, className = '' }: { onClick: () => void; className?: string }) {
  return (
    <button type="button" aria-label="Close" onClick={onClick} className={`grid size-8 place-items-center text-ink ${className}`}>
      <Close size={18} strokeWidth={2.2} />
    </button>
  );
}

/** Small top toast, auto-hides. */
export function useToast(): [ReactNode, (msg: string) => void] {
  const [msg, setMsg] = useState<string | null>(null);
  const timer = useRef<number>(0);
  const show = useCallback((m: string) => {
    setMsg(m);
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => setMsg(null), 2400);
  }, []);
  useEffect(() => () => window.clearTimeout(timer.current), []);
  const node = (
    <AnimatePresence>
      {msg && (
        <motion.div
          role="status"
          className="absolute inset-x-4 top-[70px] z-[60] rounded-[18px] bg-white/95 px-4 py-3 text-center text-[14px] font-bold leading-5 text-ink shadow-lg"
          initial={{ opacity: 0, y: -10 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -10 }}
        >
          {msg}
        </motion.div>
      )}
    </AnimatePresence>
  );
  return [node, show];
}

/** White 95% pill, ink 16/700 (Ditto primary). */
export function PrimaryButton({
  children,
  onClick,
  disabled,
  className = '',
  tone = 'ink',
}: {
  children: ReactNode;
  onClick?: () => void;
  disabled?: boolean;
  className?: string;
  tone?: 'ink' | 'pink';
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={`flex h-12 w-full items-center justify-center gap-2 rounded-full text-[16px] font-bold tracking-[-0.32px] transition-colors disabled:cursor-not-allowed ${
        disabled ? 'bg-white/30 text-[rgba(14,28,38,0.45)]' : `bg-white/95 ${tone === 'pink' ? 'text-[#ff2cab]' : 'text-ink'}`
      } ${className}`}
      style={{ backdropFilter: 'blur(10px)', WebkitBackdropFilter: 'blur(10px)' }}
    >
      {children}
    </button>
  );
}

/** One editable profile row (13 §2.1): 12px label at 0.9, 19/24 value, 19px pencil (stroke 1.7). */
export function ProfileRow({ id, label, value, ariaLabel, onClick }: { id: string; label: string; value: string; ariaLabel: string; onClick: () => void }) {
  return (
    <button type="button" id={`application-summary-${id}`} aria-label={ariaLabel} onClick={onClick} className="flex min-h-[84px] w-full items-center gap-3 px-5 py-[18px] text-left">
      <span className="min-w-0 flex-1">
        <span className="block text-[12px] leading-4 text-white/90">{label}</span>
        <span className={`mt-[5px] block whitespace-pre-line break-words text-[19px] leading-6 ${value ? 'text-white' : 'text-white/45'}`}>{value || '—'}</span>
      </span>
      <Pencil size={19} strokeWidth={1.7} className="shrink-0 text-white/90" />
    </button>
  );
}

export function NavCard({ title, children, onClick, className = '' }: { title: string; children?: ReactNode; onClick: () => void; className?: string }) {
  return (
    <button type="button" onClick={onClick} className={`app-card-lite block w-full px-5 pb-5 pt-[18px] text-left ${className}`}>
      <span className="flex items-center justify-between">
        <span className="app-display text-[26px] leading-8">{title}</span>
        <ChevronRight size={18} strokeWidth={2} className="text-white/90" />
      </span>
      {children}
    </button>
  );
}