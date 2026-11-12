import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { PARTNERS, avatarUri, demo, getCurrentMatch, useDb, type MatchStage } from '../api';
import { pick, t, useLang, useQuerySuffix, type Lang, type T } from '../i18n/lang';
import { AppShell } from '../components/AppShell';
import { Sheet, TopBar, useGo, useLive } from './ui';

/**
 * Demo controls (推断设计). The real loop is driven by the server (Wednesday drop, partner actions, feedback
 * window); here every server-side transition is a button. Opened from the floating button (`?demo=1`) or /demo.
 */

const STAGES: { id: MatchStage; label: T }[] = [
  { id: 'waiting', label: t('Waiting for drop', '等待周三匹配') },
  { id: 'emptyWeek', label: t('Empty week', '空周（无匹配）') },
  { id: 'revealed', label: t('Match revealed', '匹配揭晓') },
  { id: 'declined', label: t('I declined', '我拒绝了') },
  { id: 'awaitingPartner', label: t('Waiting for them', '等对方选时间') },
  { id: 'confirmed', label: t('Both confirmed', '双方确认') },
  { id: 'dateDay', label: t('Date day', '约会当天') },
  { id: 'checkedIn', label: t('Checked in', '已签到') },
  { id: 'feedback', label: t('Feedback open', '待填反馈') },
  { id: 'awaitingFeedback', label: t('Waiting for their feedback', '等对方反馈') },
  { id: 'mutual', label: t('Mutual → WeChat', '双向 → 交换微信') },
  { id: 'closed', label: t('Not mutual', '未双向，友好结束') },
  { id: 'reported', label: t('Reported', '已举报') },
];

const C = {
  title: t('Demo controls', '演示面板'),
  stage: t('Jump to a match state', '跳到匹配状态'),
  partner: t('Partner', '匹配对象'),
  server: t('Simulate the other side', '模拟对方 / 服务端'),
  partnerConfirms: t('They pick a time', '对方选好时间'),
  openDay: t('Open date day', '进入约会当天'),
  endDate: t('Date is over → feedback', '约会结束 → 反馈'),
  partnerFeedback: t('They submit feedback', '对方提交反馈'),
  wantsAgain: t('They want to see you again', '对方想再见你'),
  data: t('Data', '数据'),
  seed: t('Fill sample profile', '填充示例资料'),
  resetMatch: t('Reset match', '重置匹配'),
  resetAll: t('Reset everything', '清空全部数据'),
  pages: t('Pages', '页面'),
  current: t('Current', '当前'),
};

const PAGES: [string, T][] = [
  ['/home', t('Home', '首页')],
  ['/where', t('Where', '约会范围')],
  ['/you', t('You', '我的')],
  ['/edit-profile', t('Edit profile', '资料页')],
  ['/onboarding/edit/intention', t('Edit: looking for', '编辑：想找什么')],
  ['/onboarding/edit/name', t('Edit: name', '编辑：名字')],
  ['/deep-info', t('Deep info', '深度信息')],
  ['/blocklist', t('Blocklist', '屏蔽名单')],
  ['/account', t('Account', '账号设置')],
  ['/onboarding', t('Onboarding', '入驻问卷')],
];

function Chip({ active, onClick, children, disabled }: { active?: boolean; onClick: () => void; children: React.ReactNode; disabled?: boolean }) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onClick}
      className={`rounded-full px-3 py-[7px] text-[13px] font-bold transition-colors disabled:opacity-35 ${active ? 'bg-ditto-pink text-white' : 'bg-white/12 text-white'}`}
    >
      {children}
    </button>
  );
}

export function DemoPanelBody({ lang, onDone }: { lang: Lang; onDone?: () => void }) {
  const go = useGo();
  const suffix = useQuerySuffix();
  const view = useLive(getCurrentMatch);
  const wantsAgain = useDb((s) => s.partnerWantsAgain);
  const stage = view?.match.stage;
  const toHome = () => {
    go('/home');
    onDone?.();
  };
  return (
    <div className="px-5 pb-8 pt-5 text-white">
      <h2 className="text-[20px] font-bold">{pick(C.title, lang)}</h2>

      <h3 className="mb-2 mt-5 text-[12px] font-bold uppercase tracking-wide text-white/60">{pick(C.stage, lang)}</h3>
      <div className="flex flex-wrap gap-2">
        {STAGES.map((s) => (
          <Chip
            key={s.id}
            active={stage === s.id}
            onClick={() => {
              demo.setStage(s.id);
              toHome();
            }}
          >
            {pick(s.label, lang)}
          </Chip>
        ))}
      </div>

      <h3 className="mb-2 mt-5 text-[12px] font-bold uppercase tracking-wide text-white/60">{pick(C.partner, lang)}</h3>
      <div className="flex gap-3">
        {PARTNERS.map((p) => (
          <button
            key={p.id}
            type="button"
            onClick={() => demo.setPartner(p.id)}
            className={`flex flex-col items-center gap-1 rounded-[14px] p-2 text-[12px] ${view?.partner.id === p.id ? 'bg-white/20' : ''}`}
          >
            <img src={avatarUri(p.avatar)} alt="" className="size-12 rounded-full" />
            {pick(p.name, lang)}
          </button>
        ))}
      </div>

      <h3 className="mb-2 mt-5 text-[12px] font-bold uppercase tracking-wide text-white/60">{pick(C.server, lang)}</h3>
      <div className="flex flex-wrap gap-2">
        <Chip disabled={stage !== 'awaitingPartner'} onClick={() => (demo.partnerConfirms(), toHome())}>
          {pick(C.partnerConfirms, lang)}
        </Chip>
        <Chip disabled={stage !== 'confirmed'} onClick={() => (demo.openDateDay(), toHome())}>
          {pick(C.openDay, lang)}
        </Chip>
        <Chip disabled={stage !== 'dateDay' && stage !== 'checkedIn'} onClick={() => (demo.endDate(), toHome())}>
          {pick(C.endDate, lang)}
        </Chip>
        <Chip disabled={stage !== 'awaitingFeedback'} onClick={() => (demo.partnerSubmitsFeedback(), toHome())}>
          {pick(C.partnerFeedback, lang)}
        </Chip>
        <Chip active={wantsAgain} onClick={() => demo.setPartnerWantsAgain(!wantsAgain)}>
          {wantsAgain ? '✓ ' : '✗ '}
          {pick(C.wantsAgain, lang)}
        </Chip>
      </div>

      <h3 className="mb-2 mt-5 text-[12px] font-bold uppercase tracking-wide text-white/60">{pick(C.data, lang)}</h3>
      <div className="flex flex-wrap gap-2">
        <Chip onClick={() => demo.seedProfile()}>{pick(C.seed, lang)}</Chip>
        <Chip onClick={() => (demo.resetMatch(), toHome())}>{pick(C.resetMatch, lang)}</Chip>
        <Chip
          onClick={() => {
            demo.resetAll();
            go('/onboarding');
            onDone?.();
          }}
        >
          {pick(C.resetAll, lang)}
        </Chip>
      </div>

      <h3 className="mb-2 mt-5 text-[12px] font-bold uppercase tracking-wide text-white/60">{pick(C.pages, lang)}</h3>
      <div className="flex flex-wrap gap-2">
        {PAGES.map(([path, label]) => (
          <Link key={path} to={`${path}${suffix}`} onClick={onDone} className="rounded-full border border-white/25 px-3 py-[6px] text-[13px]">
            {pick(label, lang)}
          </Link>
        ))}
      </div>
    </div>
  );
}

/** Floating "Demo" pill shown on every logged-in page when `?demo=1`. */
export function DemoFab() {
  const lang = useLang();
  const [open, setOpen] = useState(false);
  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="absolute right-[12px] top-[86px] z-[45] flex h-8 items-center gap-[6px] rounded-full bg-black/45 px-3 text-[12px] font-bold text-white backdrop-blur"
        aria-label="Open demo controls"
      >
        <span className="size-2 rounded-full bg-[#ff2cab]" />
        Demo
      </button>
      <Sheet open={open} onClose={() => setOpen(false)} label={pick(C.title, lang)} tone="dark">
        <DemoPanelBody lang={lang} onDone={() => setOpen(false)} />
      </Sheet>
    </>
  );
}

/** `/demo`: the same controls as a page. */
export function DemoPage() {
  const lang = useLang();
  const go = useGo();
  const [params, setParams] = useSearchParams();
  useEffect(() => {
    if (params.get('demo') !== '1') {
      const next = new URLSearchParams(params);
      next.set('demo', '1');
      setParams(next, { replace: true });
    }
  }, [params, setParams]);
  return (
    <AppShell>
      <TopBar title={pick(C.title, lang)} onBack={() => go('/home')} />
      <div className="ob-scroll absolute inset-x-0 bottom-0 top-[64px]">
        <div className="mx-4 rounded-[24px] bg-[rgba(10,25,40,0.6)] backdrop-blur-xl">
          <DemoPanelBody lang={lang} />
        </div>
      </div>
    </AppShell>
  );
}
