import { t, type T } from '../i18n/lang';

/**
 * Offline place index for the location page and /where (Ditto uses Google Places + isochrones, 09 §19;
 * China needs a local provider, 05 §7). TODO(spring-boot): GET /api/places/search?q= backed by AMap.
 */
export interface Place {
  id: string;
  name: T;
  sub: T;
  /** Extra search keys (pinyin, abbreviations). */
  kw: string;
  /** Labels scattered around the pin on the stylized map. */
  nearby: T[];
  /** People per km² in the pool, drives the "🔥 N nearby" pill. */
  density: number;
  /** Only listed in English (Ditto's own search results, 18-location-search-now). */
  enOnly?: boolean;
}

const p = (id: string, name: T, sub: T, kw: string, nearby: T[], density: number, enOnly?: boolean): Place => ({
  id,
  name,
  sub,
  kw,
  nearby,
  density,
  ...(enOnly ? { enOnly } : {}),
});

const HAIDIAN = [t('Zhongguancun', '中关村'), t('Wudaokou', '五道口'), t('Yuanmingyuan', '圆明园'), t('Haidian', '海淀黄庄')];
const YANGPU = [t('Wujiaochang', '五角场'), t('Jiangwan', '江湾'), t('Yangpu Bridge', '杨浦大桥'), t('Hongkou', '虹口足球场')];

export const PLACES: Place[] = [
  p('pku', t('Peking University', '北京大学'), t('Haidian, Beijing', '北京市海淀区'), 'beida pku beijing daxue', HAIDIAN, 9.2),
  p('tsinghua', t('Tsinghua University', '清华大学'), t('Haidian, Beijing', '北京市海淀区'), 'qinghua thu', HAIDIAN, 8.8),
  p('ruc', t('Renmin University of China', '中国人民大学'), t('Haidian, Beijing', '北京市海淀区'), 'renda ruc', HAIDIAN, 7.4),
  p('bnu', t('Beijing Normal University', '北京师范大学'), t('Haidian, Beijing', '北京市海淀区'), 'beishida bnu', HAIDIAN, 6.9),
  p('wudaokou', t('Wudaokou', '五道口'), t('Haidian, Beijing', '北京市海淀区'), 'wudaokou wdk', HAIDIAN, 10.5),
  p(
    'sanlitun',
    t('Sanlitun', '三里屯'),
    t('Chaoyang, Beijing', '北京市朝阳区'),
    'sanlitun chaoyang',
    [t('Gongti', '工体'), t('Dongzhimen', '东直门'), t('Guomao', '国贸'), t('Liangmaqiao', '亮马桥')],
    5.1,
  ),
  p('fudan', t('Fudan University', '复旦大学'), t('Yangpu, Shanghai', '上海市杨浦区'), 'fudan fdu', YANGPU, 8.1),
  p('tongji', t('Tongji University', '同济大学'), t('Yangpu, Shanghai', '上海市杨浦区'), 'tongji', YANGPU, 6.6),
  p(
    'sjtu',
    t('Shanghai Jiao Tong University (Minhang)', '上海交通大学（闵行）'),
    t('Minhang, Shanghai', '上海市闵行区'),
    'jiaoda sjtu',
    [t('Zizhu', '紫竹'), t('Dongchuan Rd', '东川路'), t('Xinzhuang', '莘庄'), t('Huangpu River', '黄浦江')],
    5.8,
  ),
  p(
    'ecnu',
    t('East China Normal University', '华东师范大学'),
    t('Putuo, Shanghai', '上海市普陀区'),
    'huashida ecnu',
    [t('Zhongshan Park', '中山公园'), t('Changfeng', '长风公园'), t('Caoyang', '曹杨'), t('Jing’an', '静安寺')],
    6.2,
  ),
  p(
    'wukang',
    t('Wukang Road', '武康路'),
    t('Xuhui, Shanghai', '上海市徐汇区'),
    'wukanglu xuhui',
    [t('Anfu Rd', '安福路'), t('Xujiahui', '徐家汇'), t('Hengshan Rd', '衡山路'), t('Jing’an', '静安寺')],
    7.7,
  ),
  p(
    'zju',
    t('Zhejiang University (Zijingang)', '浙江大学（紫金港）'),
    t('Xihu, Hangzhou', '杭州市西湖区'),
    'zheda zju hangzhou',
    [t('West Lake', '西湖'), t('Xixi Wetland', '西溪湿地'), t('Huanglong', '黄龙'), t('Wensan Rd', '文三路')],
    7.2,
  ),
  p(
    'nju',
    t('Nanjing University (Xianlin)', '南京大学（仙林）'),
    t('Qixia, Nanjing', '南京市栖霞区'),
    'nanda nju nanjing',
    [t('Xianlin Lake', '仙林湖'), t('Zhongshan Mtn', '紫金山'), t('Xinjiekou', '新街口'), t('Maigaoqiao', '迈皋桥')],
    5.9,
  ),
  p(
    'whu',
    t('Wuhan University', '武汉大学'),
    t('Wuchang, Wuhan', '武汉市武昌区'),
    'wuda whu wuhan',
    [t('East Lake', '东湖'), t('Jiedaokou', '街道口'), t('Optics Valley', '光谷'), t('Luojia Hill', '珞珈山')],
    7.0,
  ),
  p(
    'sysu',
    t('Sun Yat-sen University', '中山大学'),
    t('Haizhu, Guangzhou', '广州市海珠区'),
    'zhongda sysu guangzhou',
    [t('Canton Tower', '广州塔'), t('Kecun', '客村'), t('Pearl River', '珠江'), t('Beijing Rd', '北京路')],
    6.4,
  ),
  p(
    'szu',
    t('Shenzhen University', '深圳大学'),
    t('Nanshan, Shenzhen', '深圳市南山区'),
    'shenda szu shenzhen',
    [t('Houhai', '后海'), t('Shekou', '蛇口'), t('Nanshan', '南山'), t('Shenzhen Bay', '深圳湾')],
    6.8,
  ),
  p(
    'scu',
    t('Sichuan University (Wangjiang)', '四川大学（望江）'),
    t('Wuhou, Chengdu', '成都市武侯区'),
    'chuanda scu chengdu',
    [t('Jiuyanqiao', '九眼桥'), t('Chunxi Rd', '春熙路'), t('Kuanzhai', '宽窄巷子'), t('Yulin', '玉林路')],
    7.3,
  ),
  p(
    'xjtu',
    t("Xi'an Jiaotong University", '西安交通大学'),
    t("Beilin, Xi'an", '西安市碑林区'),
    'xijiaoda xjtu xian',
    [t('Big Wild Goose', '大雁塔'), t('City Wall', '城墙'), t('Xiaozhai', '小寨'), t('Qujiang', '曲江')],
    6.0,
  ),
  // Ditto's own suggestions for "Boston" (09 §19), kept for the English replica only.
  p('boston-ma', t('Boston', '波士顿'), t('MA', '美国马萨诸塞州'), 'boston', [t('Cambridge', '剑桥'), t('Somerville', '萨默维尔'), t('Brookline', '布鲁克莱恩'), t('Quincy', '昆西')], 8.4, true),
  p('boston-uk', t('Boston', '波士顿'), t('UK', '英国'), 'boston', [t('Skegness', '斯凯格内斯'), t('Spalding', '斯伯丁'), t('Sleaford', '斯利福德'), t('Lincoln', '林肯')], 1.2, true),
  p('boston-bar', t('Boston Bar', '波士顿巴'), t('BC, Canada', '加拿大不列颠哥伦比亚省'), 'boston bar', [t('Hope', '霍普'), t('Lytton', '利顿'), t('Yale', '耶鲁'), t('Spuzzum', '斯普祖姆')], 0.3, true),
];

/** "Use my current location" in the demo resolves here (no geolocation prompt). */
export const CURRENT_PLACE_ID = 'wudaokou';

export function placeById(id: string | undefined): Place | undefined {
  return PLACES.find((pl) => pl.id === id);
}

export function searchPlaces(query: string, lang: 'en' | 'zh'): Place[] {
  const q = query.trim().toLowerCase();
  if (!q) return [];
  return PLACES.filter((pl) => (lang === 'en' || !pl.enOnly) && [pl.name.en, pl.name.zh, pl.sub.en, pl.sub.zh, pl.kw].some((s) => s.toLowerCase().includes(q))).slice(0, 8);
}

/** Rough pool size inside the isochrone, for the "🔥 N nearby" pill on /where. */
export function nearbyCount(place: Place, mode: 'walk' | 'transit' | 'drive', minutes: number): number {
  const kmh = mode === 'walk' ? 4.5 : mode === 'transit' ? 18 : 28;
  const r = (kmh * minutes) / 60;
  return Math.round(place.density * Math.PI * r * r * 0.9);
}
