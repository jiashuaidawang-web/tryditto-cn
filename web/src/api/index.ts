import { t, type T } from '../i18n/lang';
import { useAuth } from '../landing/auth';
import { SAVE_LATENCY_MS } from '../motion/tokens';
import { useOnboarding } from '../onboarding/store';
import type { Answers, LocationValue } from '../onboarding/types';
import { samplePhotoUris } from './art';
import { useDb } from './db';
import { ApiError, http } from './http';
import { SAMPLE_ANSWERS, defaultPartnerFor, partnerById, reasonsFor, sharedInterests } from './mock';
import { searchPlaces as searchLocal, type Place } from './places';
import { MOCK_API, clearLocal, type ServerPhoto } from './session';
import { nextDrop, proposedSlots, respondBy } from './time';
import type { AvatarSpec, BlockEntry, Feedback, Match, MatchStage, Me, Partner, SceneKind } from './types';

/**
 * Backend seam. Pages never touch the stores for writes. With the real API (default build) account,
 * onboarding, profile, photos, places and blocklist go to Spring Boot and the local stores are a write-through
 * cache; the match loop stays local until M2. `VITE_API=mock` resolves everything against the local stores.
 */

export type { BlockEntry, Feedback, Match, MatchStage, Me, Partner, Reason, Slot, Venue } from './types';
export { useDb } from './db';
export { ApiError } from './http';
export { MOCK_API, syncOnce } from './session';
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
  /** Furthest page reached (F9); pages without an answer (photos, interstitials) only move this. */
  saveProgress(furthestStepId: string | null): Promise<void>;
  getProgress(): Promise<OnboardingProgress>;
}

const simulateLatency = () =>
  new URLSearchParams(window.location.search).get('latency') === '1'
    ? new Promise<void>((r) => setTimeout(r, SAVE_LATENCY_MS))
    : Promise.resolve();

const db = () => useDb.getState();
const answers = () => useOnboarding.getState().answers;
const uid = (prefix: string) => `${prefix}_${Math.random().toString(36).slice(2, 10)}`;
const bump = () => db().set({ rev: db().rev + 1 });

function setLocalAnswer(questionId: string, value: unknown) {
  const ob = useOnboarding.getState();
  if (value === undefined || value === null) ob.removeAnswers([questionId]);
  else ob.setAnswer(questionId, value);
}

/** Answers the server does not store: photo count comes from the photo API, the code page from email verify. */
const NOT_STORED = new Set(['photos']);

export const api: OnboardingApi = {
  async saveAnswer(questionId, value) {
    await simulateLatency();
    if (!MOCK_API && !NOT_STORED.has(questionId)) {
      await http('PUT', `/api/onboarding/answers/${encodeURIComponent(questionId)}`, { value: value ?? null });
    }
    useOnboarding.getState().setAnswer(questionId, value);
  },
  async saveProgress(furthestStepId) {
    if (MOCK_API || !furthestStepId) return;
    await http('PUT', '/api/onboarding/progress', { furthestStepId });
  },
  async getProgress() {
    if (!MOCK_API) return http<OnboardingProgress>('GET', '/api/onboarding/progress');
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
  if (!MOCK_API) return http<Me>('GET', '/api/me');
  return meSnapshot();
}

export interface Profile {
  answers: Answers;
  photos: string[];
  idealPhotos: string[];
}

/**
 * Everything /you, /edit-profile, /deep-info and /where render. Read from the local cache in both modes:
 * the server copy is pulled at login and page load (session.ts) and every write goes through first.
 */
export async function getProfile(): Promise<Profile> {
  return { answers: answers(), photos: db().photos, idealPhotos: db().idealPhotos };
}

export async function completeOnboarding(): Promise<void> {
  if (!MOCK_API) await http('POST', '/api/onboarding/complete');
  useOnboarding.getState().complete();
}

export async function logout(): Promise<void> {
  if (MOCK_API) {
    useAuth.getState().logout();
    return;
  }
  await http('POST', '/api/auth/logout').catch(() => undefined);
  clearLocal();
}

export async function deactivateAccount(): Promise<void> {
  if (MOCK_API) {
    db().set({ deactivated: true });
    return;
  }
  // Hides the profile and ends every session; logging in again reactivates (技术方案 §5.5).
  await http('POST', '/api/account/deactivate');
  clearLocal();
}

export async function deleteAccount(): Promise<void> {
  if (MOCK_API) {
    resetEverything();
    return;
  }
  await http('DELETE', '/api/account');
  clearLocal();
}

// ───────────────────────── Email code ─────────────────────────

export async function sendEmailCode(email: string): Promise<{ sentTo: string }> {
  if (!MOCK_API) return http<{ sentTo: string }>('POST', '/api/auth/email/code', { email });
  await new Promise((r) => setTimeout(r, 900));
  return { sentTo: email };
}

/** Mock: any 6 digits pass. Real: the demo login code (no mail is sent in demo mode). */
export async function verifyEmailCode(email: string, code: string): Promise<boolean> {
  if (!MOCK_API) {
    try {
      return (await http<{ verified: boolean }>('POST', '/api/auth/email/verify', { email, code })).verified;
    } catch (e) {
      if (e instanceof ApiError && e.status === 429) return false;
      throw e;
    }
  }
  void email;
  return /^\d{6}$/.test(code);
}

// ───────────────────────── Photos ─────────────────────────

async function drawScaled(blobUrl: string, maxEdge: number): Promise<HTMLCanvasElement> {
  const img = new Image();
  img.src = blobUrl;
  await img.decode();
  const scale = Math.min(1, maxEdge / Math.max(img.naturalWidth, img.naturalHeight));
  const canvas = document.createElement('canvas');
  canvas.width = Math.round(img.naturalWidth * scale);
  canvas.height = Math.round(img.naturalHeight * scale);
  canvas.getContext('2d')!.drawImage(img, 0, 0, canvas.width, canvas.height);
  return canvas;
}

/** Downscale to ≤720px JPEG so the mock DB fits in localStorage. */
async function toDataUrl(blobUrl: string): Promise<string> {
  return (await drawScaled(blobUrl, 720)).toDataURL('image/jpeg', 0.82);
}

/** The server re-encodes anyway; sending a ≤1440px JPEG keeps uploads small and turns HEIC-ish inputs the browser can decode into JPEG. */
async function toJpegBlob(file: File, blobUrl: string): Promise<Blob> {
  try {
    const canvas = await drawScaled(blobUrl, 1440);
    const blob = await new Promise<Blob | null>((r) => canvas.toBlob(r, 'image/jpeg', 0.88));
    if (blob) return blob;
  } catch {
    // Fall through: let the server judge the original bytes.
  }
  return file;
}

type PhotoTarget = 'photos' | 'idealPhotos';
const kindOf = (target: PhotoTarget) => (target === 'photos' ? 'profile' : 'ideal');
const photoIdOf = (url: string) => /^\/api\/photos\/(\d+)\/raw/.exec(url)?.[1] ?? null;

async function uploadOne(file: File, blobUrl: string, target: PhotoTarget): Promise<string> {
  const body = await toJpegBlob(file, blobUrl);
  const contentType = body.type || 'image/jpeg';
  const ticket = await http<{ uploadId: string; url: string }>('POST', '/api/photos/upload-url', {
    kind: kindOf(target),
    contentType,
    bytes: body.size,
  });
  await http('PUT', ticket.url, body, contentType);
  const photo = await http<ServerPhoto>('POST', '/api/photos/finalize', { uploadId: ticket.uploadId });
  return photo.url;
}

async function refreshPhotos(target: PhotoTarget) {
  const list = await http<ServerPhoto[]>('GET', `/api/photos?kind=${kindOf(target)}`);
  db().set({ [target]: list.map((p) => p.url) });
}

/** Preview immediately via URL.createObjectURL, then swap in the persisted URL. */
export async function uploadPhotos(files: File[], target: PhotoTarget = 'photos'): Promise<string[]> {
  const previews = files.map((f) => URL.createObjectURL(f));
  db().set({ [target]: [...db()[target], ...previews].slice(0, 6) });
  if (!MOCK_API) {
    // One at a time so the server's positions follow the order the files were picked in.
    for (const [i, file] of files.entries()) {
      const blob = previews[i]!;
      if (!db()[target].includes(blob)) continue;
      try {
        const url = await uploadOne(file, blob, target);
        db().set({ [target]: db()[target].map((u) => (u === blob ? url : u)) });
      } catch (e) {
        db().set({ [target]: db()[target].filter((u) => u !== blob) });
        console.warn('photo upload failed', e);
      } finally {
        URL.revokeObjectURL(blob);
      }
    }
    return db()[target];
  }
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

/** Reorder and remove: the server gets DELETE for each dropped photo, then the new order (F3: ids, not URLs). */
export async function setPhotos(urls: string[], target: PhotoTarget = 'photos'): Promise<void> {
  const before = db()[target];
  db().set({ [target]: urls });
  if (MOCK_API) return;
  const keep = urls.map(photoIdOf).filter((id): id is string => !!id);
  const removed = before.map(photoIdOf).filter((id): id is string => !!id && !keep.includes(id));
  const order = before.map(photoIdOf).filter((id): id is string => !!id && keep.includes(id));
  try {
    for (const id of removed) await http('DELETE', `/api/photos/${id}`);
    if (keep.length > 1 && keep.join() !== order.join()) await http('PUT', '/api/photos/order', { kind: kindOf(target), ids: keep });
  } catch (e) {
    await refreshPhotos(target).catch(() => undefined);
    throw e;
  }
}

// ───────────────────────── Profile ─────────────────────────

/** Saving one answer from /onboarding/edit/:field. `undefined` deletes it (stale hobby branches). */
export async function updateProfileField(questionId: string, value: unknown): Promise<void> {
  await simulateLatency();
  if (!MOCK_API) {
    await http('PUT', `/api/profile/${encodeURIComponent(questionId)}`, { value: value ?? null });
    setLocalAnswer(questionId, value);
    return;
  }
  useOnboarding.getState().setAnswer(questionId, value);
}

export async function saveWhere(value: LocationValue): Promise<void> {
  if (!MOCK_API) await http('PUT', '/api/profile/location', { placeId: value.placeId, mode: value.mode, minutes: value.minutes });
  useOnboarding.getState().setAnswer('location', value);
}

export async function searchPlaces(query: string, lang: 'en' | 'zh'): Promise<Place[]> {
  if (!MOCK_API) {
    if (!query.trim()) return [];
    return http<Place[]>('GET', `/api/places/search?q=${encodeURIComponent(query)}&lang=${lang}`);
  }
  return searchLocal(query, lang);
}

// ───────────────────────── Blocklist ─────────────────────────

export async function getBlocks(): Promise<BlockEntry[]> {
  if (!MOCK_API) return http<BlockEntry[]>('GET', '/api/blocks');
  return db().blocks;
}

export async function addBlock(entry: Omit<BlockEntry, 'id' | 'createdAt'>): Promise<BlockEntry> {
  if (!MOCK_API) {
    const row = await http<BlockEntry>('POST', '/api/blocks', entry);
    bump();
    return row;
  }
  const row: BlockEntry = { ...entry, id: uid('blk'), createdAt: new Date().toISOString() };
  db().set({ blocks: [row, ...db().blocks] });
  return row;
}

export async function deleteBlock(id: string): Promise<void> {
  if (!MOCK_API) {
    await http('DELETE', `/api/blocks/${encodeURIComponent(id)}`);
    bump();
    return;
  }
  db().set({ blocks: db().blocks.filter((b) => b.id !== id) });
}

// ───────────────────────── Matches (推断设计, local until M2) ─────────────────────────

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

const both = (s: string | null | undefined): T => t(s ?? '', s ?? '');

const STUB_AVATAR: AvatarSpec = { bg: ['#ffb4a2', '#ffd6a5'], skin: '#f6d1b5', hair: 'bob', hairColor: '#2b1d1a', shirt: '#5b8def' };

function stubPartner(): Partner {
  const empty = both('');
  return {
    id: '',
    name: empty,
    gender: 'woman',
    age: 0,
    school: empty,
    major: empty,
    year: empty,
    heightCm: 0,
    hobbies: [],
    extraTags: [],
    avatar: STUB_AVATAR,
    scenes: ['coffee', 'books'],
    headline: empty,
    blurb: [],
    wechat: '',
    venue: { id: '', name: empty, address: empty, activity: empty, extra: empty },
    openers: [],
    fallbackReasons: [],
  };
}

interface RawPartner {
  id: string;
  name: string;
  gender: string;
  age: number;
  school: string;
  major: string;
  year: string;
  heightCm: number;
  hobbies: string[];
  extraTags: string[];
  avatar: AvatarSpec;
  scenes: string[];
  headline: string;
  blurb: string[];
  wechat: string;
  venue: { id: string; name: string; address: string; activity: string; extra: string };
  openers: string[];
}

interface RawView {
  match: Match;
  partner: RawPartner | null;
  reasons: { text: string; basis: string; ai?: boolean }[];
  interests: { label: string; shared: boolean }[];
  partnerWantsAgain?: boolean;
}

function adaptPartner(p: RawPartner): Partner {
  const gender = p.gender === 'man' || p.gender === 'woman' || p.gender === 'nonbinary' ? p.gender : 'nonbinary';
  const scenes = (p.scenes?.length ? p.scenes : ['coffee', 'books']) as SceneKind[];
  return {
    id: p.id,
    name: both(p.name),
    gender,
    age: p.age,
    school: both(p.school),
    major: both(p.major),
    year: both(p.year),
    heightCm: p.heightCm,
    hobbies: p.hobbies ?? [],
    extraTags: (p.extraTags ?? []).map((s) => both(s)),
    avatar: p.avatar ?? STUB_AVATAR,
    scenes,
    headline: both(p.headline),
    blurb: (p.blurb ?? []).map((s) => both(s)),
    wechat: p.wechat ?? '',
    venue: {
      id: p.venue?.id ?? '',
      name: both(p.venue?.name),
      address: both(p.venue?.address),
      activity: both(p.venue?.activity),
      extra: both(p.venue?.extra),
    },
    openers: (p.openers ?? []).map((s) => both(s)),
    fallbackReasons: [],
  };
}

function adaptView(raw: RawView): MatchView {
  return {
    match: raw.match,
    partner: raw.partner ? adaptPartner(raw.partner) : stubPartner(),
    reasons: (raw.reasons ?? []).map((r) => ({ text: both(r.text), basis: both(r.basis), ai: r.ai })),
    interests: (raw.interests ?? []).map((i) => ({ label: both(i.label), shared: i.shared })),
  };
}

async function touch(path: string, body?: object) {
  await http('POST', path, body);
  bump();
}

/** Current week's match (+ partner card, reasons, interest chips); stage `waiting` before Wednesday's drop. */
export async function getCurrentMatch(): Promise<MatchView> {
  if (!MOCK_API) return adaptView(await http<RawView>('GET', '/api/matches/current'));
  return matchView(currentMatch(), answers());
}

function patchMatch(patch: Partial<Match>) {
  const m = currentMatch();
  db().set({ match: { ...m, ...patch } });
}

/** "I'm in" on the poster; the slot picks follow in confirmSlots (01 §4.3: two gates — the person, then the time). */
export async function acceptMatch(matchId: string): Promise<void> {
  if (!MOCK_API) return touch(`/api/matches/${matchId}/accept`);
}

/** Contact is only released once both said "see them again" (01 §4.3, privacy policy). */
export async function getContact(matchId: string): Promise<{ wechat: string } | null> {
  if (!MOCK_API) {
    const c = await http<{ wechat: string | null }>('GET', `/api/matches/${matchId}/contact`);
    return c.wechat ? { wechat: c.wechat } : null;
  }
  const m = currentMatch();
  return m.stage === 'mutual' ? { wechat: partnerById(m.partnerId).wechat } : null;
}

/** The slots I can make from the proposed 2–3 (01 §5.3: availability, not a fixed time). */
export async function confirmSlots(matchId: string, slotIds: string[]): Promise<void> {
  if (!MOCK_API) return touch(`/api/matches/${matchId}/slots/confirm`, { slotIds });
  patchMatch({ stage: 'awaitingPartner', mySlotIds: slotIds });
}

export async function declineMatch(matchId: string, reasons: string[], note: string): Promise<void> {
  if (!MOCK_API) return touch(`/api/matches/${matchId}/decline`, { reasons, note });
  patchMatch({ stage: 'declined', declineReasons: reasons, declineNote: note });
}

/** "This reason is off" on a single reason (PRD §4.4). */
export async function flagReason(matchId: string, index: number): Promise<void> {
  if (!MOCK_API) return touch(`/api/matches/${matchId}/reasons/${index}/flag`);
  const m = currentMatch();
  const cur = m.flaggedReasons ?? [];
  patchMatch({ flaggedReasons: cur.includes(index) ? cur.filter((i) => i !== index) : [...cur, index] });
}

export async function checkIn(matchId: string): Promise<void> {
  if (!MOCK_API) return touch(`/api/matches/${matchId}/checkin`);
  patchMatch({ stage: 'checkedIn', checkedInAt: new Date().toISOString() });
}

export async function submitFeedback(matchId: string, feedback: Feedback): Promise<void> {
  if (!MOCK_API) return touch(`/api/matches/${matchId}/feedback`, feedback);
  patchMatch({ stage: 'awaitingFeedback', feedback });
}

export async function reportMatch(matchId: string, category: string, note: string): Promise<void> {
  if (!MOCK_API) return touch(`/api/matches/${matchId}/report`, { category, note });
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
  if (!MOCK_API) return http<{ url: string }>('GET', '/api/referrals/me');
  return { url: meSnapshot().inviteUrl };
}

// ───────────────────────── Demo controls ─────────────────────────

function resetEverything() {
  useOnboarding.getState().reset();
  db().reset();
}

export const demo = {
  async setStage(stage: MatchStage) {
    if (!MOCK_API) return touch('/api/demo/match/stage', { stage });
    db().set({ match: buildMatch(stage) });
  },
  async setPartner(id: string) {
    if (!MOCK_API) return touch('/api/demo/match/partner', { partnerId: id });
    db().set({ partnerId: id });
    const m = db().match;
    if (m) db().set({ match: { ...buildMatch(m.stage), mySlotIds: m.mySlotIds } });
  },
  async setPartnerWantsAgain(v: boolean) {
    if (!MOCK_API) await touch('/api/demo/match/partner-feedback-intent', { wantsAgain: v });
    db().set({ partnerWantsAgain: v });
  },
  /** Partner picks one of my slots → both confirmed. */
  async partnerConfirms() {
    if (!MOCK_API) return touch('/api/demo/match/partner-action', { action: 'confirm' });
    const m = currentMatch();
    patchMatch({ stage: 'confirmed', confirmedSlotId: m.mySlotIds[0] ?? 'slot_0' });
  },
  async openDateDay() {
    if (!MOCK_API) return touch('/api/demo/match/time', { to: 'dateDay' });
    patchMatch({ stage: 'dateDay' });
  },
  async endDate() {
    if (!MOCK_API) return touch('/api/demo/match/time', { to: 'ended' });
    patchMatch({ stage: 'feedback' });
  },
  async partnerSubmitsFeedback() {
    if (!MOCK_API) return touch('/api/demo/match/partner-action', { action: 'feedback' });
    resolveFeedback();
  },
  /** Mock only: complete onboarding with sample answers + generated photos. The real API needs a real walk-through. */
  seedProfile() {
    if (!MOCK_API) return;
    const ob = useOnboarding.getState();
    ob.setAnswers({ ...SAMPLE_ANSWERS, photos: 2 });
    ob.complete();
    if (db().photos.length < 2) db().set({ photos: samplePhotoUris() });
  },
  async resetMatch() {
    if (!MOCK_API) return touch('/api/demo/reset-me');
    db().set({ match: null });
  },
  /** Real API resets this demo account's match on the server and leaves every real user alone. */
  resetAll() {
    if (!MOCK_API) return touch('/api/demo/reset-me');
    resetEverything();
  },
};
