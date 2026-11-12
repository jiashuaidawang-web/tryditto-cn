import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Route, Routes } from 'react-router';
import { MotionConfig } from 'motion/react';
import './index.css';
import { OnboardingPage } from './onboarding/OnboardingPage';
import { landingRoutes } from './landing/routes';
import { appRoutes } from './app/routes';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <MotionConfig reducedMotion="user">
      <BrowserRouter>
        <Routes>
          {landingRoutes()}
          <Route path="/onboarding" element={<OnboardingPage />} />
          {appRoutes()}
        </Routes>
      </BrowserRouter>
    </MotionConfig>
  </StrictMode>,
);
