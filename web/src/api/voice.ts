import { http } from './http';
import { MOCK_API } from './session';

/** 一道情景题。选项不含预测排序。 */
export interface VoiceQuestion {
  sessionId: string;
  questionId: string;
  step: number;
  scenario: string;
  options: { key: string; text: string }[];
}

/** 会话结束后的人格 / 语气资产。没采过时 active=false。 */
export interface VoiceProfile {
  active: boolean;
  version?: number;
  voiceStyle?: string;
  summary?: string;
  calibration?: number;
  traits?: {
    voice_style?: string;
    summary?: string;
    traits?: { label: string; confidence: number }[];
  };
}

export interface VoiceAnswerResult {
  sessionId: string;
  step: number;
  predictHit: boolean | null;
  calibration: number;
  done: boolean;
  profile?: VoiceProfile;
  question?: VoiceQuestion;
}

const BANK: { scenario: string; options: { key: string; text: string }[] }[] = [
  {
    scenario: '你和一位刚认识的人约在校园咖啡馆，对方迟到了十分钟还没来。你会怎么做？',
    options: [
      { key: 'A', text: '发消息问到哪了，顺便开玩笑说要罚一杯' },
      { key: 'B', text: '先等着，看看对方什么情况再说' },
      { key: 'C', text: '直接打电话问是不是走错了' },
    ],
  },
  {
    scenario: '聊天时对方说了一个你不太认同的观点。你通常怎么回应？',
    options: [
      { key: 'A', text: '先认真听完，再说出自己不同的看法' },
      { key: 'B', text: '直接说出你的看法，觉得坦诚更重要' },
      { key: 'C', text: '换开话题，避免当场争执' },
    ],
  },
  {
    scenario: '第一次见面冷场了，你会怎么开口？',
    options: [
      { key: 'A', text: '先接对方刚才的话，再问一个具体的问题' },
      { key: 'B', text: '直接说我这人聊天比较直，你随便聊就行' },
      { key: 'C', text: '笑一下，等对方先开口' },
    ],
  },
];

interface MockRun {
  sessionId: string;
  step: number;
  questionId: string;
  customs: string[];
  answered: number;
  open: boolean;
}

let mockProfile: VoiceProfile = { active: false };
let mockRun: MockRun | null = null;

function questionOf(run: MockRun): VoiceQuestion {
  const bank = BANK[(run.step - 1) % BANK.length]!;
  return {
    sessionId: run.sessionId,
    questionId: run.questionId,
    step: run.step,
    scenario: bank.scenario,
    options: bank.options,
  };
}

function finishMock(run: MockRun): VoiceProfile {
  const quoted = run.customs.filter(Boolean).slice(-3);
  const voiceStyle = quoted.length
    ? `说话偏短、照本人原话来。本人常这样表达：${quoted.join('；')}`
    : '样本还少，说话风格待更多原话确认。';
  mockProfile = {
    active: true,
    version: (mockProfile.version ?? 0) + 1,
    voiceStyle,
    summary: quoted.length ? '已记下你自己写的说法，分身会照着这个语气说话。' : '这次主要选了选项，你自己的原话还不多。',
    calibration: 1,
    traits: {
      voice_style: voiceStyle,
      summary: quoted.length ? '已记下你自己写的说法。' : '样本还少。',
      traits: [{ label: quoted.length ? '直球坦诚' : '待观察', confidence: quoted.length ? 0.7 : 0.2 }],
    },
  };
  mockRun = null;
  return mockProfile;
}

/** 当前生效的语气资产。 */
export async function getVoiceProfile(): Promise<VoiceProfile> {
  if (MOCK_API) return mockProfile;
  return http<VoiceProfile>('GET', '/api/voice/profile');
}

/** 开始或续上一次采集，返回当前未答的题。 */
export async function startVoice(): Promise<VoiceQuestion> {
  if (MOCK_API) {
    if (!mockRun || !mockRun.open) {
      mockRun = {
        sessionId: `vs_${Date.now()}`,
        step: 1,
        questionId: `vq_${Date.now()}`,
        customs: [],
        answered: 0,
        open: true,
      };
    }
    return questionOf(mockRun);
  }
  return http<VoiceQuestion>('POST', '/api/voice/start');
}

/** 作答。customText 有内容时以原话为准，并成为分身的语气锚点。 */
export async function answerVoice(
  questionId: string,
  body: { chosenKey?: string; customText?: string },
): Promise<VoiceAnswerResult> {
  if (MOCK_API) {
    const run = mockRun;
    if (!run || run.questionId !== questionId) throw new Error('这道题已经不在了');
    const custom = body.customText?.trim();
    if (custom) run.customs.push(custom.slice(0, 80));
    run.answered += 1;
    run.open = false;
    if (run.answered >= BANK.length) {
      const profile = finishMock(run);
      return { sessionId: run.sessionId, step: run.step, predictHit: null, calibration: 1, done: true, profile };
    }
    run.step += 1;
    run.questionId = `vq_${Date.now()}_${run.step}`;
    run.open = true;
    return {
      sessionId: run.sessionId,
      step: run.step - 1,
      predictHit: !custom,
      calibration: 1,
      done: false,
      question: questionOf(run),
    };
  }
  return http<VoiceAnswerResult>('POST', `/api/voice/answer/${encodeURIComponent(questionId)}`, body);
}

/** 不想继续了：用已经答过的题推断语气资产。 */
export async function stopVoice(sessionId: string): Promise<VoiceProfile> {
  if (MOCK_API) {
    if (mockRun && mockRun.sessionId === sessionId) return finishMock(mockRun);
    return mockProfile.active ? mockProfile : { active: false };
  }
  return http<VoiceProfile>('POST', `/api/voice/stop/${encodeURIComponent(sessionId)}`);
}
