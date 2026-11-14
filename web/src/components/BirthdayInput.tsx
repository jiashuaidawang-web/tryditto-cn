import { useState } from 'react';
import { pick, t, type Lang } from '../i18n/lang';
import { parseIsoDate } from '../onboarding/validate';
import { Helper, TextBar } from './Inputs';

const INVALID = t("That date doesn't look right. Use MM / DD / YYYY.", '这个日期好像不对，请按 年 / 月 / 日 填写，例如 2003 / 06 / 15');

/** en: MM / DD / YYYY (Ditto). zh: YYYY / MM / DD. Digits only; emits ISO 'YYYY-MM-DD' when complete and real. */
export function BirthdayInput({
  lang,
  value,
  placeholder,
  onChange,
  onSubmit,
}: {
  lang: Lang;
  value: unknown;
  placeholder: string;
  onChange: (iso: string | undefined) => void;
  onSubmit: () => void;
}) {
  const [digits, setDigits] = useState(() => {
    const m = typeof value === 'string' ? /^(\d{4})-(\d{2})-(\d{2})$/.exec(value) : null;
    if (!m) return '';
    return lang === 'en' ? `${m[2]}${m[3]}${m[1]}` : `${m[1]}${m[2]}${m[3]}`;
  });
  const cuts = lang === 'en' ? [2, 4] : [4, 6];
  const shown = digits
    .split('')
    .map((d, i) => (cuts.includes(i) ? ` / ${d}` : d))
    .join('');

  const toIso = (d: string) => {
    if (d.length !== 8) return undefined;
    const iso = lang === 'en' ? `${d.slice(4)}-${d.slice(0, 2)}-${d.slice(2, 4)}` : `${d.slice(0, 4)}-${d.slice(4, 6)}-${d.slice(6)}`;
    return parseIsoDate(iso) ? iso : undefined;
  };

  const valid = toIso(digits) !== undefined;

  return (
    <>
      {digits.length === 8 && !valid && <Helper className="mb-[14px]">{pick(INVALID, lang)}</Helper>}
      <TextBar
        value={shown}
        placeholder={placeholder}
        inputMode="numeric"
        autoComplete="bday"
        onChange={(raw) => {
          const d = raw.replace(/\D/g, '').slice(0, 8);
          setDigits(d);
          onChange(toIso(d));
        }}
        onSubmit={onSubmit}
        canSubmit={valid}
      />
    </>
  );
}
