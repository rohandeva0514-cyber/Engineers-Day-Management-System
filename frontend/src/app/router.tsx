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
