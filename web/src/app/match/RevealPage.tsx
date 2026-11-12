import { useState } from 'react';
import { motion } from 'motion/react';
import { Navigate, useParams } from 'react-router';
import { DECLINE_REASONS, acceptMatch, avatarUri, confirmSlots, declineMatch, flagReason, formatSlot, getCurrentMatch, sceneUri, type MatchView } from '../../api';
import { Flag, MapPin } from '../../components/icons';
import { pick, t, useLang, useQuerySuffix, type Lang } from '../../i18n/lang';
import { AppScreen, PrimaryButton, Sheet, SheetClose, TopBar, useGo, useLive } from '../ui';
import { Card, Pill, SheetChip, VenueMap, fill } from './parts';

const C = {
  title: t('Your match', '本周匹配'),
  dateWith: t('Your date with', '你的约会对象是'),
  why: t('Why you two', '为什么是你们俩'),
  basedOn: t('Based on', '依据'),
  off: t('This is off', '不太准'),
  flagged: t('Thanks, noted', '已反馈'),
  likes: t('What {name} is into', '{name}喜欢的'),
  shared: t('pink = you both', '粉色 = 你们都喜欢'),
  plan: t('The plan', '约会安排'),
  when: t('When can you make it?', '你哪些时间可以？'),
  whenHint: t("Pick every time that works. We'll lock one once {name} picks too.", '能去的都选上。{name}也选好后，我们会锁定一个时间。'),
  replyBy: t('Reply by Thu 10pm, or this match expires.', '请在周四 22:00 前回复，否则本次匹配失效。'),
  ai: t('Written by Ditto’s AI from both of your answers — tell us if something is off.', '以上内容由 AI 根据你们的问卷生成，若有不准确请告诉我们。'),
  pass: t('Not this time', '这次算了'),
  in: t("I'm in", '我想见'),
  declineTitle: t('Not feeling it?', '这次不想见？'),
  declineBody: t("Tell us why so next Wednesday is better. {name} won't see this.", '告诉我们原因，下周三会更准。{name}不会看到。'),
  declineNote: t('Anything else? (optional)', '还想说点什么？（选填）'),
  declineCta: t('Pass on this match', '跳过这次匹配'),
  cancel: t('Cancel', '取消'),
  age: t('{n} yrs', '{n} 岁'),
};

/** `/reveal/:matchId` — the weekly poster (01 §5.1) + venue (§5.2) + availability (§5.3) + accept/decline (§4.3–4.4). 推断设计. */
export function RevealPage() {
  const { matchId } = useParams();
  const lang = useLang();
  const suffix = useQuerySuffix();
  const view = useLive(getCurrentMatch);
  if (!view) return <AppScreen>{null}</AppScreen>;
  if (view.match.id !== matchId || view.match.stage === 'waiting' || view.match.stage === 'emptyWeek') return <Navigate to={`/home${suffix}`} replace />;
  return <Reveal view={view} lang={lang} />;
}

function Reveal({ view, lang }: { view: MatchView; lang: Lang }) {
  const go = useGo();
  const { match, partner, reasons, interests } = view;
  const name = pick(partner.name, lang);
  const actionable = match.stage === 'revealed';
  const [slots, setSlots] = useState<string[]>([]);
  const [declineOpen, setDeclineOpen] = useState(false);
  const toggle = (id: string) => setSlots((s) => (s.includes(id) ? s.filter((x) => x !== id) : [...s, id]));

  return (
    <AppScreen>
      <TopBar title={pick(C.title, lang)} onBack={() => go('/home')} />
      <div className={`ob-scroll absolute inset-x-0 top-[64px] ${actionable ? 'bottom-[96px]' : 'bottom-0'}`}>
        <div className="flex flex-col gap-4 pb-8 pt-1">
          <Poster view={view} lang={lang} />

          <div className="mx-4 flex flex-wrap gap-2">
            {[pick(partner.school, lang), pick(partner.major, lang), pick(partner.year, lang), fill(pick(C.age, lang), { n: partner.age }), `${partner.heightCm} cm`].map((f) => (
              <span key={f} className="ob-glass rounded-full px-3 py-[6px] text-[13px]">
                {f}
              </span>
            ))}
          </div>

          <Card title={pick(C.why, lang)}>
            <ol className="flex flex-col gap-4">
              {reasons.map((r, i) => {
                const flagged = match.flaggedReasons?.includes(i);
                return (
                  <li key={i} className="flex gap-3">
                    <span className="mt-[1px] grid size-6 shrink-0 place-items-center rounded-full bg-white/90 text-[13px] font-bold text-ink">{i + 1}</span>
                    <div className="min-w-0 flex-1">
                      <p className={`text-[15px] font-bold leading-[21px] ${flagged ? 'text-white/45 line-through' : ''}`}>{pick(r.text, lang)}</p>
                      <div className="mt-1 flex items-center justify-between gap-2">
                        <span className="text-[12px] leading-4 text-white/70">
                          {pick(C.basedOn, lang)}: {pick(r.basis, lang)}
                        </span>
                        <button
                          type="button"
                          onClick={() => void flagReason(match.id, i)}
                          className={`flex shrink-0 items-center gap-1 text-[12px] ${flagged ? 'text-[#ff9fd2]' : 'text-white/60'}`}
                        >
                          <Flag size={12} />
                          {pick(flagged ? C.flagged : C.off, lang)}
                        </button>
                      </div>
                    </div>
                  </li>
                );
              })}
            </ol>
          </Card>

          <Card title={fill(pick(C.likes, lang), { name })}>
            <div className="flex flex-wrap gap-2">
              {interests.map((it) => (
                <span key={it.label.en} className={`rounded-full px-3 py-[7px] text-[14px] ${it.shared ? 'ob-selected font-bold' : 'ob-glass'}`}>
                  {pick(it.label, lang)}
                </span>
              ))}
            </div>
            <p className="mt-3 text-[12px] text-white/65">{pick(C.shared, lang)}</p>
          </Card>

          <Card title={pick(C.plan, lang)}>
            <VenueMap venue={partner.venue} lang={lang} className="h-[150px]" />
            <h3 className="mt-4 text-[17px] font-bold leading-6">{pick(partner.venue.name, lang)}</h3>
            <p className="mt-1 flex items-center gap-1 text-[13px] text-white/80">
              <MapPin size={14} />
              {pick(partner.venue.address, lang)}
            </p>
            <p className="mt-3 text-[15px] leading-[21px]">☕ {pick(partner.venue.activity, lang)}</p>
            <p className="mt-1 text-[15px] leading-[21px]">✨ {pick(partner.venue.extra, lang)}</p>
          </Card>

          <Card title={pick(C.when, lang)}>
            <p className="mb-4 text-[13px] leading-[19px] text-white/80">{fill(pick(C.whenHint, lang), { name })}</p>
            <div className="flex flex-col gap-2">
              {match.slots.map((s) => {
                const f = formatSlot(s.startsAt, lang);
                const chosen = actionable ? slots.includes(s.id) : match.mySlotIds.includes(s.id);
                return (
                  <Pill key={s.id} selected={chosen} check onClick={() => actionable && toggle(s.id)}>
                    <span className="tabular-nums">
                      {f.day} · {f.time}
                    </span>
                  </Pill>
                );
              })}
            </div>
          </Card>

          <p className="mx-6 text-center text-[12px] leading-[17px] text-white/70">
            {actionable && (
              <>
                {pick(C.replyBy, lang)}
                <br />
              </>
            )}
            {pick(C.ai, lang)}
          </p>
        </div>
      </div>

      {actionable && (
        <div className="absolute inset-x-0 bottom-0 z-10 flex gap-3 px-4 pb-8 pt-4" style={{ background: 'linear-gradient(180deg, rgba(1,34,89,0) 0%, rgba(1,34,89,0.75) 45%)' }}>
          <button type="button" onClick={() => setDeclineOpen(true)} className="app-glass h-12 flex-1 rounded-full text-[16px] font-bold text-white">
            {pick(C.pass, lang)}
          </button>
          <div className="flex-1">
            <PrimaryButton
              disabled={slots.length === 0}
              onClick={() =>
                void (async () => {
                  await acceptMatch(match.id);
                  await confirmSlots(match.id, slots);
                  go('/home');
                })()
              }
            >
              {pick(C.in, lang)}
            </PrimaryButton>
          </div>
        </div>
      )}

      <DeclineSheet
        open={declineOpen}
        lang={lang}
        name={name}
        onClose={() => setDeclineOpen(false)}
        onSubmit={(reasons, note) =>
          void declineMatch(match.id, reasons, note).then(() => {
            setDeclineOpen(false);
            go('/home');
          })
        }
      />
    </AppScreen>
  );
}

const TILE_LAYOUT = [
  { left: -18, top: -14, size: 128, rotate: -7 },
  { left: 236, top: -20, size: 140, rotate: 6 },
  { left: 250, top: 196, size: 118, rotate: -5 },
  { left: -26, top: 400, size: 132, rotate: 8 },
  { left: 120, top: 438, size: 110, rotate: -10 },
];

/** Poster: scattered collage tiles + a white card offset left ("Your date with" + name + bold line + two paragraphs), 05 §4. */
function Poster({ view, lang }: { view: MatchView; lang: Lang }) {
  const { partner } = view;
  return (
    <div className="app-poster-board relative mx-4 h-[540px] overflow-hidden rounded-[22px] shadow-[0_10px_30px_rgba(0,20,60,0.35)]">
      {partner.scenes.map((s, i) => {
        const l = TILE_LAYOUT[i];
        if (!l) return null;
        return (
          <motion.img
            key={s}
            src={sceneUri(s)}
            alt=""
            className="absolute rounded-[10px] border-[3px] border-white shadow-md"
            style={{ left: l.left, top: l.top, width: l.size, height: l.size }}
            initial={{ opacity: 0, scale: 0.8, rotate: 0 }}
            animate={{ opacity: 1, scale: 1, rotate: l.rotate }}
            transition={{ delay: 0.1 + i * 0.08, type: 'spring', damping: 16, stiffness: 200 }}
          />
        );
      })}
      <motion.img
        src={avatarUri(partner.avatar)}
        alt={pick(partner.name, lang)}
        className="absolute bottom-[24px] right-[-6px] h-[210px] w-[172px] rounded-[16px] border-4 border-white object-cover shadow-xl"
        initial={{ opacity: 0, y: 30, rotate: 0 }}
        animate={{ opacity: 1, y: 0, rotate: 4 }}
        transition={{ delay: 0.5, type: 'spring', damping: 15, stiffness: 160 }}
      />
      <motion.div
        className="absolute left-[14px] top-[56px] w-[236px] rounded-[22px] bg-white px-[18px] pb-5 pt-[18px] text-[#1b1b1f] shadow-[0_6px_20px_rgba(0,0,0,0.18)]"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.25, duration: 0.45, ease: [0.2, 0.9, 0.3, 1] }}
      >
        <div className="text-[15px] font-semibold leading-5">{pick(C.dateWith, lang)}</div>
        <div className="text-[30px] font-extrabold leading-9 tracking-[-0.5px]">{pick(partner.name, lang)}</div>
        <p className="mt-3 text-[15px] font-bold leading-5">{pick(partner.headline, lang)}</p>
        {partner.blurb.map((b, i) => (
          <p key={i} className="mt-[10px] text-[12.5px] leading-[18px] text-[#2b2b30]">
            {pick(b, lang)}
          </p>
        ))}
      </motion.div>
      <span
        className="app-dove absolute left-[178px] top-[18px] rotate-[-8deg]"
        style={{ width: 84, height: 63, backgroundSize: `${84 * 17}px 63px`, ['--dove-w' as string]: '84px' }}
        aria-hidden
      />
      <svg className="absolute bottom-[150px] left-[214px] size-[34px] text-[#f5c242]" viewBox="0 0 24 24" fill="currentColor" aria-hidden>
        <path d="M12 0l2.6 9.4L24 12l-9.4 2.6L12 24l-2.6-9.4L0 12l9.4-2.6z" />
      </svg>
      <svg className="absolute bottom-[78px] left-[244px] size-[18px] text-[#ff2cab]" viewBox="0 0 24 24" fill="currentColor" aria-hidden>
        <path d="M12 0l2.6 9.4L24 12l-9.4 2.6L12 24l-2.6-9.4L0 12l9.4-2.6z" />
      </svg>
    </div>
  );
}

function DeclineSheet({
  open,
  lang,
  name,
  onClose,
  onSubmit,
}: {
  open: boolean;
  lang: Lang;
  name: string;
  onClose: () => void;
  onSubmit: (reasons: string[], note: string) => void;
}) {
  const [reasons, setReasons] = useState<string[]>([]);
  const [note, setNote] = useState('');
  return (
    <Sheet open={open} onClose={onClose} label={pick(C.declineTitle, lang)}>
      <div className="px-6 pb-7 pt-6">
        <SheetClose onClick={onClose} className="-ml-[6px]" />
        <h2 className="mt-1 text-center text-[24px] font-bold">{pick(C.declineTitle, lang)}</h2>
        <p className="mx-auto mt-2 max-w-[300px] text-center text-[15px] leading-[21px] text-ink/65">{fill(pick(C.declineBody, lang), { name })}</p>
        <div className="mt-5 flex flex-wrap gap-2">
          {DECLINE_REASONS.map((r) => (
            <SheetChip key={r.id} selected={reasons.includes(r.id)} onClick={() => setReasons((s) => (s.includes(r.id) ? s.filter((x) => x !== r.id) : [...s, r.id]))}>
              {pick(r.label, lang)}
            </SheetChip>
          ))}
        </div>
        <textarea
          value={note}
          onChange={(e) => setNote(e.target.value)}
          placeholder={pick(C.declineNote, lang)}
          rows={2}
          maxLength={300}
          className="app-field mt-4 block w-full resize-none px-3 py-2 text-[16px] outline-none"
        />
        <button
          type="button"
          disabled={reasons.length === 0}
          onClick={() => onSubmit(reasons, note.trim())}
          className="mt-5 h-12 w-full rounded-full bg-ink text-[16px] font-bold text-white disabled:opacity-35"
        >
          {pick(C.declineCta, lang)}
        </button>
        <button type="button" onClick={onClose} className="mt-2 h-10 w-full text-[16px] font-bold text-ink">
          {pick(C.cancel, lang)}
        </button>
      </div>
    </Sheet>
  );
}
