import { useEffect, type ReactNode } from 'react';
import { Navigate, Route } from 'react-router';
import { MOCK_API, demo, syncOnce } from '../api';
import { useQuerySuffix } from '../i18n/lang';
import { useAuth } from '../landing/auth';
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
 * With the real API every logged-in page needs a session; the local caches are refreshed from the server
 * once per page load. The mock build keeps the old behaviour (no login needed).
 */
export function RequireLogin({ children }: { children: ReactNode }) {
  const token = useAuth((s) => s.token);
  const suffix = useQuerySuffix();
  useEffect(syncOnce, [token]);
  if (!MOCK_API && !token) return <Navigate to={`/login${suffix}`} replace />;
  return <>{children}</>;
}

/**
 * Logged-in pages require a finished questionnaire (Ditto's guard sends unfinished users back to
 * /onboarding, 13 §0). In the mock build `?demo=1` fills in a sample profile instead.
 */
function Onboarded({ children }: { children: ReactNode }) {
  const completed = useOnboarding((s) => s.completed);
  const seed = useIsDemo() && MOCK_API;
  const suffix = useQuerySuffix();
  useEffect(() => {
    if (!completed && seed) demo.seedProfile();
  }, [completed, seed]);
  if (!completed && !seed) return <Navigate to={`/onboarding${suffix}`} replace />;
  if (!completed) return null;
  return <RequireLogin>{children}</RequireLogin>;
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
