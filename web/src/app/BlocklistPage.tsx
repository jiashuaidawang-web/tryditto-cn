import { useState, type ReactNode } from 'react';
import { addBlock, deleteBlock, getBlocks, type BlockEntry } from '../api';
import { FileText, Mail, Phone, ShieldOff, Trash, UserIcon } from '../components/icons';
import { pick, t, useLang, type Lang } from '../i18n/lang';
import { AppScreen, PrimaryButton, Sheet, SheetClose, TopBar, useGo, useLive, useToast } from './ui';

const C = {
  title: t('Blocklist', '屏蔽名单'),
  back: t('Back to profile', '返回'),
  empty: t('No one on your blocklist yet', '屏蔽名单还是空的'),
  add: t('Add block', '添加屏蔽'),
  name: t('Name', '称呼'),
  namePh: t('e.g. Alex from college', '比如：高中同桌小王'),
  emails: t('Emails', '邮箱'),
  emailPh: t('someone@example.com', 'someone@example.com'),
  phones: t('Phones', '手机号'),
  phonePh: t('(415) 555-1234', '138 0000 0000'),
  note: t('Note', '备注'),
  notePh: t('A quick reminder for future-you.', '给以后的自己留个提醒。'),
  addRow: t('Add', '添加'),
  block: t('Block', '屏蔽'),
  cancel: t('Cancel', '取消'),
  need: t('Add an email or phone number so Ditto can recognise them.', '至少填一个邮箱或手机号，我们才能认出这个人。'),
  hint: t("We only use these to keep you from being matched. They're never contacted.", '这些信息只用于避免匹配到对方，我们不会联系 ta。'),
  blocked: t('Blocked. You will never be matched.', '已屏蔽，你们不会被匹配到。'),
  remove: t('Remove', '移除'),
  unnamed: t('No name', '未命名'),
};

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
const validPhone = (lang: Lang, v: string) => (lang === 'zh' ? /^1[3-9]\d{9}$/.test(v.replace(/[\s-]/g, '')) : v.replace(/\D/g, '').length >= 7);

/** `/blocklist` (10-blocklist.png, 13 §6). */
export function BlocklistPage() {
  const lang = useLang();
  const go = useGo();
  const blocks = useLive(getBlocks);
  const [open, setOpen] = useState(false);
  const [toast, showToast] = useToast();

  return (
    <AppScreen>
      <TopBar title={pick(C.title, lang)} onBack={() => go('/you')} backLabel={pick(C.back, lang)} />
      <div className="ob-scroll absolute inset-x-0 bottom-0 top-[80px] px-4 pb-10">
        {blocks && blocks.length === 0 && (
          <div className="app-card flex h-[163px] flex-col items-center justify-center gap-[17px]">
            <span className="grid size-16 place-items-center rounded-full bg-white/15 text-white/70">
              <ShieldOff size={34} strokeWidth={1.6} />
            </span>
            <span className="text-[16px] font-medium">{pick(C.empty, lang)}</span>
          </div>
        )}
        {blocks && blocks.length > 0 && (
          <div className="app-card divide-y divide-white/15 py-1">
            {blocks.map((b) => (
              <BlockRow key={b.id} entry={b} lang={lang} />
            ))}
          </div>
        )}
        <PrimaryButton tone="pink" className="mt-[17px]" onClick={() => setOpen(true)}>
          {pick(C.add, lang)}
        </PrimaryButton>
      </div>
      <AddBlockSheet
        open={open}
        lang={lang}
        onClose={() => setOpen(false)}
        onBlocked={() => {
          setOpen(false);
          showToast(pick(C.blocked, lang));
        }}
      />
      {toast}
    </AppScreen>
  );
}

function BlockRow({ entry, lang }: { entry: BlockEntry; lang: Lang }) {
  const contacts = [...entry.emails, ...entry.phones].join(' · ');
  return (
    <div className="flex items-center gap-3 px-5 py-4">
      <div className="min-w-0 flex-1">
        <div className="truncate text-[17px] leading-6">{entry.name || pick(C.unnamed, lang)}</div>
        <div className="truncate text-[13px] leading-[18px] text-white/80">{contacts}</div>
        {entry.note && <div className="mt-1 truncate text-[12px] leading-4 text-white/60">{entry.note}</div>}
      </div>
      <button type="button" aria-label={pick(C.remove, lang)} onClick={() => void deleteBlock(entry.id)} className="grid size-9 place-items-center text-white/80">
        <Trash size={18} strokeWidth={1.7} />
      </button>
    </div>
  );
}

function Label({ icon, children, action }: { icon: ReactNode; children: string; action?: ReactNode }) {
  return (
    <div className="mb-[8px] flex items-center justify-between text-[14px] text-ink/70">
      <span className="flex items-center gap-[8px]">
        {icon}
        {children}
      </span>
      {action}
    </div>
  );
}

function MultiInput({
  values,
  onChange,
  placeholder,
  type,
  inputMode,
  invalid,
}: {
  values: string[];
  onChange: (v: string[]) => void;
  placeholder: string;
  type: string;
  inputMode: 'email' | 'tel';
  invalid: (v: string) => boolean;
}) {
  return (
    <div className="flex flex-col gap-2">
      {values.map((v, i) => (
        <input
          key={i}
          value={v}
          type={type}
          inputMode={inputMode}
          autoComplete="off"
          placeholder={placeholder}
          onChange={(e) => onChange(values.map((x, j) => (j === i ? e.target.value : x)))}
          className={`app-field h-10 w-full px-3 text-[16px] outline-none ${v && invalid(v) ? '!border-[#ff2cab]' : ''}`}
        />
      ))}
    </div>
  );
}

/** Add-block sheet (31-blocklist-add-sheet.png). `Block` stays disabled until there is a valid email or phone. */
function AddBlockSheet({ open, lang, onClose, onBlocked }: { open: boolean; lang: Lang; onClose: () => void; onBlocked: () => void }) {
  const [name, setName] = useState('');
  const [emails, setEmails] = useState(['']);
  const [phones, setPhones] = useState(['']);
  const [note, setNote] = useState('');
  const goodEmails = emails.map((e) => e.trim()).filter((e) => EMAIL.test(e));
  const goodPhones = phones.map((p) => p.trim()).filter((p) => validPhone(lang, p));
  const canBlock = goodEmails.length + goodPhones.length > 0;
  const reset = () => {
    setName('');
    setEmails(['']);
    setPhones(['']);
    setNote('');
  };
  const close = () => {
    reset();
    onClose();
  };
  const addAction = (label: string, onClick: () => void) => (
    <button type="button" aria-label={label} onClick={onClick} className="text-[14px] text-ink">
      {pick(C.addRow, lang)}
    </button>
  );

  return (
    <Sheet open={open} onClose={close} label={pick(C.add, lang)} className="min-h-[50dvh]">
      <form
        className="px-6 pb-6 pt-6"
        onSubmit={(e) => {
          e.preventDefault();
          if (!canBlock) return;
          void addBlock({ name: name.trim(), emails: goodEmails, phones: goodPhones, note: note.trim() }).then(() => {
            reset();
            onBlocked();
          });
        }}
      >
        <SheetClose onClick={close} className="-ml-[6px] -mt-[2px]" />
        <h2 className="-mt-[2px] mb-[18px] text-center text-[24px] font-bold tracking-[-0.01em]">{pick(C.add, lang)}</h2>

        <Label icon={<UserIcon size={15} strokeWidth={1.8} />}>{pick(C.name, lang)}</Label>
        <input
          id="blocklist-entry-form-name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          autoComplete="off"
          placeholder={pick(C.namePh, lang)}
          className="app-field h-10 w-full px-3 text-[16px] outline-none"
        />

        <div className="h-[24px]" />
        <Label icon={<Mail size={15} strokeWidth={1.8} />} action={addAction('Add email', () => setEmails([...emails, '']))}>
          {pick(C.emails, lang)}
        </Label>
        <MultiInput values={emails} onChange={setEmails} placeholder={pick(C.emailPh, lang)} type="email" inputMode="email" invalid={(v) => !EMAIL.test(v.trim())} />

        <div className="h-[24px]" />
        <Label icon={<Phone size={15} strokeWidth={1.8} />} action={addAction('Add phone', () => setPhones([...phones, '']))}>
          {pick(C.phones, lang)}
        </Label>
        <MultiInput values={phones} onChange={setPhones} placeholder={pick(C.phonePh, lang)} type="tel" inputMode="tel" invalid={(v) => !validPhone(lang, v.trim())} />

        <div className="h-[24px]" />
        <Label icon={<FileText size={15} strokeWidth={1.8} />}>{pick(C.note, lang)}</Label>
        <textarea
          id="blocklist-entry-form-note"
          value={note}
          onChange={(e) => setNote(e.target.value)}
          placeholder={pick(C.notePh, lang)}
          rows={2}
          className="app-field block h-[66px] w-full resize-y px-3 py-2 text-[16px] outline-none"
        />

        <p className="mt-4 text-center text-[12px] leading-4 text-ink/55">{pick(canBlock ? C.hint : C.need, lang)}</p>
        <button
          type="submit"
          disabled={!canBlock}
          className="mt-4 h-12 w-full rounded-full bg-ink text-[16px] font-bold text-white transition-opacity disabled:opacity-35"
        >
          {pick(C.block, lang)}
        </button>
        <button type="button" onClick={close} className="mt-[10px] h-10 w-full text-[16px] font-bold text-ink">
          {pick(C.cancel, lang)}
        </button>
      </form>
    </Sheet>
  );
}
