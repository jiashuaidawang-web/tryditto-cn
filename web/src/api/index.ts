import { SAVE_LATENCY_MS } from '../motion/tokens';
import { useOnboarding } from '../onboarding/store';
import type { Answers } from '../onboarding/types';

/**
 * Backend seam. Everything below resolves from the local zustand store for now.
 * TODO(spring-boot): replace bodies with fetch calls once the API exists:
 *   PUT  /api/onboarding/answers/{questionId}   body: { value }
 *   GET  /api/onboarding/progress               → OnboardingProgress
 */

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

export const api: OnboardingApi = {
  async saveAnswer(questionId, value) {
    await simulateLatency();
    // TODO(spring-boot): PUT /api/onboarding/answers/{questionId}
    useOnboarding.getState().setAnswer(questionId, value);
  },
  async getProgress() {
    // TODO(spring-boot): GET /api/onboarding/progress
    const { answers, furthestId, completed } = useOnboarding.getState();
    return { answers, furthestStepId: furthestId, completed };
  },
};
