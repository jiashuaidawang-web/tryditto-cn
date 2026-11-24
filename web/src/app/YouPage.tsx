import { getProfile } from '../api';
import { pick, t, useLang } from '../i18n/lang';
import { AppScreen, DisplayTitle, NavCard, useGo, useLive } from './ui';

const C = {
  title: t('What Ditto knows\nabout you', '见一面眼中的你'),
  profile: t('Profile', '我的资料'),
  deep: t('Deep Info', '深度信息'),
  deepSub: t('Ideal look, religion, politics, and what matters to you.', '理想型照片，以及你真正在意的事。'),
  block: t('Blocklist', '屏蔽名单'),
  blockSub: t('People you never want Ditto to match you with.', '永远不想被匹配到的人。'),
  voice: t('How you talk', '说话方式'),
  voiceSub: t('A few scenes, in your own words, so your double sounds like you.', '用你自己的话答几道情景题，分身照着学。'),
};

/** `/you` (03-you.png, 09 §10.1): three cards — Profile (photo thumbs), Deep Info, Blocklist. */
export function YouPage() {
  const lang = useLang();
  const go = useGo();
  const profile = useLive(getProfile);
  return (
    <AppScreen tab="you">
      <DisplayTitle text={pick(C.title, lang)} className="absolute left-5 top-[26px] text-[31px] leading-[34px] text-white" stagger={0.02} />
      <div className="absolute inset-x-4 top-[148px] bottom-[84px] flex flex-col gap-3 overflow-y-auto pb-2">
        <NavCard title={pick(C.profile, lang)} onClick={() => go('/edit-profile')}>
          <span className="mt-[14px] flex gap-2">
            {(profile?.photos ?? []).slice(0, 6).map((url, i) => (
              <span key={i} className="block size-[52px] rounded-[8px] bg-cover bg-center" style={{ backgroundImage: `url("${url}")` }} />
            ))}
          </span>
        </NavCard>
        <NavCard title={pick(C.deep, lang)} onClick={() => go('/deep-info')}>
          <span className="mt-[6px] block truncate text-[14px] leading-5 text-white/90">{pick(C.deepSub, lang)}</span>
        </NavCard>
        <NavCard title={pick(C.voice, lang)} onClick={() => go('/voice')}>
          <span className="mt-[6px] block text-[14px] leading-5 text-white/90">{pick(C.voiceSub, lang)}</span>
        </NavCard>
        <NavCard title={pick(C.block, lang)} onClick={() => go('/blocklist')}>
          <span className="mt-[6px] block truncate text-[14px] leading-5 text-white/90">{pick(C.blockSub, lang)}</span>
        </NavCard>
      </div>
    </AppScreen>
  );
}
