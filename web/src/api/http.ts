import { useAuth } from '../landing/auth';

/** RFC 9457 problem body from the Spring Boot API (技术方案 §6.1) plus the HTTP status. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly fields: string[] = [],
    readonly retryAfter?: number,
  ) {
    super(message);
  }
}

let onUnauthorized: (() => void) | null = null;

/** Called when a request that carried a token gets 401 (logged out elsewhere, deactivated, expired). */
export function setUnauthorizedHandler(fn: () => void) {
  onUnauthorized = fn;
}

type Body = BodyInit | object | undefined;

export async function http<T = void>(method: string, path: string, body?: Body, contentType?: string): Promise<T> {
  const token = useAuth.getState().token;
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  let payload: BodyInit | undefined;
  if (body instanceof Blob || body instanceof ArrayBuffer) {
    payload = body;
    if (contentType) headers['Content-Type'] = contentType;
  } else if (body !== undefined) {
    payload = JSON.stringify(body);
    headers['Content-Type'] = 'application/json';
  }

  let res: Response;
  try {
    res = await fetch(path, { method, headers, body: payload });
  } catch (e) {
    throw new ApiError(0, 'NETWORK', e instanceof Error ? e.message : 'network error');
  }
  if (res.status === 401 && token) onUnauthorized?.();
  if (!res.ok) {
    const p = (await res.json().catch(() => ({}))) as { code?: string; detail?: string; title?: string; fields?: string[]; retryAfter?: number };
    throw new ApiError(res.status, p.code ?? `HTTP_${res.status}`, p.detail ?? p.title ?? res.statusText, p.fields ?? [], p.retryAfter);
  }
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}
