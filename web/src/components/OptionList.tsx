import { useRef, useState } from 'react';
import { motion } from 'motion/react';
import { pick, type Lang, type T } from '../i18n/lang';
import { pill } from '../motion/tokens';
import type { Option } from '../onboarding/types';
import { Check, Pencil } from './icons';
import { OptionPill } from './OptionPill';

export const CUSTOM_PREFIX = 'custom:';
export const isCustom = (v: string) => v.startsWith(CUSTOM_PREFIX);

interface Props {
  lang: Lang;
  options: Option[];
  multi: boolean;
  value: string | string[] | undefined;
  onChange: (value: string | string[]) => void;
  exclusive?: string[];
  maxSelect?: number;
  maxHint?: T;
  allowCustom?: T;
}

/** Right-aligned pill column with exclusivity, max-select and an optional pencil "Other" chip. */
export function OptionList({ lang, options, multi, value, onChange, exclusive = [], maxSelect, maxHint, allowCustom }: Props) {
  const selected: string[] = Array.isArray(value) ? value : typeof value === 'string' && value ? [value] : [];
  const atMax = multi && maxSelect !== undefined && selected.length >= maxSelect;

  const toggle = (id: string) => {
    if (!multi) {
      onChange(id);
      return;
    }
    if (selected.includes(id)) {
      onChange(selected.filter((s) => s !== id));
      return;
    }
    if (exclusive.includes(id)) {
      onChange([id]);
      return;
    }
    if (atMax) return;
    onChange([...selected.filter((s) => !exclusive.includes(s)), id]);
  };

  const addCustom = (text: string) => {
    const v = `${CUSTOM_PREFIX}${text}`;
    if (!multi) {
      onChange(v);
      return;
    }
    if (selected.includes(v) || atMax) return;
    onChange([...selected.filter((s) => !exclusive.includes(s)), v]);
  };

  const customs = selected.filter(isCustom);

  return (
    <div className="flex flex-col items-end gap-3" role={multi ? 'group' : 'radiogroup'}>
      {options.map((opt) => {
        const on = selected.includes(opt.id);
        return (
          <OptionPill
            key={opt.id}
            label={pick(opt.label, lang)}
            emoji={lang === 'zh' ? (opt.emojiZh ?? opt.emoji) : opt.emoji}
            selected={on}
            showCheck={multi}
            disabled={!on && atMax}
            onClick={() => toggle(opt.id)}
          />
        );
      })}
      {customs.map((c) => (
        <OptionPill
          key={c}
          label={c.slice(CUSTOM_PREFIX.length)}
          selected
          showCheck={false}
          onClick={() => toggle(c)}
          trailing={<Pencil size={16} className="ml-auto shrink-0 opacity-90" />}
        />
      ))}
      {allowCustom && (multi || customs.length === 0) && (
        <CustomChip placeholder={pick(allowCustom, lang)} disabled={atMax} onSubmit={addCustom} />
      )}
      {atMax && maxHint && <p className="pr-1 text-[12px] leading-4 text-white/80">{pick(maxHint, lang)}</p>}
    </div>
  );
}

function CustomChip({ placeholder, disabled, onSubmit }: { placeholder: string; disabled: boolean; onSubmit: (v: string) => void }) {
  const [editing, setEditing] = useState(false);
  const [text, setText] = useState('');
  const input = useRef<HTMLInputElement>(null);

  const commit = () => {
    const v = text.trim();
    if (v) onSubmit(v);
    setText('');
    setEditing(false);
  };

  return (
    <motion.div
      initial={false}
      animate={{ width: editing ? pill.widthSelected : pill.widthUnselected, opacity: disabled ? 0.4 : 1 }}
      transition={{ width: { duration: pill.widthDuration, ease: pill.widthEase } }}
      className="ob-glass flex min-h-12 shrink-0 items-center rounded-3xl pl-4 pr-[6px] text-[14px] leading-5 text-white"
      onClick={() => {
        if (disabled || editing) return;
        setEditing(true);
        requestAnimationFrame(() => input.current?.focus());
      }}
    >
      {editing ? (
        <>
          <input
            ref={input}
            value={text}
            maxLength={40}
            onChange={(e) => setText(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') commit();
              if (e.key === 'Escape') setEditing(false);
            }}
            onBlur={() => !text.trim() && setEditing(false)}
            placeholder={placeholder}
            className="ob-input min-w-0 flex-1 bg-transparent text-white outline-none"
          />
          <button
            type="button"
            aria-label="Add"
            onMouseDown={(e) => e.preventDefault()}
            onClick={commit}
            className="grid h-7 w-9 shrink-0 place-items-center rounded-full bg-white/15 text-white/80"
          >
            <Check size={14} />
          </button>
        </>
      ) : (
        <button type="button" className="flex w-full items-center pr-[10px] text-left" disabled={disabled}>
          <span className="flex-1 text-white/75">{placeholder}</span>
          <Pencil size={18} className="text-white/85" />
        </button>
      )}
    </motion.div>
  );
}
