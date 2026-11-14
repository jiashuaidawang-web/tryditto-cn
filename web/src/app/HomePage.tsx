import { useState, type ReactNode } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { avatarUri, enableMatchAlerts, formatSlot, getContact, getCurrentMatch, getInvite, sceneUri, type MatchView } from '../api';
import { Confetti } from '../components/Confetti';
import { BookOpen, Close, ContactCard, Copy, Gear, HeartArrow, ImageIcon, LinkIcon, Mail, MenuIcon, MessageCircle, Share } from '../components/icons';
import { useA11y } from '../i18n/a11y';
import { pick, t, useLang, type Lang } from '../i18n/lang';
import { NextDrop } from './Countdown';
import { AppScreen, DisplayTitle, PrimaryButton, Sheet, SheetClose, useGo, useLive, useToast } from './ui';

const C = {
  invite: t('Invite', '邀请'),
  finding: t('Ditto is finding\nyou a date!', '见一面正在\n帮你找约会对象！'),
  message: t('Message Ditto', '给见一面发消息'),
  saveContact: t("Save Ditto's Contact", '保存见一面名片'),
  contactUs: t('Contact Us', '联系我们'),
  account: t('Account Settings', '账号设置'),
  inviteTitle: t('Invite friends to secure match', '邀请好友，锁定下周匹配'),
  inviteBody: t('Once they finish onboarding, your match next week is locked in.', '好友完成入驻后，你下周的匹配就锁定了。'),
  copied: t('Link copied', '链接已复制'),
  alertsTitle: t('Be first to know when you match 💌', '匹配出来，第一时间告诉你 💌'),
  alertsBody: t(
    "We'll text you the moment your next match is ready. No app needed.",
    '下一位约会对象一出来，我们就发短信提醒你，不用一直盯着页面。',
  ),
  alertsCta: t('Turn on text alerts', '开启短信提醒'),
  alertsOn: t("You're all set. We'll text you on Wednesday.", '已开启，周三匹配出来就提醒你。'),
  soon: t('Coming soon in the demo', '演示版暂未开放'),
};

/** `/home` (01-home.png, 12-menu.png, 11-invite.png) + the weekly match loop states (推断设计). */
export function HomePage() {
  const lang = useLang();
  const view = useLive(getCurrentMatch);
  const [menuOpen, setMenuOpen] = useState(false);
  const [inviteOpen, setInviteOpen] = useState(false);
  const [alertsOpen, setAlertsOpen] = useState(false);
  const [toast, showToast] = useToast();

  return (
    <AppScreen tab="ditto">
      <HomeHeader lang={lang} menuOpen={menuOpen} onMenu={() => setMenuOpen((v) => !v)} onInvite={() => setInviteOpen(true)} />

      <div className="absolute inset-x-0 bottom-[150px] top-[80px]">{view && <StageView view={view} lang={lang} />}</div>

      <button
        type="button"
        onClick={() => setAlertsOpen(true)}
        className="app-glass absolute inset-x-[18px] bottom-[97px] flex h-[46px] items-center justify-center gap-[10px] rounded-full text-[16px] text-white"
      >
        <span className="grid size-[20px] place-items-center rounded-[5px] bg-[#34c759]">
          <MessageCircle size={13} strokeWidth={2.4} className="text-white" />
        </span>
        {pick(C.message, lang)}
      </button>

      <MenuPanel
        open={menuOpen}
        lang={lang}
        onClose={() => setMenuOpen(false)}
        onMessage={() => {
          setMenuOpen(false);
          setAlertsOpen(true);
        }}
      />
      <InviteSheet open={inviteOpen} lang={lang} onClose={() => setInviteOpen(false)} toast={showToast} />
      <Sheet open={alertsOpen} onClose={() => setAlertsOpen(false)} label={pick(C.alertsTitle, lang)}>
        <div className="px-6 pb-8 pt-5">
          <SheetClose onClick={() => setAlertsOpen(false)} className="-ml-1" />
          <h2 className="mt-3 text-center text-[22px] font-bold leading-7">{pick(C.alertsTitle, lang)}</h2>
          <p className="mt-3 text-center text-[15px] leading-[22px] text-ink/70">{pick(C.alertsBody, lang)}</p>
          <button
            type="button"
            className="mt-6 h-12 w-full rounded-full bg-ink text-[16px] font-bold text-white"
            onClick={() => {
              void enableMatchAlerts().then(() => {
                setAlertsOpen(false);
                showToast(pick(C.alertsOn, lang));
              });
            }}
          >
            {pick(C.alertsCta, lang)}
          </button>
        </div>
      </Sheet>
      {toast}
    </AppScreen>
  );
}

function HomeHeader({ lang, menuOpen, onMenu, onInvite }: { lang: Lang; menuOpen: boolean; onMenu: () => void; onInvite: () => void }) {
  const a11y = useA11y();
  return (
    <div className="absolute inset-x-0 top-0 z-40 h-[80px]">
      <div
        className={`absolute left-[22px] top-[27px] font-sans font-black leading-none text-white ${lang === 'en' ? 'text-[35px] tracking-[-1.5px]' : 'text-[29px] tracking-[1px]'}`}
        aria-label={lang === 'en' ? 'Ditto' : '见一面'}
      >
        {lang === 'en' ? 'Ditto' : '见一面'}
      </div>
      <button
        type="button"
        onClick={onInvite}
        className="app-glass absolute right-[64px] top-[25px] flex h-[38px] items-center gap-[6px] rounded-full pl-[10px] pr-[14px] text-[15px] text-white"
      >
        <HeartArrow size={21} strokeWidth={1.6} />
        {pick(C.invite, lang)}
      </button>
      <button
        type="button"
        aria-label={menuOpen ? a11y.closeMenu : a11y.menu}
        data-action="menu"
        aria-expanded={menuOpen}
        onClick={onMenu}
        className="app-glass absolute right-[16px] top-[25px] grid size-[38px] place-items-center rounded-full text-white"
      >
        {menuOpen ? <Close size={18} strokeWidth={2.2} /> : <MenuIcon size={20} strokeWidth={2} />}
      </button>
    </div>
  );
}

function downloadVcard(lang: Lang) {
  const vcf = ['BEGIN:VCARD', 'VERSION:3.0', `FN:${lang === 'en' ? 'Ditto (见一面)' : '见一面'}`, 'TEL;TYPE=CELL:+8610690000000', 'EMAIL:hi@jianyimian.cn', 'END:VCARD'].join('\n');
  const url = URL.createObjectURL(new Blob([vcf], { type: 'text/vcard' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = 'jianyimian.vcf';
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/** ☰ dropdown (08 §11): Message Ditto, Save Ditto's Contact (vcf), Contact Us (mailto), Account Settings. */
function MenuPanel({ open, lang, onClose, onMessage }: { open: boolean; lang: Lang; onClose: () => void; onMessage: () => void }) {
  const go = useGo();
  const items: { label: string; Icon: typeof Mail; onClick: () => void }[] = [
    { label: pick(C.message, lang), Icon: MessageCircle, onClick: onMessage },
    { label: pick(C.saveContact, lang), Icon: ContactCard, onClick: () => (downloadVcard(lang), onClose()) },
    { label: pick(C.contactUs, lang), Icon: Mail, onClick: () => ((window.location.href = 'mailto:hi@jianyimian.cn'), onClose()) },
    { label: pick(C.account, lang), Icon: Gear, onClick: () => go('/account') },
  ];
  return (
    <AnimatePresence>
      {open && (
        <>
          <div className="absolute inset-0 z-30" onClick={onClose} aria-hidden />
          <motion.div
            role="menu"
            className="app-menu absolute right-[16px] top-[72px] z-40 w-[210px] origin-top-right rounded-[20px] py-[6px]"
            initial={{ opacity: 0, scale: 0.94, y: -6 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.96, y: -4 }}
            transition={{ duration: 0.16, ease: 'easeOut' }}
          >
            {items.map(({ label, Icon, onClick }) => (
              <button key={label} type="button" role="menuitem" onClick={onClick} className="flex h-[41px] w-full items-center gap-[8px] px-[20px] text-left text-[15px] text-white">
                <Icon size={14} strokeWidth={1.8} className="shrink-0" />
                {label}
              </button>
            ))}
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}

const Instagram = ({ size = 24 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} aria-hidden>
    <rect x="3" y="3" width="18" height="18" rx="5" />
    <circle cx="12" cy="12" r="4" />
    <circle cx="17.3" cy="6.7" r="0.6" fill="currentColor" />
  </svg>
);

/** Invite bottom sheet (11-invite.png). zh swaps the share targets for copy / WeChat / RedNote / poster (08 §10). */
function InviteSheet({ open, lang, onClose, toast }: { open: boolean; lang: Lang; onClose: () => void; toast: (m: string) => void }) {
  const invite = useLive(getInvite);
  const url = invite?.url ?? '';
  const copy = () => {
    void navigator.clipboard?.writeText(`https://${url}`).catch(() => undefined);
    toast(pick(C.copied, lang));
  };
  const share = () => {
    if (navigator.share) void navigator.share({ url: `https://${url}` }).catch(() => undefined);
    else copy();
  };
  const actions: { label: string; icon: ReactNode; onClick: () => void }[] =
    lang === 'en'
      ? [
          { label: 'copy', icon: <LinkIcon size={24} strokeWidth={1.7} />, onClick: copy },
          { label: 'share', icon: <Share size={24} strokeWidth={1.7} />, onClick: share },
          { label: 'messages', icon: <MessageCircle size={24} strokeWidth={1.7} />, onClick: () => (window.location.href = `sms:&body=${encodeURIComponent(`https://${url}`)}`) },
          { label: 'instagram', icon: <Instagram />, onClick: () => toast(pick(C.soon, lang)) },
        ]
      : [
          { label: '复制链接', icon: <LinkIcon size={24} strokeWidth={1.7} />, onClick: copy },
          { label: '微信', icon: <MessageCircle size={24} strokeWidth={1.7} />, onClick: share },
          { label: '小红书', icon: <BookOpen size={24} strokeWidth={1.7} />, onClick: () => toast(pick(C.soon, lang)) },
          { label: '生成海报', icon: <ImageIcon size={24} strokeWidth={1.7} />, onClick: () => toast(pick(C.soon, lang)) },
        ];
  return (
    <Sheet open={open} onClose={onClose} label={pick(C.inviteTitle, lang)}>
      <div className="px-6 pb-[34px] pt-[22px]">
        <SheetClose onClick={onClose} className="-ml-[3px]" />
        <img src="/app/invite-pigeon.png" alt="" className="mx-auto mt-[6px] block size-[164px] rounded-full" draggable={false} />
        <h2 className="mt-[22px] text-center text-[22px] font-bold leading-7 tracking-[-0.2px]">{pick(C.inviteTitle, lang)}</h2>
        <p className="mx-auto mt-[10px] max-w-[310px] text-center text-[16px] leading-[22px] text-ink/65">{pick(C.inviteBody, lang)}</p>
        <button type="button" onClick={copy} className="mt-[26px] flex h-[50px] w-full items-center justify-center rounded-[14px] border border-ink/10 bg-ink/[0.07] px-4 text-[15px] text-ink/85">
          <span className="truncate">{url}</span>
        </button>
        <div className="mt-[24px] grid grid-cols-4">
          {actions.map((a) => (
            <button key={a.label} type="button" onClick={a.onClick} className="flex flex-col items-center gap-[8px] text-[13px] text-ink">
              {a.icon}
              {a.label}
            </button>
          ))}
        </div>
      </div>
    </Sheet>
  );
}

// ───────────────────────── Match loop on /home (推断设计) ─────────────────────────

const S = {
  emptyTitle: t('No match\nthis week', '这周\n先不凑合'),
  emptyBody: t("We didn't find someone good enough. We'd rather wait than send you a so-so date.", '这周没找到足够合适的人。宁可等一等，也不给你凑合一个。'),
  revealedTitle: t('Your date\nis here!', '你的约会对象\n来啦！'),
  replyBy: t('Reply by Thu 10pm', '周四 22:00 前回复'),
  meet: t('Meet {name}', '看看{name}'),
  declinedTitle: t('Got it.', '收到。'),
  declinedBody: t("Thanks for telling us why. {name} won't see it — we'll use it for next Wednesday.", '谢谢你告诉我们原因，不会转告{name}。下周三会参考它重新帮你匹配。'),
  waitingTitle: t('Waiting for\n{name}', '等{name}\n选时间'),
  waitingBody: t("You picked {n}. We'll text you the moment they pick one.", '你选了 {n} 个时间，ta 选好后我们第一时间通知你。'),
  confirmedTitle: t("It's a date!", '约好啦！'),
  dayTitle: t("Today's the day", '就是今天！'),
  checkedIn: t("You're checked in. {name} knows you're here.", '已签到，{name}知道你到了。'),
  details: t('Date details', '查看约会详情'),
  checkinNote: t('Check-in opens 2 hours before.', '开始前 2 小时开放签到。'),
  feedbackTitle: t('How did\nit go?', '约会\n怎么样？'),
  feedbackBody: t("30 seconds. {name} never sees your answers.", '花 30 秒告诉我们，你的回答{name}看不到。'),
  feedbackCta: t('Leave feedback', '填写反馈'),
  thanksTitle: t('Thanks for\nthe feedback!', '收到你的\n反馈啦！'),
  thanksBody: t("If you both want to see each other again, we'll swap WeChat. Waiting on {name}.", '如果你们都想再见一面，我们会帮你们交换微信。正在等{name}的回答。'),
  mutualTitle: t("It's mutual!", '双向奔赴！'),
  mutualBody: t('You both want a second date. Say hi within 24 hours.', '你们都想再见一面。24 小时内打个招呼吧。'),
  wechatOf: t("{name}'s WeChat", '{name}的微信'),
  placeholder: t('Demo placeholder ID', '演示用占位微信号'),
  copy: t('Copy', '复制'),
  copied: t('Copied', '已复制'),
  closedTitle: t('Thanks for\nshowing up', '谢谢你\n认真赴约'),
  closedBody: t("This one wasn't mutual, and that's okay. Every date teaches us more about your type.", '这次没有双向，没关系。每一次约会都让我们更懂你的类型。'),
  reportedTitle: t('Thanks for\ntelling us', '谢谢你\n告诉我们'),
  reportedBody: t(
    'This match is closed. Our safety team reviews every report within 24 hours. In an emergency, call 110.',
    '这次匹配已关闭，安全团队会在 24 小时内处理。如遇紧急情况，请立即拨打 110。',
  ),
};

const fill = (copy: string, vars: Record<string, string | number>) => copy.replace(/\{(\w+)\}/g, (_, k: string) => String(vars[k] ?? ''));

function Dove({ size = 152, className = '' }: { size?: number; className?: string }) {
  const h = (size * 364) / 358;
  return (
    <motion.div
      className={`app-dove mx-auto ${className}`}
      style={{ width: size, height: h, backgroundSize: `${size * 17}px ${h}px`, ['--dove-w' as string]: `${size}px` }}
      animate={{ y: [0, -8, 0] }}
      transition={{ duration: 2.4, repeat: Infinity, ease: 'easeInOut' }}
      aria-hidden
    />
  );
}

function Avatar({ view, size, ring }: { view: MatchView; size: number; ring?: boolean }) {
  return (
    <div className="relative mx-auto" style={{ width: size, height: size }}>
      {ring && <span className="app-pulse-ring absolute inset-0 rounded-full border-2 border-[#ff7fd0]" aria-hidden />}
      <img src={avatarUri(view.partner.avatar)} alt="" className="relative size-full rounded-full border-[3px] border-white/90 object-cover shadow-lg" />
    </div>
  );
}

function SlotTicket({ view, lang }: { view: MatchView; lang: Lang }) {
  const { match, partner } = view;
  const slot = match.slots.find((s) => s.id === match.confirmedSlotId) ?? match.slots[0]!;
  const f = formatSlot(slot.startsAt, lang);
  return (
    <div className="app-card-lite mx-5 flex items-center gap-4 px-4 py-4 text-left">
      <img src={avatarUri(partner.avatar)} alt="" className="size-[56px] shrink-0 rounded-full border-2 border-white/80" />
      <div className="min-w-0">
        <div className="text-[13px] leading-4 text-white/80">{pick(partner.name, lang)}</div>
        <div className="text-[20px] font-bold leading-[26px]">
          {f.day} · {f.time}
        </div>
        <div className="truncate text-[14px] leading-5 text-white/90">{pick(partner.venue.name, lang)}</div>
      </div>
    </div>
  );
}

function StageView({ view, lang }: { view: MatchView; lang: Lang }) {
  const go = useGo();
  const { match, partner } = view;
  const name = pick(partner.name, lang);
  const v = { name, n: match.mySlotIds.length };
  const body = (copy: ReturnType<typeof t>) => <p className="mx-auto mt-4 max-w-[310px] text-center text-[15px] leading-[22px] text-white/90">{fill(pick(copy, lang), v)}</p>;
  const title = (copy: ReturnType<typeof t>, cls = 'mt-5', size = lang === 'en' ? 'text-[44px] leading-[50px]' : 'text-[36px] leading-[46px]') => (
    <DisplayTitle key={`${match.stage}-${lang}`} text={fill(pick(copy, lang), v)} className={`${cls} ${size} text-center text-white`} />
  );
  const wrap = (children: ReactNode, justify = 'justify-center') => <div className={`flex h-full flex-col ${justify} pb-2 *:shrink-0`}>{children}</div>;

  switch (match.stage) {
    case 'waiting':
      return wrap(
        <>
          <Dove size={200} className="mt-[156px]" />
          {title(C.finding, '-mt-1', lang === 'en' ? 'text-[52px] leading-[58px]' : 'text-[40px] leading-[52px]')}
          <NextDrop lang={lang} className="mt-5" />
        </>,
        'justify-start',
      );
    case 'emptyWeek':
      return wrap(
        <>
          <Dove size={120} />
          {title(S.emptyTitle)}
          {body(S.emptyBody)}
          <NextDrop lang={lang} className="mt-4" />
        </>,
      );
    case 'revealed':
      return wrap(
        <>
          <div className="relative mx-auto h-[190px] w-[240px]">
            <img src={sceneUri(partner.scenes[0]!)} alt="" className="absolute left-0 top-[22px] size-[118px] -rotate-[9deg] rounded-[14px] border-[3px] border-white shadow-lg" />
            <img src={sceneUri(partner.scenes[1]!)} alt="" className="absolute right-0 top-[10px] size-[110px] rotate-[8deg] rounded-[14px] border-[3px] border-white shadow-lg" />
            <motion.img
              src={avatarUri(partner.avatar)}
              alt=""
              className="absolute left-1/2 top-0 size-[150px] -translate-x-1/2 rounded-[22px] border-4 border-white shadow-xl"
              initial={{ scale: 0.7, rotate: -8, opacity: 0 }}
              animate={{ scale: 1, rotate: -2, opacity: 1 }}
              transition={{ type: 'spring', damping: 14, stiffness: 180 }}
            />
          </div>
          {title(S.revealedTitle)}
          <p className="mt-3 text-center text-[15px] text-white/90">
            {name} · {pick(partner.school, lang)}
          </p>
          <div className="mx-auto mt-5 w-[300px]">
            <PrimaryButton onClick={() => go(`/reveal/${match.id}`)}>{fill(pick(S.meet, lang), v)}</PrimaryButton>
          </div>
          <p className="mt-3 text-center text-[13px] text-white/75">{pick(S.replyBy, lang)}</p>
        </>,
      );
    case 'declined':
      return wrap(
        <>
          <Dove size={120} />
          {title(S.declinedTitle)}
          {body(S.declinedBody)}
          <NextDrop lang={lang} className="mt-4" />
        </>,
      );
    case 'awaitingPartner':
      return wrap(
        <>
          <Avatar view={view} size={128} ring />
          {title(S.waitingTitle)}
          {body(S.waitingBody)}
          <div className="mx-auto mt-4 flex max-w-[330px] flex-wrap justify-center gap-2">
            {match.slots
              .filter((s) => match.mySlotIds.includes(s.id))
              .map((s) => {
                const f = formatSlot(s.startsAt, lang);
                return (
                  <span key={s.id} className="ob-glass rounded-full px-3 py-[6px] text-[13px]">
                    {f.day} {f.time}
                  </span>
                );
              })}
          </div>
        </>,
      );
    case 'confirmed':
    case 'dateDay':
    case 'checkedIn':
      return wrap(
        <>
          {match.stage === 'confirmed' && <Confetti count={24} />}
          {title(match.stage === 'confirmed' ? S.confirmedTitle : S.dayTitle, 'mt-0')}
          <div className="mt-6">
            <SlotTicket view={view} lang={lang} />
          </div>
          <div className="mx-auto mt-5 w-[300px]">
            <PrimaryButton onClick={() => go(`/match/${match.id}/date`)}>{pick(S.details, lang)}</PrimaryButton>
          </div>
          <p className="mt-3 text-center text-[13px] text-white/80">
            {match.stage === 'checkedIn' ? fill(pick(S.checkedIn, lang), v) : match.stage === 'confirmed' ? pick(S.checkinNote, lang) : ''}
          </p>
        </>,
      );
    case 'feedback':
      return wrap(
        <>
          <Avatar view={view} size={110} />
          {title(S.feedbackTitle)}
          {body(S.feedbackBody)}
          <div className="mx-auto mt-6 w-[300px]">
            <PrimaryButton onClick={() => go(`/match/${match.id}/feedback`)}>{pick(S.feedbackCta, lang)}</PrimaryButton>
          </div>
        </>,
      );
    case 'awaitingFeedback':
      return wrap(
        <>
          <Dove size={120} />
          {title(S.thanksTitle)}
          {body(S.thanksBody)}
        </>,
      );
    case 'mutual':
      return <Mutual view={view} lang={lang} />;
    case 'closed':
      return wrap(
        <>
          <Dove size={120} />
          {title(S.closedTitle)}
          {body(S.closedBody)}
          <NextDrop lang={lang} className="mt-4" />
        </>,
      );
    case 'reported':
      return wrap(
        <>
          {title(S.reportedTitle, 'mt-0')}
          {body(S.reportedBody)}
          <NextDrop lang={lang} className="mt-5" />
        </>,
      );
  }
}

function Mutual({ view, lang }: { view: MatchView; lang: Lang }) {
  const contact = useLive(() => getContact(view.match.id), [view.match.id]);
  const [copied, setCopied] = useState(false);
  const name = pick(view.partner.name, lang);
  return (
    <div className="flex h-full flex-col justify-center">
      <Confetti count={40} />
      <div className="relative mx-auto flex items-center">
        <Avatar view={view} size={104} />
      </div>
      <DisplayTitle text={pick(S.mutualTitle, lang)} className="mt-4 text-center text-[44px] leading-[50px]" />
      <p className="mx-auto mt-3 max-w-[300px] text-center text-[15px] leading-[22px] text-white/90">{pick(S.mutualBody, lang)}</p>
      {contact && (
        <div className="mx-5 mt-5 rounded-[22px] bg-white/95 px-5 py-4 text-ink shadow-xl">
          <div className="text-[13px] text-ink/60">{fill(pick(S.wechatOf, lang), { name })}</div>
          <div className="mt-1 flex items-center justify-between gap-3">
            <span className="truncate font-mono text-[19px] font-bold">{contact.wechat}</span>
            <button
              type="button"
              onClick={() => {
                void navigator.clipboard?.writeText(contact.wechat).catch(() => undefined);
                setCopied(true);
              }}
              className="flex h-9 shrink-0 items-center gap-1 rounded-full bg-[#07c160] px-3 text-[14px] font-bold text-white"
            >
              <Copy size={15} />
              {pick(copied ? S.copied : S.copy, lang)}
            </button>
          </div>
          <div className="mt-2 text-[12px] text-ink/45">{pick(S.placeholder, lang)}</div>
        </div>
      )}
    </div>
  );
}
