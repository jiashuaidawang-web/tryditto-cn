import { Route } from 'react-router';
import { LandingPage } from './LandingPage';
import { LegalPage, NotFoundPage } from './LegalPage';
import { LoginPage } from './LoginPage';
import { ManifestoPage } from './ManifestoPage';

export function landingRoutes() {
  return [
    <Route key="landing" path="/" element={<LandingPage />} />,
    <Route key="login" path="/login" element={<LoginPage />} />,
    <Route key="manifesto" path="/manifesto" element={<ManifestoPage />} />,
    <Route key="terms" path="/terms" element={<LegalPage kind="terms" />} />,
    <Route key="privacy" path="/privacy" element={<LegalPage kind="privacy" />} />,
    <Route key="cookies" path="/cookies" element={<LegalPage kind="cookies" />} />,
  ];
}

/** Must be the last route. */
export function notFoundRoute() {
  return <Route key="404" path="*" element={<NotFoundPage />} />;
}
