import { Route } from 'react-router';
import { LandingPage } from './LandingPage';
import { LoginPage } from './LoginPage';

export function landingRoutes() {
  return [<Route key="landing" path="/" element={<LandingPage />} />, <Route key="login" path="/login" element={<LoginPage />} />];
}
