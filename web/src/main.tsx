import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Route, Routes } from 'react-router';
import { MotionConfig } from 'motion/react';
import './index.css';
import { OnboardingPage } from './onboarding/OnboardingPage';
import { landingRoutes, notFoundRoute } from './landing/routes';
import { RequireLogin, appRoutes } from './app/routes';
import { MatchLabRoutes } from './matchlab/MatchLab';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <MotionConfig reducedMotion="user">
      <BrowserRouter>
        <Routes>
          {landingRoutes()}
          <Route
            path="/onboarding"
            element={
              <RequireLogin>
                <OnboardingPage />
              </RequireLogin>
            }
          />
          {appRoutes()}
          {MatchLabRoutes()}
          {notFoundRoute()}
        </Routes>
      </BrowserRouter>
    </MotionConfig>
  </StrictMode>,
);
