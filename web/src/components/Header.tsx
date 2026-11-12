import { useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { useLang, t, pick } from '../i18n/lang';
import { progress as progressTokens } from '../motion/tokens';
import { ChevronLeft, Lock } from './icons';

const PRIVACY = {
  title: t('Ditto Privacy', '隐私说明'),
  body: t(
    "Preferences marked with a 🔒 lock icon are private, they're not included in ur profile shown to other users, and are only used to match u with the best person.",
    '带 🔒 的偏好是私密的，不会出现在别人能看到的资料里，只用来帮你匹配最合适的人。',
  ),
  ok: t('Got it', '知道了'),
};

function Avatar() {
  return (
    <img
      src="/brand/pigeon-avatar.jpg"
      alt=""
      aria-hidden
      draggable={false}
      className="block size-[38px] shrink-0 rounded-full object-cover"
    />
  );
}

export function Header({ onBack, fills }: { onBack?: () => void; fills: [number, number, number] }) {
  const lang = useLang();
  const [open, setOpen] = useState(false);
  return (
    <>
      <div className="absolute inset-x-0 top-0 z-20 h-[72px]">
        {onBack && (
          <button
            type="button"
            aria-label="Back"
            onClick={onBack}
            className="absolute left-[8px] top-[12px] grid size-10 place-items-center text-white"
          >
            <ChevronLeft size={22} strokeWidth={2.4} />
          </button>
        )}
        <div
          className="absolute left-1/2 top-[8px] flex h-[48px] -translate-x-1/2 items-center rounded-full pl-[5px] pr-[13px]"
          style={{
            background: 'rgba(255,255,255,0.16)',
            boxShadow: 'inset 0 0 0 1px rgba(255,255,255,0.38), inset 0 1px 6px rgba(255,255,255,0.18)',
            backdropFilter: 'blur(12px)',
            WebkitBackdropFilter: 'blur(12px)',
          }}
        >
          <Avatar />
          <span
            className={
              lang === 'en'
                ? 'ml-[10px] font-rounded text-[22px] leading-none tracking-[-0.2px] text-white'
                : 'ml-[9px] text-[19px] font-semibold leading-none tracking-[1px] text-white'
            }
          >
            {lang === 'en' ? 'Ditto' : '见一面'}
          </span>
        </div>
        <button
          type="button"
          aria-label="What the lock means"
          onClick={() => setOpen(true)}
          className="absolute right-[6px] top-[12px] grid size-10 place-items-center text-white"
        >
          <Lock size={24} strokeWidth={1.8} />
        </button>
        <ProgressBar fills={fills} />
      </div>
      <AnimatePresence>
        {open && (
          <motion.div
            className="absolute inset-0 z-50 flex items-end bg-black/40 p-4"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={() => setOpen(false)}
          >
            <motion.div
              role="dialog"
              aria-modal
              className="w-full rounded-[28px] bg-white/95 p-6 text-ink"
              initial={{ y: 40 }}
              animate={{ y: 0 }}
              exit={{ y: 40 }}
              onClick={(e) => e.stopPropagation()}
            >
              <div className="mb-2 flex items-center gap-2 text-[18px] font-bold">
                <Lock size={18} /> {pick(PRIVACY.title, lang)}
              </div>
              <p className="text-[15px] leading-[22px]">{pick(PRIVACY.body, lang)}</p>
              <button
                type="button"
                className="mt-5 h-12 w-full rounded-full bg-ink text-[16px] font-bold text-white"
                onClick={() => setOpen(false)}
              >
                {pick(PRIVACY.ok, lang)}
              </button>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </>
  );
}

/** 3 segments, each ~117×4 (09 §1): track white/20, fill white/66 with 0 0 3px white glow. */
export function ProgressBar({ fills }: { fills: [number, number, number] }) {
  return (
    <div className="absolute left-4 right-4 top-[64px] flex gap-1" aria-hidden>
      {fills.map((f, i) => (
        <div key={i} className="h-1 flex-1 overflow-hidden rounded-full bg-white/20">
          <div
            className="h-full rounded-full bg-white/66"
            style={{ width: `${f * 100}%`, boxShadow: '0 0 3px white', transition: progressTokens.transition }}
          />
        </div>
      ))}
    </div>
  );
}
