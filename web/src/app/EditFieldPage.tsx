import { useEffect, useState } from 'react';
import { AnimatePresence } from 'motion/react';
import { Navigate, useParams } from 'react-router';
import { getProfile, updateProfileField } from '../api';
import { pick, t, useLang, useQuerySuffix } from '../i18n/lang';
import { branchHobbies, staleBranchKeys } from '../onboarding/flow';
import { BRANCHES, QUESTIONS, type QuestionId } from '../onboarding/questions';
import { AGE_DEFAULT, HEIGHT_RANGE_DEFAULT, PageFrame, QuestionPage } from '../onboarding/QuestionPage';
import type { Answers, Question, Step } from '../onboarding/types';
import { resolveField } from './profileFields';
import { AppScreen, TopBar, useGo, useLive } from './ui';

const TITLE = t('Edit Profile', '编辑资料');

function withDefault(q: Question, v: unknown): unknown {
  if (v !== undefined) return v;
  if (q.type === 'ageRange') return AGE_DEFAULT;
  if (q.type === 'heightRange') return HEIGHT_RANGE_DEFAULT;
  if (q.type === 'longText') return '';
  return v;
}

/**
 * `/onboarding/edit/:field` (05/06 screenshots, 13 §3): the onboarding page in edit mode — back + `Edit Profile`,
 * no progress, `Done` bottom-right (text bars and birthday keep the round Send). Back returns to /edit-profile,
 * or /deep-info for its four rows.
 */
export function EditFieldPage() {
  const { field } = useParams();
  const lang = useLang();
  const go = useGo();
  const suffix = useQuerySuffix();
  const profile = useLive(getProfile);
  const [draft, setDraft] = useState<Answers | null>(null);
  const [index, setIndex] = useState(0);
  const resolved = resolveField(field);

  useEffect(() => {
    if (profile && !draft) setDraft({ ...profile.answers });
  }, [profile, draft]);

  if (!resolved || resolved.row.locked) return <Navigate to={`/edit-profile${suffix}`} replace />;
  const { row, back } = resolved;

  const answers = draft ?? profile?.answers ?? {};
  const queue: QuestionId[] = row.q === 'hobbies' ? ['hobbies', ...branchHobbies(answers).flatMap((h) => BRANCHES[h]!)] : [row.q];
  const id = queue[Math.min(index, queue.length - 1)]!;
  const question = QUESTIONS[id] as Question;
  const step: Step = { id, question, segment: 0, cell: 1, cells: 1 };

  const save = async () => {
    if (!draft) return;
    for (const qid of queue) await updateProfileField(qid, withDefault(QUESTIONS[qid] as Question, draft[qid]));
    if (row.q === 'hobbies') for (const stale of staleBranchKeys(draft)) await updateProfileField(stale, undefined);
    go(back);
  };

  const onNext = () => {
    if (index < queue.length - 1) setIndex(index + 1);
    else void save();
  };

  return (
    <AppScreen>
      <TopBar variant="edit" title={pick(TITLE, lang)} onBack={() => (index > 0 ? setIndex(index - 1) : go(back))} />
      <AnimatePresence mode="wait">
        {draft && (
          <PageFrame key={id}>
            <QuestionPage
              step={step}
              lang={lang}
              answers={draft}
              mode="edit"
              editNext={index < queue.length - 1}
              onSet={(qid, v) => setDraft((d) => ({ ...(d ?? {}), [qid]: v }))}
              onNext={onNext}
            />
          </PageFrame>
        )}
      </AnimatePresence>
    </AppScreen>
  );
}
