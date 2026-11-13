import { useState, type ReactNode } from 'react';
import { MOCK_API, deactivateAccount, deleteAccount, getMe, logout } from '../api';
import { ChevronRight, LogOut, Mail, PauseCircle, Smartphone, Trash } from '../components/icons';
import { pick, t, useLang, type T } from '../i18n/lang';
import { SectionTitle } from './EditProfilePage';
import { AppScreen, Sheet, TopBar, useGo, useLive, useToast } from './ui';

const C = {
  title: t('Account Settings', '账号设置'),
  email: t('Change Email', '更换邮箱'),
  phone: t('Change Phone Number', '更换手机号'),
  logout: t('Log Out', '退出登录'),
  leaving: t('Leaving Ditto', '离开见一面'),
  deactivate: t('Deactivate Account', '停用账号'),
  deactivateSub: t('Hide your profile. Nothing is deleted', '隐藏你的资料，不会删除任何数据'),
  del: t('Delete Account', '注销账号'),
  delSub: t('Permanently delete your data after 30 days', '30 天后永久删除你的数据'),
  soon: t('Coming soon in the demo', '演示版暂未开放'),
  current: t('Current: {v}', '当前：{v}'),
  cancel: t('Cancel', '取消'),
  sure: t('Are you sure?', '确定吗？'),
  logoutBody: t('You can log back in with your phone number anytime.', '之后随时可以用手机号重新登录。'),
  deactivateBody: t("You won't get matches while deactivated. Log back in to come back.", '停用期间不会收到匹配，重新登录即可恢复。'),
  delBody: t('Your account is closed now and your data is permanently deleted after 30 days.', '账号会立即关闭，30 天后数据将被永久删除。'),
  deactivated: t('Account deactivated', '账号已停用'),
};

type Confirm = { title: T; body: T; cta: T; danger?: boolean; run: () => Promise<void> };

function Row({ icon, label, sub, onClick, tall, danger }: { icon: ReactNode; label: string; sub?: string; onClick: () => void; tall?: boolean; danger?: boolean }) {
  return (
    <button type="button" onClick={onClick} className={`flex w-full items-center gap-[14px] px-5 text-left ${tall ? 'h-[66px]' : 'h-[56px]'}`}>
      <span className={`shrink-0 ${danger ? 'text-[#ff8fa3]' : 'text-white'}`}>{icon}</span>
      <span className="min-w-0 flex-1">
        <span className="block text-[16px] leading-[22px]">{label}</span>
        {sub && <span className="block truncate text-[13px] leading-[18px] text-white/85">{sub}</span>}
      </span>
      <ChevronRight size={16} strokeWidth={2} className="shrink-0 text-white/80" />
    </button>
  );
}

const Divider = () => <div className="mx-5 h-px bg-white/20" />;

/** `/account` (13-account.png, 13 §7). */
export function AccountPage() {
  const lang = useLang();
  const go = useGo();
  const me = useLive(getMe);
  const [confirm, setConfirm] = useState<Confirm | null>(null);
  const [toast, showToast] = useToast();
  const soon = (v?: string) => showToast(`${pick(C.soon, lang)}${v ? ` · ${pick(C.current, lang).replace('{v}', v)}` : ''}`);

  return (
    <AppScreen>
      <TopBar title={pick(C.title, lang)} onBack={() => go('/home')} />
      <div className="absolute inset-x-0 top-[80px]">
        <div className="app-card mx-4">
          <Row icon={<Mail size={18} strokeWidth={1.7} />} label={pick(C.email, lang)} onClick={() => soon(me?.email || undefined)} />
          <Divider />
          <Row icon={<Smartphone size={18} strokeWidth={1.7} />} label={pick(C.phone, lang)} onClick={() => soon(me?.phone || undefined)} />
          <Divider />
          <Row
            icon={<LogOut size={18} strokeWidth={1.7} />}
            label={pick(C.logout, lang)}
            onClick={() =>
              setConfirm({
                title: C.logout,
                body: C.logoutBody,
                cta: C.logout,
                run: async () => {
                  await logout();
                  go('/', { replace: true });
                },
              })
            }
          />
        </div>
        <div className="h-[24px]" />
        <SectionTitle>{pick(C.leaving, lang)}</SectionTitle>
        <div className="app-card mx-4">
          <Row
            tall
            icon={<PauseCircle size={18} strokeWidth={1.7} />}
            label={pick(C.deactivate, lang)}
            sub={pick(C.deactivateSub, lang)}
            onClick={() =>
              setConfirm({
                title: C.deactivate,
                body: C.deactivateBody,
                cta: C.deactivate,
                run: async () => {
                  await deactivateAccount();
                  // The server ends every session on deactivate; logging in again brings the account back.
                  if (!MOCK_API) return go('/', { replace: true });
                  showToast(pick(C.deactivated, lang));
                },
              })
            }
          />
          <Divider />
          <Row
            tall
            danger
            icon={<Trash size={18} strokeWidth={1.7} />}
            label={pick(C.del, lang)}
            sub={pick(C.delSub, lang)}
            onClick={() =>
              setConfirm({
                title: C.del,
                body: C.delBody,
                cta: C.del,
                danger: true,
                run: async () => {
                  await deleteAccount();
                  go('/', { replace: true });
                },
              })
            }
          />
        </div>
      </div>

      <Sheet open={!!confirm} onClose={() => setConfirm(null)} label={confirm ? pick(confirm.title, lang) : ''}>
        {confirm && (
          <div className="px-6 pb-8 pt-7">
            <h2 className="text-center text-[22px] font-bold">{pick(C.sure, lang)}</h2>
            <p className="mt-3 text-center text-[15px] leading-[22px] text-ink/70">{pick(confirm.body, lang)}</p>
            <button
              type="button"
              className={`mt-6 h-12 w-full rounded-full text-[16px] font-bold text-white ${confirm.danger ? 'bg-[#d6336c]' : 'bg-ink'}`}
              onClick={() => {
                const c = confirm;
                setConfirm(null);
                void c.run();
              }}
            >
              {pick(confirm.cta, lang)}
            </button>
            <button type="button" className="mt-2 h-11 w-full text-[16px] font-bold text-ink" onClick={() => setConfirm(null)}>
              {pick(C.cancel, lang)}
            </button>
          </div>
        )}
      </Sheet>
      {toast}
    </AppScreen>
  );
}
