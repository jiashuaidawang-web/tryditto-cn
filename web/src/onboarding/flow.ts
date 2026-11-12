import type { Lang } from '../i18n/lang';
import { BRANCHES, HOBBIES, QUESTIONS, type QuestionId } from './questions';
import type { Answers, Step } from './types';
import { isAnswered } from './validate';

export interface FlowFlags {
  /** Jump over pages that already hold a valid answer (Ditto shows drafts instead; default off). */
  skipAnswered: boolean;
  /**
   * Which segment-3 tail to use. 12 §2.6 could not pin the rule; `auto` reproduces all six recordings by
   * using the long tail whenever the ideal-match / describe-yourself pages are in play (hypothesis H1).
   * A fresh account needs photos, location and the preference tail, so the product default is `long`;
   * `?tail=auto|short` replays the recordings.
   */
  tail: 'auto' | 'short' | 'long';
}

export const DEFAULT_FLAGS: FlowFlags = { skipAnswered: false, tail: 'long' };

/** Max hobby branches asked per pass (questionnaire.json branchLimit: 3). */
export const BRANCH_LIMIT = 3;

const arr = (v: unknown): string[] => (Array.isArray(v) ? (v as string[]) : []);

/** questionnaire.json trigger: NOT(Man & [Women]) AND NOT(Woman & [Men]). Confirmed for Nonbinary → Everyone (12 §2.2). */
export function showsPositionPages(answers: Answers): boolean {
  const gender = answers.gender;
  const expected = arr(answers.expectedGender);
  if (typeof gender !== 'string' || expected.length === 0) return false;
  if (gender === 'man' && expected.length === 1 && expected[0] === 'women') return false;
  if (gender === 'woman' && expected.length === 1 && expected[0] === 'men') return false;
  return true;
}

/** Branches follow QUESTION-BANK order, not tap order (12 §2.3), capped at 3. */
export function branchHobbies(answers: Answers): string[] {
  const picked = arr(answers.hobbies);
  return HOBBIES.map((h) => h.id)
    .filter((id) => picked.includes(id))
    .slice(0, BRANCH_LIMIT);
}

export function isStudentPath(answers: Answers): boolean {
  return answers.isStudent === 'yes';
}

export function usesLongTail(answers: Answers, flags: FlowFlags): boolean {
  if (flags.tail === 'long') return true;
  if (flags.tail === 'short') return false;
  return showsPositionPages(answers);
}

interface Draft {
  id: QuestionId;
  skip?: boolean;
  ghost?: boolean;
}

export function buildSteps(answers: Answers, lang: Lang, flags: FlowFlags): Step[] {
  const position: Draft[] = showsPositionPages(answers) ? [{ id: 'expectedPosition' }, { id: 'position' }] : [];
  const branches: Draft[] = branchHobbies(answers).flatMap((h) => BRANCHES[h]!.map((id) => ({ id })));
  const long = usesLongTail(answers, flags);
  const student = isStudentPath(answers);
  const escaped = student && answers.noSchoolEmail === true;

  const seg0: Draft[] = [{ id: 'isStudent' }, { id: 'name' }, { id: 'gender' }, { id: 'expectedGender' }, { id: 'birthday' }];

  // 09 §2 (pages 18–19): photos, then the dating-area map. 07 JSON puts emailVerify between the email and photos.
  const photosAndArea: Draft[] = [{ id: 'photos' }, { id: 'location' }];

  const seg1: Draft[] = student
    ? [
        { id: 'hobbies' },
        ...branches,
        { id: 'ethnicity' },
        { id: 'expectedEthnicity' },
        { id: 'expectedAgeRange' },
        ...position,
        { id: 'height' },
        { id: 'heightRange' },
        { id: 'schoolEmail' },
        // Escape hatch skips the school-email code (10 §7); verified students get the code page (12 §0.1 loaded there).
        ...(escaped ? [] : [{ id: 'emailVerify' as const }]),
        ...photosAndArea,
      ]
    : [
        { id: 'expectedAgeRange' },
        { id: 'ethnicity' },
        { id: 'expectedEthnicity' },
        ...position,
        { id: 'height' },
        { id: 'heightRange' },
        { id: 'hobbies' },
        ...branches,
        // N1/N2: this slot is counted, then skipped behind a ~0.9s spinner. N3 shows it.
        { id: 'photos', skip: !long },
        // Not observed on N runs (recorded accounts already had an area); a fresh account needs one. 推断.
        ...(long ? [{ id: 'location' as const }] : []),
      ];

  // 09 §1: on the student path the third segment starts after location and ends full on datingHelp, which
  // submits on the first tap. The preferences intro occupies a cell but never showed (09 §20).
  const studentTail: Draft[] = [
    { id: 'preferencesIntro', ghost: true },
    { id: 'relationshipLooking' },
    { id: 'matchingStyle' },
    { id: 'schoolYear' },
    { id: 'hearAbout' },
    { id: 'dealbreakers' },
    { id: 'politics' },
    { id: 'politicsImportance' },
    { id: 'religion' },
    { id: 'religionImportance' },
    { id: 'firstMessage' },
    { id: 'datingHelp' },
  ];

  // 12 §1 N3 (long) / N1 (short), and 10 §7 for the "not a student?" escape. Ends with personal email → code.
  const professionalTail: Draft[] = [
    { id: 'preferencesIntro' },
    ...(long ? [{ id: 'relationshipLooking' as const }, { id: 'matchingStyle' as const }] : []),
    { id: 'occupation' },
    { id: 'firstDateBudget' },
    ...(long || escaped ? [{ id: 'dealbreakers' as const }] : []),
    { id: 'educationLevel' },
    { id: 'educationImportance' },
    ...(long
      ? ([
          { id: 'politics' },
          { id: 'politicsImportance' },
          { id: 'religion' },
          { id: 'religionImportance' },
          { id: 'firstMessage' },
          { id: 'datingHelp' },
        ] as Draft[])
      : []),
    { id: 'personalEmail' },
    { id: 'emailVerify' },
  ];

  const seg2: Draft[] = student && !escaped ? studentTail : professionalTail;

  const steps: Step[] = [];
  [seg0, seg1, seg2].forEach((seg, segment) => {
    const kept = seg.filter((d) => !QUESTIONS[d.id].drop?.[lang]);
    kept.forEach((d, i) => {
      steps.push({
        id: d.id,
        question: QUESTIONS[d.id],
        segment: segment as 0 | 1 | 2,
        cell: i + 1,
        cells: kept.length,
        ...(d.skip ? { skip: true } : {}),
        ...(d.ghost ? { ghost: true } : {}),
      });
    });
  });

  if (flags.skipAnswered) {
    // Keep the step in the list (progress math is unchanged) but mark it so navigation jumps over it.
    return steps.map((s) =>
      !s.ghost && s.question.type !== 'interstitial' && isAnswered(s.question, answers[s.id], { answers, lang })
        ? { ...s, answered: true }
        : s,
    );
  }
  return steps;
}

/** Three fill fractions for the 3-segment bar. */
export function progressOf(step: Step | undefined): [number, number, number] {
  if (!step) return [0, 0, 0];
  const f = step.cell / step.cells;
  return [0, 1, 2].map((s) => (s < step.segment ? 1 : s === step.segment ? f : 0)) as [number, number, number];
}

export const navigable = (s: Step) => !s.skip && !s.ghost && !s.answered;

/** 09 §30 / §6: the last page (datingHelp, all three bars full) shows confetti and submits on the first option tap. */
export function isFinalTap(step: Step, steps: Step[]): boolean {
  const last = [...steps].reverse().find((s) => !s.ghost && !s.skip);
  return last?.id === step.id && (step.question.type === 'multi' || step.question.type === 'single');
}

/** The address the code page verifies: school email unless the user escaped or is not a student. */
export function emailToVerify(answers: Answers): string {
  const school = answers.isStudent === 'yes' && answers.noSchoolEmail !== true;
  const v = school ? answers.schoolEmail : answers.personalEmail;
  return typeof v === 'string' ? v : '';
}

/** Answers that must be cleared when a hobby is dropped (12 §2.4: removing a hobby wipes its branch drafts). */
export function staleBranchKeys(answers: Answers): QuestionId[] {
  const picked = arr(answers.hobbies);
  return Object.entries(BRANCHES)
    .filter(([hobby]) => !picked.includes(hobby))
    .flatMap(([, ids]) => ids)
    .filter((id) => answers[id] !== undefined);
}
