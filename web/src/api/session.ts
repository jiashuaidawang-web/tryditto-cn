import { useAuth } from '../landing/auth';
import { useOnboarding } from '../onboarding/store';
import type { Answers } from '../onboarding/types';
import { useDb } from './db';
import { http, setUnauthorizedHandler } from './http';

/**
 * Build-time switch: `VITE_API=mock` keeps the original pure-localStorage demo; anything else talks to the
 * Spring Boot API under /api. The local stores then act as a cache that is filled from the server at login
 * and on every page load, and written through on every save.
 */
export const MOCK_API = import.meta.env.VITE_API === 'mock';

export interface ServerPhoto {
  id: string;
  url: string;
  position: number;
}

interface ServerProfile {
  answers: Answers;
  photos: ServerPhoto[];
  idealPhotos: ServerPhoto[];
  furthestStepId: string | null;
  completed: boolean;
}

/** Replaces the local caches with the server's copy. `fresh` = a new login, so the page cursor restarts too. */
export async function hydrate(fresh: boolean): Promise<void> {
  const p = await http<ServerProfile>('GET', '/api/profile');
  useOnboarding.setState((s) => ({
    answers: p.answers,
    furthestId: p.furthestStepId,
    completed: p.completed,
    currentId: fresh ? null : s.currentId,
    photoUrls: [],
  }));
  useDb.getState().set({ photos: p.photos.map((x) => x.url), idealPhotos: p.idealPhotos.map((x) => x.url) });
}

/** Drops the session and every cached answer, photo and demo state on this device. */
export function clearLocal(): void {
  useAuth.getState().logout();
  useOnboarding.getState().reset();
  useDb.getState().reset();
}

let synced = false;

/** Once per page load: pull the server's copy so edits made in another browser show up here. */
export function syncOnce(): void {
  if (MOCK_API || synced || !useAuth.getState().token) return;
  synced = true;
  hydrate(false).catch(() => {
    synced = false;
  });
}

if (!MOCK_API) {
  setUnauthorizedHandler(() => {
    clearLocal();
    if (window.location.pathname !== '/login') window.location.replace(`/login${window.location.search}`);
  });
}
