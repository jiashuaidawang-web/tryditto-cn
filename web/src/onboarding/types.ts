import type { Lang, T } from '../i18n/lang';

export type QuestionType =
  | 'single'
  | 'multi'
  | 'text'
  | 'longText'
  | 'date'
  | 'ageRange'
  | 'height'
  | 'heightRange'
  | 'interstitial'
  | 'email'
  | 'photos'
  /** 6-digit email code (emailVerify). */
  | 'code'
  /** Dating area: place + travel mode + max minutes (09 §19). */
  | 'location';

export type TravelMode = 'walk' | 'transit' | 'drive';

export interface LocationValue {
  placeId: string;
  mode: TravelMode;
  minutes: number;
}

export interface Option {
  id: string;
  label: T;
  emoji?: string;
  /** Override emoji for zh when the localized meaning changed. */
  emojiZh?: string;
}

export type Answers = Record<string, unknown>;

export interface FlowContext {
  answers: Answers;
  lang: Lang;
}

export interface HeightValue {
  cm: number;
  unit: 'cm' | 'ft';
}

export interface HeightRangeValue {
  any: boolean;
  min: number;
  max: number;
}

export interface Question {
  id: string;
  type: QuestionType;
  /** One or more bubbles. Wrap keywords in **double asterisks**; they render bold with the letter wave. */
  bubbles: T[];
  options?: Option[];
  /** Options computed from earlier answers (e.g. position pages depend on gender). */
  optionsFor?: (ctx: FlowContext) => Option[];
  /** Ids that clear every other selection, and are cleared by any other selection. */
  exclusive?: string[];
  maxSelect?: number;
  maxHint?: T;
  /** Adds a trailing pencil chip that becomes a text input (`Other`, `Type your hobby`). */
  allowCustom?: T;
  placeholder?: T;
  /** Shows "🔒 Private. Only used to match you" under the bubbles. */
  private?: boolean;
  /** Small text centered above the bottom input. */
  helper?: T;
  /** Paper-plane hint above the textarea (firstMessage). */
  sendHint?: T;
  /** Underlined text button under the bubble (school email escape). */
  escape?: T;
  emailKind?: 'school' | 'personal';
  /** Long text can be submitted empty (dealbreakers). */
  optional?: boolean;
  maxLength?: number;
  /** Fixed-width pills that wrap long labels instead of growing (ideal match / describe yourself). */
  wrapPills?: boolean;
  /** Hidden in that language (zh drops ethnicity / politics / religion / position). */
  drop?: Partial<Record<Lang, boolean>>;
  /** Branch questions belong to a hobby option id. */
  hobby?: string;
}

/** A resolved page in the current flow. */
export interface Step {
  id: string;
  question: Question;
  /** 0, 1, 2 — which progress segment. */
  segment: 0 | 1 | 2;
  /** 1-based cell index within its segment and the segment's cell count. */
  cell: number;
  cells: number;
  /** Counted in progress but jumped over with a short spinner (N1/N2 photos slot). */
  skip?: boolean;
  /** Counted in progress only (pages after the run's scope, e.g. code verification). */
  ghost?: boolean;
  /** `skipAnswered` flag: silently jumped over because it already holds a valid answer. */
  answered?: boolean;
}
