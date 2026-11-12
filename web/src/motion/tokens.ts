/**
 * All onboarding motion parameters, measured from Ditto pass3 recordings
 * (docs/research/12 §4, which supersedes 11 where they differ).
 * Times are in seconds unless the name ends with Ms.
 */

/** Exponential settle: offset(t) = offset0 * e^(-t/tau). 12 §4.1: ~55% left per 40ms frame → tau ≈ 67ms, no overshoot. */
export function expoSettle(durationMs: number, tauMs: number) {
  const k = durationMs / tauMs;
  const norm = 1 - Math.exp(-k);
  return (t: number) => (t >= 1 ? 1 : (1 - Math.exp(-k * t)) / norm);
}

export const page = {
  /** Old page flies up + fades. Visible 40–80ms, ease-in; frame 2 is 93–137px up. */
  exit: { y: -140, duration: 0.08, ease: [0.55, 0, 0.9, 0.4] as const },
  /** Empty gap between old content gone and new content first visible: 80–160ms, median 120. */
  gap: 0.1,
  /** New column first visible ~370–430px below its rest position. */
  enterFromY: 420,
  enterFromOpacity: 0.15,
  /** Settles (±0) in 320–400ms, median 360ms. */
  enterDurationMs: 420,
  enterTauMs: 67,
  /** Opacity goes faint → solid within 3–4 frames. */
  enterOpacityDuration: 0.14,
} as const;

export const pageEnterEase = expoSettle(page.enterDurationMs, page.enterTauMs);

export const bubble = {
  /** Empty pill is visible (moving with column) before the first word. Median 160ms after first visible frame. */
  firstWordDelayMs: 160,
  /** Regression slope 93ms/word (80/120 alternating at 25fps). */
  wordIntervalMs: 93,
  /** zh: reveal 2 characters per tick at the same pace. */
  zhCharsPerTick: 2,
  /** New word starts ~30% grey and goes solid in 1–2 frames. */
  wordFadeFrom: 0.3,
  wordFadeDuration: 0.06,
  /** Keyword letter wave: each letter +60ms, swell lasts 120–160ms. */
  waveLetterDelayMs: 60,
  waveLetterDuration: 0.14,
  waveScale: 1.28,
  /** Second bubble: empty pill shows on the frame after bubble 1 finishes, waits 200–240ms. */
  secondBubbleHoldMs: 220,
} as const;

export const pill = {
  /** Glass → #E40089. CSS 0.15s cubic-bezier(.4,0,.2,1); visible 160–200ms. */
  colorDuration: 0.15,
  colorEase: 'cubic-bezier(0.4, 0, 0.2, 1)',
  /** Selected pill grows leftwards 208 → 260px, S-curve, 280–320ms, a beat slower than color. */
  widthDuration: 0.32,
  widthEase: [0.45, 0, 0.35, 1] as const,
  widthUnselected: 208,
  widthSelected: 260,
  /** Check circle fades in ~200ms, slightly after the color. */
  checkDuration: 0.2,
  checkDelay: 0.05,
} as const;

export const progress = {
  /** 09 stylesheet: width 0.3s cubic-bezier(0.4,0,0.2,1); starts on the frame the new page mounts. */
  transition: 'width 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
} as const;

export const nextButton = {
  /** Disappears with the old page, fades in + slight scale in place (main change within 120ms, colour stable by 300ms). */
  enterDuration: 0.12,
  enterScaleFrom: 0.92,
  colorTransition: 'background-color 0.3s cubic-bezier(0.4, 0, 0.2, 1), color 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
} as const;

export const interstitial = {
  /** Spinner shown in place of a skipped step (N1/N2 photos slot): 0.9–1.0s. */
  skipSpinnerMs: 920,
} as const;

/**
 * Ditto waits for a save round-trip (~0.45s) before a normal page starts leaving; the intro page leaves at once.
 * Not network-verified (12 §2.5). The local mock api uses this only when `?latency=1`.
 */
export const SAVE_LATENCY_MS = 450;
