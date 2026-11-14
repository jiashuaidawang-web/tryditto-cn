import { pick, t, useLang } from './lang';

/** Screen-reader labels for icon-only controls. */
const A11Y = {
  back: t('Back', '返回'),
  next: t('Next', '下一步'),
  send: t('Send', '发送'),
  lockInfo: t('What the lock means', '锁图标是什么意思'),
  whyWhere: t("Why we ask where you'd date", '为什么要问约会范围'),
  addPhotos: t('Add photos', '添加照片'),
  addPhoto: t('Add photo', '添加照片'),
  removePhoto: t('Remove photo', '删除照片'),
  clear: t('Clear', '清空'),
  useLocation: t('Use my current location', '使用当前位置'),
  maxTravel: t('Max travel time', '最长路程时间'),
  close: t('Close', '关闭'),
  loading: t('Loading', '加载中'),
  tabs: t('Tabs', '页面导航'),
  add: t('Add', '添加'),
  height: t('Height', '身高'),
  typing: t('typing', '正在输入'),
  menu: t('Menu', '菜单'),
  closeMenu: t('Close menu', '关闭菜单'),
  openDemo: t('Open demo controls', '打开演示面板'),
};

export type A11yLabels = { [K in keyof typeof A11Y]: string };

export function useA11y(): A11yLabels {
  const lang = useLang();
  return Object.fromEntries(Object.entries(A11Y).map(([k, v]) => [k, pick(v, lang)])) as A11yLabels;
}
