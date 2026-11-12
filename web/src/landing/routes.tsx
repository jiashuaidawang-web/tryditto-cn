import { Route } from 'react-router';
import { Home } from '../pages/Home';

/** Public routes: marketing landing page and login. Called as a function inside <Routes>. */
export function landingRoutes() {
  return (
    <>
      <Route path="/" element={<Home />} />
    </>
  );
}
