/**
 * Local database (localStorage). Only src/api/index.ts writes to it; pages read through the hooks
 * re-exported from there. With the real backend it is a cache of the server's photo URLs plus the
 * match-loop demo state, which stays local until M2.
 */
import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { BlockEntry, Match } from './types';

export interface AppDb {
  photos: string[];
  idealPhotos: string[];
  blocks: BlockEntry[];
  match: Match | null;
  /** Demo: partner forced from the demo panel. */
  partnerId: string | null;
  /** Demo: how the partner answers "want to see them again?". */
  partnerWantsAgain: boolean;
  phone: string;
  deactivated: boolean;
  /** SMS alert when the weekly match drops (Ditto: Verify with iMessage, 09 §10.1). */
  alertsOn: boolean;
  /** Bumped after a server write that no cached field reflects, so useLive reloads (blocklist). */
  rev: number;
  set: (patch: Partial<Omit<AppDb, 'set' | 'reset'>>) => void;
  reset: () => void;
}

const initial = {
  photos: [] as string[],
  idealPhotos: [] as string[],
  blocks: [] as BlockEntry[],
  match: null as Match | null,
  partnerId: null as string | null,
  partnerWantsAgain: true,
  phone: '',
  deactivated: false,
  alertsOn: false,
  rev: 0,
};

export const useDb = create<AppDb>()(
  persist(
    (set) => ({
      ...initial,
      set: (patch) => set(patch),
      reset: () => set({ ...initial }),
    }),
    {
      name: 'jym-app-v1',
      // Object URLs die on reload; only persisted data URLs survive.
      partialize: ({ set: _s, reset: _r, ...rest }) => ({
        ...rest,
        photos: rest.photos.filter((u) => !u.startsWith('blob:')),
        idealPhotos: rest.idealPhotos.filter((u) => !u.startsWith('blob:')),
      }),
    },
  ),
);
