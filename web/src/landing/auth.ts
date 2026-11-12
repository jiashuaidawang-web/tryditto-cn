import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { useOnboarding } from '../onboarding/store';

/** Demo SMS code. The real flow never ships a code to the client. */
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

export type SendCodeResult = { ok: true; cooldown: number } | { ok: false; error: 'phone' };

/** TODO: POST /api/auth/sms/send { phone } on the Spring Boot backend. */
export async function sendCode(phone: string): Promise<SendCodeResult> {
  if (!isValidPhone(phone)) return { ok: false, error: 'phone' };
  await delay(500);
  return { ok: true, cooldown: CODE_COOLDOWN_S };
}

export type LoginResult = { ok: true } | { ok: false; error: 'phone' | 'code' | 'wrong' };

/** TODO: POST /api/auth/login { phone, code } → { token }. */
export async function login(phone: string, code: string): Promise<LoginResult> {
  if (!isValidPhone(phone)) return { ok: false, error: 'phone' };
  if (!isValidCode(code)) return { ok: false, error: 'code' };
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
