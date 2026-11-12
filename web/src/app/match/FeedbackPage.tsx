import { useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { Navigate, useParams } from 'react-router';
import { avatarUri, getCurrentMatch, submitFeedback, type Feedback, type MatchView } from '../../api';
import { Star } from '../../components/icons';
import { pick, t, useLang, useQuerySuffix, type Lang, type T } from '../../i18n/lang';
import { AppScreen, PrimaryButton, TopBar, useGo, useLive } from '../ui';
import { Card, Pill, fill } from './parts';

const C = {
  title: t('How did it go?', '约会反馈'),
  private: t("{name} never sees your answers. If you both say yes, we'll swap WeChat.", '你的回答{name}看不到。如果你们都想再见，我们会帮你们交换微信。'),
  met: t('Did you meet {name}?', '你和{name}见面了吗？'),
  rating: t('How was the date?', '这次约会体验如何？'),
  again: t('Would you see {name} again?', '还想再见{name}吗？'),
  safe: t('Did you feel safe and respected?', '你觉得安全、被尊重吗？'),
  comment: t('Anything Ditto should know? (optional)', '还有什么想告诉我们的？（选填）'),
  commentPh: t('What clicked, what didn’t. Only Ditto reads this.', '哪里聊得来，哪里不太对。只有见一面能看到。'),
  noShowNote: t("Sorry about that. We'll look into it and it won't count against you.", '抱歉遇到这种情况。我们会核实，这不会影响你的匹配。'),
  submit: t('Send feedback', '提交反馈'),
  yes: t('Yes', '是'),
  no: t('No', '否'),
};

const MET: { id: Feedback['met']; label: T }[] = [
  { id: 'met', label: t('Yes, we met', '见到了') },
  { id: 'noShow', label: t("They didn't show", '对方没来') },
  { id: 'iSkipped', label: t("I couldn't make it", '我没去成') },
];
const AGAIN: { id: NonNullable<Feedback['again']>; label: T }[] = [
  { id: 'yes', label: t('Yes, I’d love to 💘', '想！💘') },
  { id: 'unsure', label: t('Not sure yet', '还不确定') },
  { id: 'no', label: t('No, but thanks', '不了，谢谢') },
];

/** `/match/:matchId/feedback` — after the date (01 §4.1 item 5: feedback is the main training signal). 推断设计. */
export function FeedbackPage() {
  const { matchId } = useParams();
  const suffix = useQuerySuffix();
  const lang = useLang();
  const view = useLive(getCurrentMatch);
  if (!view) return <AppScreen>{null}</AppScreen>;
  const ok = view.match.id === matchId && ['dateDay', 'checkedIn', 'feedback'].includes(view.match.stage);
  if (!ok) return <Navigate to={`/home${suffix}`} replace />;
  return <FeedbackForm view={view} lang={lang} />;
}

function FeedbackForm({ view, lang }: { view: MatchView; lang: Lang }) {
  const go = useGo();
  const { match, partner } = view;
  const name = pick(partner.name, lang);
  const [fb, setFb] = useState<Feedback>({ met: 'met' });
  const [touched, setTouched] = useState(false);
  const met = touched ? fb.met : undefined;
  const set = (patch: Partial<Feedback>) => setFb((f) => ({ ...f, ...patch }));
  const canSubmit = !!met && (met !== 'met' || (!!fb.rating && !!fb.again && fb.feltSafe !== undefined));

  return (
    <AppScreen>
      <TopBar title={pick(C.title, lang)} onBack={() => go('/home')} />
      <div className="ob-scroll absolute inset-x-0 bottom-0 top-[64px]">
        <div className="flex flex-col gap-4 pb-12 pt-1">
          <div className="mx-6 flex items-center gap-3">
            <img src={avatarUri(partner.avatar)} alt="" className="size-12 rounded-full border-2 border-white/85" />
            <p className="text-[14px] leading-5 text-white/90">{fill(pick(C.private, lang), { name })}</p>
          </div>

          <Card title={fill(pick(C.met, lang), { name })}>
            <div className="flex flex-col items-end gap-2">
              {MET.map((o) => (
                <Pill
                  key={o.id}
                  selected={met === o.id}
                  onClick={() => {
                    setTouched(true);
                    set({ met: o.id });
                  }}
                >
                  {pick(o.label, lang)}
                </Pill>
              ))}
            </div>
          </Card>

          <AnimatePresence initial={false}>
            {met === 'met' && (
              <motion.div key="met" className="flex flex-col gap-4" initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}>
                <Card title={pick(C.rating, lang)}>
                  <div className="flex justify-between px-2" role="radiogroup" aria-label={pick(C.rating, lang)}>
                    {[1, 2, 3, 4, 5].map((n) => (
                      <button
                        key={n}
                        type="button"
                        role="radio"
                        aria-checked={fb.rating === n}
                        aria-label={`${n}`}
                        onClick={() => set({ rating: n })}
                        className={`transition-transform active:scale-90 ${fb.rating && n <= fb.rating ? 'text-[#ffd166]' : 'text-white/35'}`}
                      >
                        <Star size={40} fill={fb.rating && n <= fb.rating ? 'currentColor' : 'none'} />
                      </button>
                    ))}
                  </div>
                </Card>
                <Card title={fill(pick(C.again, lang), { name })}>
                  <div className="flex flex-col items-end gap-2">
                    {AGAIN.map((o) => (
                      <Pill key={o.id} selected={fb.again === o.id} onClick={() => set({ again: o.id })}>
                        {pick(o.label, lang)}
                      </Pill>
                    ))}
                  </div>
                </Card>
                <Card title={pick(C.safe, lang)}>
                  <div className="flex justify-end gap-2">
                    <Pill selected={fb.feltSafe === true} onClick={() => set({ feltSafe: true })}>
                      {pick(C.yes, lang)}
                    </Pill>
                    <Pill selected={fb.feltSafe === false} onClick={() => set({ feltSafe: false })}>
                      {pick(C.no, lang)}
                    </Pill>
                  </div>
                </Card>
              </motion.div>
            )}
            {met && met !== 'met' && (
              <motion.p key="noshow" className="mx-6 text-[14px] leading-5 text-white/90" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                {pick(C.noShowNote, lang)}
              </motion.p>
            )}
          </AnimatePresence>

          {met && (
            <Card title={pick(C.comment, lang)}>
              <div className="ob-dock rounded-[20px]">
                <textarea
                  value={fb.comment ?? ''}
                  onChange={(e) => set({ comment: e.target.value })}
                  placeholder={pick(C.commentPh, lang)}
                  rows={3}
                  maxLength={1000}
                  className="ob-input block w-full resize-none bg-transparent px-4 py-3 text-[16px] leading-5 text-white outline-none"
                />
              </div>
            </Card>
          )}

          <div className="mx-4">
            <PrimaryButton
              disabled={!canSubmit}
              onClick={() =>
                void submitFeedback(match.id, met === 'met' ? fb : { met: fb.met, ...(fb.comment ? { comment: fb.comment } : {}) }).then(() => go('/home'))
              }
            >
              {pick(C.submit, lang)}
            </PrimaryButton>
          </div>
        </div>
      </div>
    </AppScreen>
  );
}
