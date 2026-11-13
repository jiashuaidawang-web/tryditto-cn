import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { ApiError, http } from '../api/http';
import { MOCK_API, clearLocal, hydrate } from '../api/session';
import { useOnboarding } from '../onboarding/store';

/** Demo SMS code shown on the login page. The server's DEMO_LOGIN_CODE is what actually counts. */
export const DEMO_CODE = import.meta.env.VITE_DEMO_CODE || '888888';
export const CODE_COOLDOWN_S = 60;

export const isValidPhone = (phone: string) => /^1[3-9]\d{9}$/.test(phone);
export const isValidCode = (code: string) => /^\d{6}$/.test(code);

interface AuthState {
  phone: string | null;
  token: string | null;
  loggedInAt: number | null;
  logout: () => void;
}

export const useAuth = create<AuthState>()(
  persist(
    (set) => ({
      phone: null,
      token: null,
      loggedInAt: null,
      logout: () => set({ phone: null, token: null, loggedInAt: null }),
    }),
    { name: 'jym-auth-v1' },
  ),
);

const delay = (ms: number) => new Promise((r) => setTimeout(r, ms));

export type SendCodeResult = { ok: true; cooldown: number } | { ok: false; error: 'phone' | 'unavailable' };

/** POST /api/auth/sms/send { phone } → { cooldown }. Demo mode sends nothing; the server only rate-limits. */
export async function sendCode(phone: string): Promise<SendCodeResult> {
  if (!isValidPhone(phone)) return { ok: false, error: 'phone' };
  if (!MOCK_API) {
    try {
      const r = await http<{ cooldown: number }>('POST', '/api/auth/sms/send', { phone });
      return { ok: true, cooldown: r.cooldown };
    } catch (e) {
      if (e instanceof ApiError && e.code === 'INVALID_PHONE') return { ok: false, error: 'phone' };
      if (e instanceof ApiError && e.status === 429) return { ok: true, cooldown: e.retryAfter ?? CODE_COOLDOWN_S };
      return { ok: false, error: 'unavailable' };
    }
  }
  await delay(500);
  return { ok: true, cooldown: CODE_COOLDOWN_S };
}

export type LoginError = 'phone' | 'code' | 'wrong' | 'locked' | 'inactive' | 'network';
export type LoginResult = { ok: true } | { ok: false; error: LoginError; retryAfter?: number };

const LOGIN_ERRORS: Record<string, LoginError> = {
  INVALID_PHONE: 'phone',
  INVALID_CODE_FORMAT: 'code',
  AUTH_CODE_WRONG: 'wrong',
  AUTH_CODE_LOCKED: 'locked',
  ACCOUNT_INACTIVE: 'inactive',
};

/**
 * POST /api/auth/login { phone, code, ref? } → { token, expiresAt, me }. On success the local caches are
 * replaced with this account's server data before returning, so the caller can route on `completed`.
 */
export async function login(phone: string, code: string): Promise<LoginResult> {
  if (!isValidPhone(phone)) return { ok: false, error: 'phone' };
  if (!isValidCode(code)) return { ok: false, error: 'code' };
  if (!MOCK_API) {
    try {
      const ref = new URLSearchParams(window.location.search).get('ref') ?? undefined;
      const r = await http<{ token: string }>('POST', '/api/auth/login', { phone, code, ref });
      clearLocal();
      useAuth.setState({ phone, token: r.token, loggedInAt: Date.now() });
      await hydrate(true);
      return { ok: true };
    } catch (e) {
      if (e instanceof ApiError && LOGIN_ERRORS[e.code]) return { ok: false, error: LOGIN_ERRORS[e.code]!, retryAfter: e.retryAfter };
      clearLocal();
      return { ok: false, error: 'network' };
    }
  }
  await delay(600);
  if (code !== DEMO_CODE) return { ok: false, error: 'wrong' };
  useAuth.setState({ phone, token: `demo-${phone}-${Date.now()}`, loggedInAt: Date.now() });
  return { ok: true };
}

/** Where a logged-in user continues: the app if onboarding is done, else the questionnaire. */
export function useContinuePath(): string | null {
  const token = useAuth((s) => s.token);
  const completed = useOnboarding((s) => s.completed);
  if (!token) return null;
  return completed ? '/home' : '/onboarding';
}
