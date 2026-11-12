import { useAuth } from '../landing/auth';
import { SAVE_LATENCY_MS } from '../motion/tokens';
import { useOnboarding } from '../onboarding/store';
import type { Answers, LocationValue } from '../onboarding/types';
import { samplePhotoUris } from './art';
import { useDb } from './db';
import { SAMPLE_ANSWERS, defaultPartnerFor, partnerById, reasonsFor, sharedInterests } from './mock';
import { searchPlaces as searchLocal, type Place } from './places';
import { nextDrop, proposedSlots, respondBy } from './time';
import type { BlockEntry, Feedback, Match, MatchStage, Me } from './types';

/**
 * Backend seam. Every function is the future Spring Boot call named in its TODO; today they resolve
 * against the local stores (onboarding answers + src/api/db.ts). Pages never touch the stores for writes.
 */

export type { BlockEntry, Feedback, Match, MatchStage, Me, Partner, Reason, Slot, Venue } from './types';
export { useDb } from './db';
export { PARTNERS, DECLINE_REASONS, REPORT_CATEGORIES, partnerById } from './mock';
export { placeById, nearbyCount, CURRENT_PLACE_ID, type Place } from './places';
export { nextDrop, formatCountdown, formatSlot, formatDropLabel } from './time';
export { avatarUri, sceneUri } from './art';

export interface OnboardingProgress {
  answers: Answers;
  furthestStepId: string | null;
  completed: boolean;
}

export interface OnboardingApi {
  saveAnswer(questionId: string, value: unknown): Promise<void>;
  getProgress(): Promise<OnboardingProgress>;
}

const simulateLatency = () =>
  new URLSearchParams(window.location.search).get('latency') === '1'
    ? new Promise<void>((r) => setTimeout(r, SAVE_LATENCY_MS))
    : Promise.resolve();

const db = () => useDb.getState();
const answers = () => useOnboarding.getState().answers;
const uid = (prefix: string) => `${prefix}_${Math.random().toString(36).slice(2, 10)}`;

export const api: OnboardingApi = {
  async saveAnswer(questionId, value) {
    await simulateLatency();
    // TODO(spring-boot): PUT /api/onboarding/answers/{questionId}  body: { value }
    useOnboarding.getState().setAnswer(questionId, value);
  },
  async getProgress() {
    // TODO(spring-boot): GET /api/onboarding/progress
    const { answers: a, furthestId, completed } = useOnboarding.getState();
    return { answers: a, furthestStepId: furthestId, completed };
  },
};

// ───────────────────────── Account ─────────────────────────

function meSnapshot(): Me {
  const a = answers();
  const email = (a.noSchoolEmail !== true && typeof a.schoolEmail === 'string' ? a.schoolEmail : a.personalEmail) as string | undefined;
  return {
    id: 'me_demo',
    name: typeof a.name === 'string' ? a.name : '',
    onboarded: useOnboarding.getState().completed,
    isStudent: a.isStudent === 'yes',
    email: email ?? '',
    phone: useAuth.getState().phone ?? db().phone,
    inviteUrl: `jianyimian.cn/j/home-referral/u_${(typeof a.name === 'string' && a.name ? a.name : 'demo').toLowerCase().replace(/[^a-z0-9]/g, '').slice(0, 6) || 'demo'}82`,
  };
}

export async function getMe(): Promise<Me> {
  // TODO(spring-boot): GET /api/me
  return meSnapshot();
}

export interface Profile {
  answers: Answers;
  photos: string[];
  idealPhotos: string[];
}

/** Everything /you, /edit-profile, /deep-info and /where render. */
export async function getProfile(): Promise<Profile> {
  // TODO(spring-boot): GET /api/me  (profile answers + photo urls; Ditto: /user/profile, 13 §2.3)
  return { answers: answers(), photos: db().photos, idealPhotos: db().idealPhotos };
}

export async function completeOnboarding(): Promise<void> {
  // TODO(spring-boot): POST /api/onboarding/complete (Ditto: /user/onboarding/enroll, 07 §2)
  useOnboarding.getState().complete();
}

export async function logout(): Promise<void> {
  // TODO(spring-boot): POST /api/auth/logout
  useAuth.getState().logout();
}

export async function deactivateAccount(): Promise<void> {
  // TODO(spring-boot): POST /api/account/deactivate  (hide profile, nothing deleted — 13 §7)
  db().set({ deactivated: true });
}

export async function deleteAccount(): Promise<void> {
  // TODO(spring-boot): DELETE /api/account  (purged after 30 days — 13 §7)
  resetEverything();
}

// ───────────────────────── Email code ─────────────────────────

export async function sendEmailCode(email: string): Promise<{ sentTo: string }> {
  // TODO(spring-boot): POST /api/auth/email/code  body: { email }  (Ditto: /auth/email/code)
  await new Promise((r) => setTimeout(r, 900));
  return { sentTo: email };
}

/** Demo: any 6 digits pass. */
export async function verifyEmailCode(email: string, code: string): Promise<boolean> {
  // TODO(spring-boot): POST /api/auth/email/verify  body: { email, code }  (Ditto: /user/email-verification)
  void email;
  return /^\d{6}$/.test(code);
}

// ───────────────────────── Photos ─────────────────────────

/** Downscale to ≤720px JPEG so the mock DB fits in localStorage. */
async function toDataUrl(blobUrl: string): Promise<string> {
  const img = new Image();
  img.src = blobUrl;
  await img.decode();
  const scale = Math.min(1, 720 / Math.max(img.naturalWidth, img.naturalHeight));
  const canvas = document.createElement('canvas');
  canvas.width = Math.round(img.naturalWidth * scale);
  canvas.height = Math.round(img.naturalHeight * scale);
  canvas.getContext('2d')!.drawImage(img, 0, 0, canvas.width, canvas.height);
  return canvas.toDataURL('image/jpeg', 0.82);
}

type PhotoTarget = 'photos' | 'idealPhotos';

/** Preview immediately via URL.createObjectURL, then swap in a persisted data URL. */
export async function uploadPhotos(files: File[], target: PhotoTarget = 'photos'): Promise<string[]> {
  // TODO(spring-boot): POST /api/photos/upload-url → PUT object storage → POST /api/photos/finalize
  //   (Ditto: /user/profile/photo/mint-upload-url, /finalize, 07 §2). Ideal look → idealDateImages.
  const previews = files.map((f) => URL.createObjectURL(f));
  db().set({ [target]: [...db()[target], ...previews].slice(0, 6) });
  await Promise.all(
    previews.map(async (blob) => {
      try {
        const data = await toDataUrl(blob);
        db().set({ [target]: db()[target].map((u) => (u === blob ? data : u)) });
        URL.revokeObjectURL(blob);
      } catch {
        // HEIC etc. that the browser can't decode keep their in-memory preview.
      }
    }),
  );
  return db()[target];
}

export async function setPhotos(urls: string[], target: PhotoTarget = 'photos'): Promise<void> {
  // TODO(spring-boot): PUT /api/photos/order  body: { ids }  (removals: DELETE /api/photos/{id})
  db().set({ [target]: urls });
}

// ───────────────────────── Profile ─────────────────────────

/** Saving one answer from /onboarding/edit/:field. */
export async function updateProfileField(questionId: string, value: unknown): Promise<void> {
  // TODO(spring-boot): PUT /api/profile/{field}  body: { value }
  await simulateLatency();
  useOnboarding.getState().setAnswer(questionId, value);
}

export async function saveWhere(value: LocationValue): Promise<void> {
  // TODO(spring-boot): PUT /api/profile/location  body: { placeId, mode, minutes }  (Ditto: /location/isochrone, 09 §19)
  useOnboarding.getState().setAnswer('location', value);
}

export async function searchPlaces(query: string, lang: 'en' | 'zh'): Promise<Place[]> {
  // TODO(spring-boot): GET /api/places/search?q={query}
  return searchLocal(query, lang);
}

// ───────────────────────── Blocklist ─────────────────────────

export async function getBlocks(): Promise<BlockEntry[]> {
  // TODO(spring-boot): GET /api/blocks
  return db().blocks;
}

export async function addBlock(entry: Omit<BlockEntry, 'id' | 'createdAt'>): Promise<BlockEntry> {
  // TODO(spring-boot): POST /api/blocks  body: { name, emails, phones, note }
  const row: BlockEntry = { ...entry, id: uid('blk'), createdAt: new Date().toISOString() };
  db().set({ blocks: [row, ...db().blocks] });
  return row;
}

export async function deleteBlock(id: string): Promise<void> {
  // TODO(spring-boot): DELETE /api/blocks/{id}
  db().set({ blocks: db().blocks.filter((b) => b.id !== id) });
}

// ───────────────────────── Matches (推断设计) ─────────────────────────

const ORDER: MatchStage[] = [
  'waiting',
  'emptyWeek',
  'revealed',
  'declined',
  'awaitingPartner',
  'confirmed',
  'dateDay',
  'checkedIn',
  'feedback',
  'awaitingFeedback',
  'mutual',
  'closed',
  'reported',
];
const atLeast = (stage: MatchStage, min: MatchStage) => ORDER.indexOf(stage) >= ORDER.indexOf(min);

function buildMatch(stage: MatchStage): Match {
  const partner = db().partnerId ? partnerById(db().partnerId!) : defaultPartnerFor(answers());
  const now = new Date();
  const dropped = new Date(nextDrop(now).getTime() - 7 * 86400_000);
  const slots = proposedSlots(now).map((d, i) => ({ id: `slot_${i}`, startsAt: d.toISOString() }));
  const m: Match = {
    id: `m_${partner.id}_${dropped.toISOString().slice(0, 10)}`,
    partnerId: partner.id,
    stage,
    droppedAt: dropped.toISOString(),
    respondBy: respondBy(dropped).toISOString(),
    slots,
    mySlotIds: [],
  };
  const inLoop = !['waiting', 'emptyWeek', 'revealed', 'declined', 'reported'].includes(stage);
  if (inLoop) m.mySlotIds = ['slot_0', 'slot_1'];
  if (inLoop && stage !== 'awaitingPartner') m.confirmedSlotId = 'slot_0';
  if (atLeast(stage, 'checkedIn') && stage !== 'reported') m.checkedInAt = now.toISOString();
  if (['awaitingFeedback', 'mutual', 'closed'].includes(stage)) {
    m.feedback = { met: 'met', rating: 5, again: stage === 'closed' ? 'unsure' : 'yes', comment: '', feltSafe: true };
  }
  if (stage === 'declined') m.declineReasons = ['noTime'];
  if (stage === 'reported') m.reportCategory = 'other';
  return m;
}

function currentMatch(): Match {
  return db().match ?? buildMatch('waiting');
}

/** Current week's match (+ partner card, AI reasons, interest chips); stage `waiting` before Wednesday's drop. */
export async function getCurrentMatch(): Promise<MatchView> {
  // TODO(spring-boot): GET /api/matches/current
  return matchView(currentMatch(), answers());
}

function patchMatch(patch: Partial<Match>) {
  const m = currentMatch();
  db().set({ match: { ...m, ...patch } });
}

/** "I'm in" on the poster; the slot picks follow in confirmSlots (01 §4.3: two gates — the person, then the time). */
export async function acceptMatch(matchId: string): Promise<void> {
  // TODO(spring-boot): POST /api/matches/{id}/accept
  void matchId;
}

/** Contact is only released once both said "see them again" (01 §4.3, privacy policy). */
export async function getContact(matchId: string): Promise<{ wechat: string } | null> {
  // TODO(spring-boot): GET /api/matches/{id}/contact  (403 unless mutual)
  void matchId;
  const m = currentMatch();
  return m.stage === 'mutual' ? { wechat: partnerById(m.partnerId).wechat } : null;
}

/** The slots I can make from the proposed 2–3 (01 §5.3: availability, not a fixed time). */
export async function confirmSlots(matchId: string, slotIds: string[]): Promise<void> {
  // TODO(spring-boot): POST /api/matches/{id}/slots/confirm  body: { slotIds }
  void matchId;
  patchMatch({ stage: 'awaitingPartner', mySlotIds: slotIds });
}

export async function declineMatch(matchId: string, reasons: string[], note: string): Promise<void> {
  // TODO(spring-boot): POST /api/matches/{id}/decline  body: { reasons, note }  (not forwarded to them, PRD §4.4)
  void matchId;
  patchMatch({ stage: 'declined', declineReasons: reasons, declineNote: note });
}

/** "This reason is off" on a single AI reason (PRD §4.4). */
export async function flagReason(matchId: string, index: number): Promise<void> {
  // TODO(spring-boot): POST /api/matches/{id}/reasons/{index}/flag
  void matchId;
  const m = currentMatch();
  const cur = m.flaggedReasons ?? [];
  patchMatch({ flaggedReasons: cur.includes(index) ? cur.filter((i) => i !== index) : [...cur, index] });
}

export async function checkIn(matchId: string): Promise<void> {
  // TODO(spring-boot): POST /api/matches/{id}/checkin
  void matchId;
  patchMatch({ stage: 'checkedIn', checkedInAt: new Date().toISOString() });
}

export async function submitFeedback(matchId: string, feedback: Feedback): Promise<void> {
  // TODO(spring-boot): POST /api/matches/{id}/feedback
  void matchId;
  patchMatch({ stage: 'awaitingFeedback', feedback });
}

export async function reportMatch(matchId: string, category: string, note: string): Promise<void> {
  // TODO(spring-boot): POST /api/matches/{id}/report  body: { category, note }  (PRD §4.9)
  void matchId;
  void note;
  patchMatch({ stage: 'reported', reportCategory: category });
}

/** Resolves an `awaitingFeedback` match once the other side has answered (server push in production). */
function resolveFeedback() {
  const m = currentMatch();
  const mutual = m.feedback?.met === 'met' && m.feedback.again === 'yes' && db().partnerWantsAgain;
  patchMatch({ stage: mutual ? 'mutual' : 'closed' });
}

export interface MatchView {
  match: Match;
  partner: ReturnType<typeof partnerById>;
  reasons: ReturnType<typeof reasonsFor>;
  interests: ReturnType<typeof sharedInterests>;
}

export function matchView(match: Match, a: Answers): MatchView {
  const partner = partnerById(match.partnerId);
  return { match, partner, reasons: reasonsFor(partner, a), interests: sharedInterests(partner, a) };
}

export async function enableMatchAlerts(): Promise<void> {
  // TODO(spring-boot): PUT /api/me/notifications  body: { sms: true }
  db().set({ alertsOn: true });
}

export async function getInvite(): Promise<{ url: string }> {
  // TODO(spring-boot): GET /api/referrals/me
  return { url: meSnapshot().inviteUrl };
}

// ───────────────────────── Demo controls ─────────────────────────

function resetEverything() {
  useOnboarding.getState().reset();
  db().reset();
}

export const demo = {
  setStage(stage: MatchStage) {
    db().set({ match: buildMatch(stage) });
  },
  setPartner(id: string) {
    db().set({ partnerId: id });
    const m = db().match;
    if (m) db().set({ match: { ...buildMatch(m.stage), mySlotIds: m.mySlotIds } });
  },
  setPartnerWantsAgain(v: boolean) {
    db().set({ partnerWantsAgain: v });
  },
  /** Partner picks one of my slots → both confirmed. */
  partnerConfirms() {
    const m = currentMatch();
    patchMatch({ stage: 'confirmed', confirmedSlotId: m.mySlotIds[0] ?? 'slot_0' });
  },
  openDateDay() {
    patchMatch({ stage: 'dateDay' });
  },
  endDate() {
    patchMatch({ stage: 'feedback' });
  },
  partnerSubmitsFeedback() {
    resolveFeedback();
  },
  /** Complete onboarding with sample answers + generated photos. */
  seedProfile() {
    const ob = useOnboarding.getState();
    ob.setAnswers({ ...SAMPLE_ANSWERS, photos: 2 });
    ob.complete();
    if (db().photos.length < 2) db().set({ photos: samplePhotoUris() });
  },
  resetMatch() {
    db().set({ match: null });
  },
  resetAll: resetEverything,
};
