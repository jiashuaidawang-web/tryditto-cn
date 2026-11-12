import { useEffect, type ReactNode } from 'react';
import { Navigate, Route } from 'react-router';
import { demo } from '../api';
import { useQuerySuffix } from '../i18n/lang';
import { useOnboarding } from '../onboarding/store';
import { AccountPage } from './AccountPage';
import { BlocklistPage } from './BlocklistPage';
import { DeepInfoPage } from './DeepInfoPage';
import { DemoPage } from './DemoPanel';
import { EditFieldPage } from './EditFieldPage';
import { EditProfilePage } from './EditProfilePage';
import { HomePage } from './HomePage';
import { DatePage } from './match/DatePage';
import { FeedbackPage } from './match/FeedbackPage';
import { RevealPage } from './match/RevealPage';
import { useIsDemo } from './ui';
import { WherePage } from './WherePage';
import { YouPage } from './YouPage';
import './app.css';

/**
 * Logged-in pages require a finished questionnaire (Ditto's guard sends unfinished users back to
 * /onboarding, 13 §0). With `?demo=1` a sample profile is filled in instead.
 */
function Onboarded({ children }: { children: ReactNode }) {
  const completed = useOnboarding((s) => s.completed);
  const isDemo = useIsDemo();
  const suffix = useQuerySuffix();
  useEffect(() => {
    if (!completed && isDemo) demo.seedProfile();
  }, [completed, isDemo]);
  if (!completed && !isDemo) return <Navigate to={`/onboarding${suffix}`} replace />;
  if (!completed) return null;
  return <>{children}</>;
}

const guarded: [string, ReactNode][] = [
  ['/home', <HomePage />],
  ['/where', <WherePage />],
  ['/you', <YouPage />],
  ['/edit-profile', <EditProfilePage />],
  ['/onboarding/edit/:field', <EditFieldPage />],
  ['/deep-info', <DeepInfoPage />],
  ['/blocklist', <BlocklistPage />],
  ['/account', <AccountPage />],
  ['/reveal/:matchId', <RevealPage />],
  ['/match/:matchId/date', <DatePage />],
  ['/match/:matchId/feedback', <FeedbackPage />],
];

/** Logged-in routes (after onboarding). Called as a function inside <Routes>. */
export function appRoutes() {
  return (
    <>
      {guarded.map(([path, el]) => (
        <Route key={path} path={path} element={<Onboarded>{el}</Onboarded>} />
      ))}
      <Route path="/demo" element={<DemoPage />} />
    </>
  );
}
