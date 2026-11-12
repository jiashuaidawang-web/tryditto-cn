import type { ReactNode } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { pill } from '../motion/tokens';
import { Check } from './icons';

export function PillCheck() {
  return (
    <motion.span
      className="ml-auto grid size-[18px] shrink-0 place-items-center rounded-full bg-white text-ditto-pink"
      initial={{ opacity: 0, scale: 0.6 }}
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0, scale: 0.6, transition: { duration: 0.1 } }}
      transition={{ duration: pill.checkDuration, delay: pill.checkDelay, ease: 'easeOut' }}
    >
      <Check size={11} />
    </motion.span>
  );
}

export function OptionPill({
  label,
  emoji,
  selected,
  showCheck,
  disabled,
  trailing,
  widths = [pill.widthUnselected, pill.widthSelected],
  center = false,
  onClick,
}: {
  label: ReactNode;
  emoji?: string;
  selected: boolean;
  /** Multi-select pills show a white check when selected; single-select pills don't. */
  showCheck: boolean;
  disabled?: boolean;
  trailing?: ReactNode;
  /** [unselected, selected] px. Default 208 → 260 (grows leftwards in a right-aligned column). */
  widths?: [number, number];
  center?: boolean;
  onClick: () => void;
}) {
  return (
    <motion.button
      type="button"
      role={showCheck ? 'checkbox' : 'radio'}
      aria-checked={selected}
      aria-disabled={disabled || undefined}
      onClick={() => !disabled && onClick()}
      initial={false}
      animate={{ width: selected ? widths[1] : widths[0], opacity: disabled ? 0.4 : 1 }}
      transition={{ width: { duration: pill.widthDuration, ease: pill.widthEase }, opacity: { duration: 0.2 } }}
      className={`ob-glass flex min-h-12 shrink-0 items-center rounded-3xl px-4 py-2 text-left text-[14px] leading-5 text-white ${
        selected ? 'font-bold' : 'font-normal'
      } ${center ? 'justify-center' : ''}`}
      style={{
        // The list column is masked (bottom fade) → it is a backdrop root and backdrop brightness() sees nothing,
        // so the tint alone must reproduce Ditto's ≈0.62× darkening of the sky behind the pill.
        backgroundColor: selected ? '#E40089' : 'rgba(0, 8, 28, 0.38)',
        boxShadow: selected
          ? 'inset 0 0 8px rgba(255,255,255,0.15), inset 0 0 0 1px rgba(255,255,255,0.2)'
          : 'inset 0 1px 3px rgba(255,255,255,0.12)',
        transition: `background-color ${pill.colorDuration}s ${pill.colorEase}, box-shadow ${pill.colorDuration}s ${pill.colorEase}`,
      }}
    >
      {emoji && <span className="mr-2 inline-block w-4 shrink-0 text-center text-[14px] leading-5">{emoji}</span>}
      <span className={center ? 'min-w-0' : 'min-w-0 flex-1 pr-2'}>{label}</span>
      <AnimatePresence initial={false}>{showCheck && selected && <PillCheck key="check" />}</AnimatePresence>
      {trailing}
    </motion.button>
  );
}
