import { useEffect, useState } from 'react';
import { motion } from 'motion/react';
import { Navigate, useParams } from 'react-router';
import { REPORT_CATEGORIES, avatarUri, checkIn, formatCountdown, formatSlot, getCurrentMatch, reportMatch, type MatchView } from '../../api';
import { Clock, Flag, MapPin } from '../../components/icons';
import { pick, t, useLang, useQuerySuffix, type Lang } from '../../i18n/lang';
import { AppScreen, PrimaryButton, Sheet, SheetClose, TopBar, useGo, useLive, useToast } from '../ui';
import { Card, SheetChip, VenueMap, fill } from './parts';

const C = {
  title: t('Your date', '约会详情'),
  startsIn: t('Starts in {t}', '还有 {t} 开始'),
  started: t('Happening now', '进行中'),
  navigate: t('Directions', '导航过去'),
  navSoon: t('Maps open in the full app', '正式版会打开高德地图导航'),
  here: t("I'm here 📍", '我到了 📍'),
  hereHint: t("Tap when you arrive — we'll let {name} know.", '到了就点一下，我们会告诉{name}。'),
  opensLater: t('Check-in opens 2 hours before the date.', '约会开始前 2 小时开放签到。'),
  checkedAt: t('Checked in at {time}', '{time} 已签到'),
  told: t("We told {name} you're here.", '已经告诉{name}你到了。'),
  over: t("We've met — how did it go?", '见完了，聊聊感受'),
  starters: t('Conversation starters', '开场话题'),
  safety: t('Stay safe', '安全提示'),
  tips: [
    t('Meet where we planned — a public spot on campus.', '只在约定的公共场所见面，不临时换去私密地点。'),
    t('Tell a friend where you are and when you expect to be back.', '告诉一位朋友你在哪、大概几点结束。'),
    t('Get there and back on your own. Keep your phone charged.', '自己往返，手机保持有电。'),
    t("You can leave anytime. No explanation needed.", '任何时候都可以离开，不需要理由。'),
    t('Never send money or verification codes to a match.', '不要给对方转账，也不要提供任何验证码。'),
  ],
  report: t('Report a problem', '举报 / 求助'),
  reportTitle: t('What happened?', '发生了什么？'),
  reportBody: t('Our safety team reviews every report within 24 hours. In an emergency, call 110.', '安全团队会在 24 小时内处理。如遇紧急情况，请立即拨打 110。'),
  reportNote: t('Details help us act faster (optional)', '补充细节能帮我们更快处理（选填）'),
  reportCta: t('Send report', '提交举报'),
  cancel: t('Cancel', '取消'),
};

/** `/match/:matchId/date` — date-day page: venue map placeholder, check-in, openers, safety tips, report. 推断设计. */
export function DatePage() {
  const { matchId } = useParams();
  const suffix = useQuerySuffix();
  const lang = useLang();
  const view = useLive(getCurrentMatch);
  if (!view) return <AppScreen>{null}</AppScreen>;
  const ok = view.match.id === matchId && ['confirmed', 'dateDay', 'checkedIn'].includes(view.match.stage);
  if (!ok) return <Navigate to={`/home${suffix}`} replace />;
  return <DateDay view={view} lang={lang} />;
}

function useNow() {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const id = window.setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(id);
  }, []);
  return now;
}

function DateDay({ view, lang }: { view: MatchView; lang: Lang }) {
  const go = useGo();
  const now = useNow();
  const [toast, showToast] = useToast();
  const [reportOpen, setReportOpen] = useState(false);
  const { match, partner } = view;
  const name = pick(partner.name, lang);
  const slot = match.slots.find((s) => s.id === match.confirmedSlotId) ?? match.slots[0]!;
  const f = formatSlot(slot.startsAt, lang);
  const start = new Date(slot.startsAt);
  const checkedAt = match.checkedInAt ? formatSlot(match.checkedInAt, lang).time : '';

  return (
    <AppScreen>
      <TopBar title={pick(C.title, lang)} onBack={() => go('/home')} />
      <div className="ob-scroll absolute inset-x-0 bottom-0 top-[64px]">
        <div className="flex flex-col gap-4 pb-10 pt-1">
          <Card>
            <div className="flex items-center gap-4">
              <img src={avatarUri(partner.avatar)} alt="" className="size-[64px] shrink-0 rounded-full border-2 border-white/85" />
              <div className="min-w-0">
                <div className="text-[19px] font-bold leading-6">
                  {name}, {partner.age}
                </div>
                <div className="truncate text-[13px] leading-[18px] text-white/80">
                  {pick(partner.school, lang)} · {pick(partner.major, lang)}
                </div>
              </div>
            </div>
            <div className="mt-4 text-[26px] font-bold leading-8 tabular-nums">
              {f.day} · {f.time}
            </div>
            <div className="mt-1 flex items-center gap-1 text-[14px] text-white/85">
              <Clock size={14} />
              {start > now ? fill(pick(C.startsIn, lang), { t: formatCountdown(start, lang, now) }) : pick(C.started, lang)}
            </div>
          </Card>

          <Card>
            <VenueMap venue={partner.venue} lang={lang} className="h-[180px]" />
            <h3 className="mt-4 text-[17px] font-bold leading-6">{pick(partner.venue.name, lang)}</h3>
            <p className="mt-1 flex items-center gap-1 text-[13px] text-white/80">
              <MapPin size={14} />
              {pick(partner.venue.address, lang)}
            </p>
            <p className="mt-2 text-[14px] leading-5 text-white/90">
              {pick(partner.venue.activity, lang)} · {pick(partner.venue.extra, lang)}
            </p>
            <button type="button" onClick={() => showToast(pick(C.navSoon, lang))} className="app-glass mt-4 h-10 w-full rounded-full text-[15px] font-bold">
              {pick(C.navigate, lang)}
            </button>
          </Card>

          <div className="mx-4">
            {match.stage === 'checkedIn' ? (
              <motion.div initial={{ scale: 0.96, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} className="rounded-[24px] bg-[#14c38e]/90 px-5 py-4 text-center">
                <div className="text-[18px] font-bold">✓ {fill(pick(C.checkedAt, lang), { time: checkedAt })}</div>
                <div className="mt-1 text-[14px] text-white/90">{fill(pick(C.told, lang), { name })}</div>
              </motion.div>
            ) : (
              <>
                <PrimaryButton disabled={match.stage !== 'dateDay'} onClick={() => void checkIn(match.id)} className="!h-14 !text-[18px]">
                  {pick(C.here, lang)}
                </PrimaryButton>
                <p className="mt-2 text-center text-[13px] text-white/80">{match.stage === 'dateDay' ? fill(pick(C.hereHint, lang), { name }) : pick(C.opensLater, lang)}</p>
              </>
            )}
            {match.stage !== 'confirmed' && (
              <button type="button" onClick={() => go(`/match/${match.id}/feedback`)} className="app-glass mt-3 h-12 w-full rounded-full text-[16px] font-bold">
                {pick(C.over, lang)}
              </button>
            )}
          </div>

          <Card title={pick(C.starters, lang)}>
            <ul className="flex flex-col gap-3">
              {partner.openers.map((o, i) => (
                <li key={i} className="rounded-[18px] bg-white/90 px-4 py-3 text-[15px] font-medium leading-[21px] text-ink">
                  {pick(o, lang)}
                </li>
              ))}
            </ul>
          </Card>

          <Card title={pick(C.safety, lang)}>
            <ul className="flex flex-col gap-[10px]">
              {C.tips.map((tip, i) => (
                <li key={i} className="flex gap-2 text-[14px] leading-5 text-white/95">
                  <span className="mt-[7px] size-[6px] shrink-0 rounded-full bg-[#ff7fd0]" />
                  {pick(tip, lang)}
                </li>
              ))}
            </ul>
            <button type="button" onClick={() => setReportOpen(true)} className="mt-4 flex items-center gap-2 text-[15px] font-bold text-[#ffb3d9]">
              <Flag size={16} />
              {pick(C.report, lang)}
            </button>
          </Card>
        </div>
      </div>
      <ReportSheet
        open={reportOpen}
        lang={lang}
        onClose={() => setReportOpen(false)}
        onSubmit={(category, note) =>
          void reportMatch(match.id, category, note).then(() => {
            setReportOpen(false);
            go('/home');
          })
        }
      />
      {toast}
    </AppScreen>
  );
}

function ReportSheet({ open, lang, onClose, onSubmit }: { open: boolean; lang: Lang; onClose: () => void; onSubmit: (category: string, note: string) => void }) {
  const [category, setCategory] = useState<string | null>(null);
  const [note, setNote] = useState('');
  return (
    <Sheet open={open} onClose={onClose} label={pick(C.reportTitle, lang)}>
      <div className="px-6 pb-7 pt-6">
        <SheetClose onClick={onClose} className="-ml-[6px]" />
        <h2 className="mt-1 text-center text-[24px] font-bold">{pick(C.reportTitle, lang)}</h2>
        <p className="mx-auto mt-2 max-w-[310px] text-center text-[14px] leading-5 text-ink/65">{pick(C.reportBody, lang)}</p>
        <div className="mt-5 flex flex-wrap gap-2">
          {REPORT_CATEGORIES.map((c) => (
            <SheetChip key={c.id} selected={category === c.id} onClick={() => setCategory(c.id)}>
              {pick(c.label, lang)}
            </SheetChip>
          ))}
        </div>
        <textarea
          value={note}
          onChange={(e) => setNote(e.target.value)}
          placeholder={pick(C.reportNote, lang)}
          rows={3}
          maxLength={1000}
          className="app-field mt-4 block w-full resize-none px-3 py-2 text-[16px] outline-none"
        />
        <button
          type="button"
          disabled={!category}
          onClick={() => category && onSubmit(category, note.trim())}
          className="mt-5 h-12 w-full rounded-full bg-[#d6336c] text-[16px] font-bold text-white disabled:opacity-35"
        >
          {pick(C.reportCta, lang)}
        </button>
        <button type="button" onClick={onClose} className="mt-2 h-10 w-full text-[16px] font-bold text-ink">
          {pick(C.cancel, lang)}
        </button>
      </div>
    </Sheet>
  );
}
