import type { T } from '../i18n/lang';

/**
 * 推断设计: Ditto keeps the whole match loop in iMessage, so none of these shapes were observed.
 * They follow 01 §4–5 (weekly drop, poster, venue, availability) and PRD §4.3–4.7.
 */
export type MatchStage =
  /** Before Wednesday 19:00: "Ditto is finding you a date". */
  | 'waiting'
  /** Drop day with nobody good enough (PRD §4.3 空周). */
  | 'emptyWeek'
  /** Poster is out; respond by Thursday 22:00. */
  | 'revealed'
  /** I said no (reasons captured, not forwarded). */
  | 'declined'
  /** I'm in with some slots; waiting for them to confirm. */
  | 'awaitingPartner'
  /** Both confirmed: time + place locked. */
  | 'confirmed'
  /** Date page open (2h before start). */
  | 'dateDay'
  | 'checkedIn'
  /** After the date: feedback form open. */
  | 'feedback'
  /** My feedback is in; theirs isn't. */
  | 'awaitingFeedback'
  /** Both want to see each other again → contact revealed. */
  | 'mutual'
  /** Not mutual / not met → friendly close. */
  | 'closed'
  /** Reported from any stage: match ended. */
  | 'reported';

export interface AvatarSpec {
  bg: [string, string];
  skin: string;
  hair: 'bob' | 'short' | 'long' | 'curly' | 'bun';
  hairColor: string;
  shirt: string;
  glasses?: boolean;
  earring?: boolean;
}

export type SceneKind = 'coffee' | 'books' | 'film' | 'mountain' | 'cat' | 'guitar' | 'bike' | 'sunset' | 'noodles' | 'camera' | 'gamepad' | 'plant';

export interface Partner {
  id: string;
  name: T;
  gender: 'woman' | 'man' | 'nonbinary';
  age: number;
  school: T;
  major: T;
  year: T;
  heightCm: number;
  hobbies: string[];
  extraTags: T[];
  avatar: AvatarSpec;
  scenes: SceneKind[];
  headline: T;
  blurb: T[];
  wechat: string;
  venue: Venue;
  openers: T[];
  /** Generic reasons used when the user's answers give no overlap. */
  fallbackReasons: Reason[];
}

export interface Venue {
  id: string;
  name: T;
  address: T;
  activity: T;
  extra: T;
}

export interface Reason {
  text: T;
  /** Which answers the reason rests on (PRD §4.4: every reason points to concrete answers). */
  basis: T;
  /** False for template copy. The reveal page hides the AI line when every reason says so. */
  ai?: boolean;
}

export interface Slot {
  id: string;
  startsAt: string;
}

export interface Feedback {
  met: 'met' | 'noShow' | 'iSkipped';
  rating?: number;
  again?: 'yes' | 'no' | 'unsure';
  comment?: string;
  feltSafe?: boolean;
}

export interface Match {
  id: string;
  partnerId: string;
  stage: MatchStage;
  droppedAt: string;
  respondBy: string;
  slots: Slot[];
  mySlotIds: string[];
  confirmedSlotId?: string;
  declineReasons?: string[];
  declineNote?: string;
  flaggedReasons?: number[];
  checkedInAt?: string;
  feedback?: Feedback;
  reportCategory?: string;
}

export interface BlockEntry {
  id: string;
  name: string;
  emails: string[];
  phones: string[];
  note: string;
  createdAt: string;
}

export interface Me {
  id: string;
  name: string;
  onboarded: boolean;
  isStudent: boolean;
  email: string;
  /** Masked by the server: 138****0001. */
  phone: string;
  inviteUrl: string;
  status?: 'onboarding' | 'active' | 'paused' | 'deactivated' | 'deleting';
  photoIssue?: string | null;
  isAdmin?: boolean;
  demoMode?: boolean;
}
