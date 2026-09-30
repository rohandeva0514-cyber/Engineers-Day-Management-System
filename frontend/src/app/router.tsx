import { createBrowserRouter } from 'react-router-dom';
import { RootLayout } from './RootLayout';
import { ExperienceRoute } from './ExperienceRoute';
import { EventsPage } from '@/pages/EventsPage';
import { EventDetailPage } from '@/pages/EventDetailPage';
import { RegisterPage } from '@/pages/RegisterPage';
import { RegistrationSuccessPage } from '@/pages/RegistrationSuccessPage';
import { MyRegistrationsPage } from '@/pages/MyRegistrationsPage';
import { NotFoundPage } from '@/pages/NotFoundPage';
import { VerifyPage } from '@/pages/VerifyPage';
import { RouteErrorPage } from '@/pages/RouteErrorPage';
import { AdminPage } from '@/admin/AdminPage';

/**
 * Route table.
 *
 * The front door sits OUTSIDE the practical shell. The experience route owns the
 * whole viewport — boot sequence, hero, and the scroll campaign that follows —
 * and carries its own HUD navigation rather than the conventional site header.
 *
 * Everything below `/` is the practical platform and is fully functional without
 * the experience layer. The dependency arrow runs one way: the experience route
 * imports from `boot/` and `sections/`, and nothing in the practical routes
 * imports from either.
 */
export const router = createBrowserRouter([
  { path: '/', element: <ExperienceRoute />, errorElement: <RouteErrorPage /> },

  // The operations panel. Outside the public shell deliberately: it carries no
  // site navigation and is not linked from anywhere a student sees. That is
  // NOT what protects it — Spring Security refusing /api/admin/** is. Hiding a
  // route only hides the buttons.
  { path: '/admin', element: <AdminPage />, errorElement: <RouteErrorPage /> },

  // The Debugging Arena. Outside the practical shell AND outside the experience
  // route: it owns the viewport, carries no site chrome, and must never inherit
  // Lenis — smooth-scroll hijacking fights a code editor.
  //
  // One route, no sub-paths. The server owns which phase a participant is in, and
  // a URL is a client-supplied claim about phase.
  //
  // Code-split via the router's own `lazy`, not `React.lazy`: the router awaits
  // the module before it commits the navigation, so there is no Suspense boundary
  // and no flash of an empty frame. This matters more than it sounds — the arena
  // will carry a code editor and five syntax grammars, and registration is the
  // path every student uses. None of that belongs in the bundle they download to
  // sign up for Chess.
  {
    path: '/arena',
    lazy: async () => ({ Component: (await import('@/arena/ArenaRoute')).ArenaRoute }),
    errorElement: <RouteErrorPage />,
  },
  {
    element: <RootLayout />,
    errorElement: <RouteErrorPage />,
    children: [
      { path: 'events', element: <EventsPage /> },
      { path: 'events/:eventId', element: <EventDetailPage /> },
      { path: 'register/:eventId', element: <RegisterPage /> },
      { path: 'registration/success', element: <RegistrationSuccessPage /> },
      { path: 'my-registrations', element: <MyRegistrationsPage /> },
      // Event-day check-in. Inside the practical shell, not the experience layer:
      // it is used standing at a terminal, not browsed.
      { path: 'verify', element: <VerifyPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
