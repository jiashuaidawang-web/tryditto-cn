import { forwardRef, type ReactNode } from 'react';
import { motion } from 'motion/react';
import { useA11y } from '../i18n/a11y';
import { nextButton } from '../motion/tokens';
import { ArrowRight, Lock, PaperPlane } from './icons';

/** Round Next / Send. Enabled: white 95% + ink arrow. Disabled (aria-disabled): white 30% + 45% ink, blur 10. */
export function RoundButton({
  enabled,
  onClick,
  label = 'Next',
  className = '',
}: {
  enabled: boolean;
  onClick: () => void;
  label?: 'Next' | 'Send';
  className?: string;
}) {
  const a11y = useA11y();
  return (
    <button
      type="button"
      aria-label={label === 'Send' ? a11y.send : a11y.next}
      data-action={label.toLowerCase()}
      aria-disabled={!enabled}
      onClick={() => enabled && onClick()}
      className={`grid h-12 w-[72px] place-items-center rounded-full ${className}`}
      style={{
        backgroundColor: enabled ? 'rgba(255,255,255,0.95)' : 'rgba(255,255,255,0.3)',
        color: enabled ? 'rgb(2,43,78)' : 'rgba(14,28,38,0.45)',
        backdropFilter: 'blur(10px)',
        WebkitBackdropFilter: 'blur(10px)',
        transition: nextButton.colorTransition,
      }}
    >
      <ArrowRight size={22} strokeWidth={2} />
    </button>
  );
}

/** The standalone bottom-right Next: does not slide with the column; fades + scales in place (12 §4.1 item 5). */
export function FloatingNext({ enabled, onClick }: { enabled: boolean; onClick: () => void }) {
  return (
    <motion.div
      className="absolute bottom-8 right-4 z-10"
      variants={{
        initial: { opacity: 0, scale: nextButton.enterScaleFrom },
        enter: { opacity: 1, scale: 1, transition: { duration: nextButton.enterDuration, ease: 'easeOut', delay: 0.1 } },
        exit: { opacity: 0, transition: { duration: 0.04 } },
      }}
    >
      <RoundButton enabled={enabled} onClick={onClick} />
    </motion.div>
  );
}

export const TextBar = forwardRef<
  HTMLInputElement,
  {
    value: string;
    placeholder: string;
    onChange: (v: string) => void;
    onSubmit: () => void;
    canSubmit: boolean;
    inputMode?: 'text' | 'email' | 'numeric';
    type?: string;
    maxLength?: number;
    autoComplete?: string;
  }
>(function TextBar({ value, placeholder, onChange, onSubmit, canSubmit, inputMode, type = 'text', maxLength, autoComplete }, ref) {
  return (
    <div className="ob-dock flex h-[70px] items-center rounded-full pl-6 pr-[11px]">
      <input
        ref={ref}
        value={value}
        type={type}
        inputMode={inputMode}
        maxLength={maxLength}
        autoComplete={autoComplete ?? 'off'}
        placeholder={placeholder}
        onChange={(e) => onChange(e.target.value)}
        onKeyDown={(e) => e.key === 'Enter' && canSubmit && onSubmit()}
        className="ob-input min-w-0 flex-1 bg-transparent text-[16px] text-white outline-none"
      />
      <RoundButton enabled={canSubmit} onClick={onSubmit} label="Send" className="ml-3 shrink-0" />
    </div>
  );
});

/** Edit-mode submit (13 §3): 96×48 white pill, ink 16/700, blur 10. Always tappable unless `enabled` is false. */
export function DoneButton({
  onClick,
  enabled = true,
  label = 'Done',
  className = '',
}: {
  onClick: () => void;
  enabled?: boolean;
  label?: string;
  className?: string;
}) {
  return (
    <button
      type="button"
      aria-disabled={!enabled}
      onClick={() => enabled && onClick()}
      className={`grid h-12 min-w-24 place-items-center rounded-full px-5 text-[16px] font-bold ${className}`}
      style={{
        backgroundColor: enabled ? 'rgba(255,255,255,0.95)' : 'rgba(255,255,255,0.3)',
        color: enabled ? 'rgb(2,43,78)' : 'rgba(14,28,38,0.45)',
        backdropFilter: 'blur(10px)',
        WebkitBackdropFilter: 'blur(10px)',
        transition: nextButton.colorTransition,
      }}
    >
      {label}
    </button>
  );
}

export function TextAreaBox({
  value,
  placeholder,
  onChange,
  onSubmit,
  canSubmit,
  maxLength,
  doneLabel,
}: {
  value: string;
  placeholder: string;
  onChange: (v: string) => void;
  onSubmit: () => void;
  canSubmit: boolean;
  maxLength?: number;
  /** Edit mode renders a `Done` pill inside the box instead of the round Send (13 §01). */
  doneLabel?: string;
}) {
  if (doneLabel) {
    return (
      <div className="ob-dock relative h-[156px] rounded-[24px]">
        <textarea
          value={value}
          maxLength={maxLength}
          placeholder={placeholder}
          onChange={(e) => onChange(e.target.value)}
          className="ob-input absolute inset-0 resize-none bg-transparent px-5 pb-[72px] pt-[16px] text-[16px] leading-5 text-white outline-none"
        />
        <DoneButton label={doneLabel} enabled={canSubmit} onClick={onSubmit} className="absolute bottom-4 right-4" />
      </div>
    );
  }
  return (
    <div className="ob-dock relative h-[156px] rounded-[24px]">
      <textarea
        value={value}
        maxLength={maxLength}
        placeholder={placeholder}
        onChange={(e) => onChange(e.target.value)}
        className="ob-input absolute inset-0 resize-none bg-transparent px-5 pb-[72px] pt-[14px] text-[16px] leading-5 text-white outline-none"
      />
      <RoundButton enabled={canSubmit} onClick={onSubmit} label="Send" className="absolute bottom-4 right-4" />
    </div>
  );
}

export function PrivateNote({ children }: { children: ReactNode }) {
  return (
    <div className="flex h-5 items-center justify-end gap-[5px] pr-1 text-[12px] leading-4 text-white/90">
      <Lock size={13} strokeWidth={2} />
      <span>{children}</span>
    </div>
  );
}

export function Helper({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <p className={`text-center text-[12px] leading-4 text-white/90 ${className}`}>{children}</p>;
}

export function SendHint({ children }: { children: ReactNode }) {
  return (
    <div className="flex items-center justify-end gap-[6px] pr-[10px] text-[12px] leading-4 text-white/60">
      <PaperPlane size={14} />
      <span>{children}</span>
    </div>
  );
}
