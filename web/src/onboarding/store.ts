import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { Answers } from './types';

interface OnboardingState {
  answers: Answers;
  /** Page currently shown. */
  currentId: string | null;
  /** Furthest page reached — the resume point. */
  furthestId: string | null;
  completed: boolean;
  /** Local object URLs for the photos page (UI only, not persisted). */
  photoUrls: string[];
  setAnswer: (id: string, value: unknown) => void;
  setAnswers: (patch: Answers) => void;
  removeAnswers: (ids: string[]) => void;
  goTo: (id: string, furthest?: string | null) => void;
  setPhotoUrls: (urls: string[]) => void;
  complete: () => void;
  reset: () => void;
}

export const useOnboarding = create<OnboardingState>()(
  persist(
    (set) => ({
      answers: {},
      currentId: null,
      furthestId: null,
      completed: false,
      photoUrls: [],
      setAnswer: (id, value) => set((s) => ({ answers: { ...s.answers, [id]: value } })),
      setAnswers: (patch) => set((s) => ({ answers: { ...s.answers, ...patch } })),
      removeAnswers: (ids) =>
        set((s) => {
          const answers = { ...s.answers };
          ids.forEach((id) => delete answers[id]);
          return { answers };
        }),
      goTo: (id, furthest) => set((s) => ({ currentId: id, furthestId: furthest === undefined ? s.furthestId : furthest })),
      setPhotoUrls: (photoUrls) => set({ photoUrls }),
      complete: () => set({ completed: true }),
      reset: () => set({ answers: {}, currentId: null, furthestId: null, completed: false, photoUrls: [] }),
    }),
    {
      name: 'jym-onboarding-v1',
      partialize: ({ answers, currentId, furthestId, completed }) => ({ answers, currentId, furthestId, completed }),
    },
  ),
);
