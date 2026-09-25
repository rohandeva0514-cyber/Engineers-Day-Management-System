import { createBrowserRouter } from 'react-router-dom';
import { RootLayout } from './RootLayout';
import { ExperienceRoute } from './ExperienceRoute';
import { EventsPage } from '@/pages/EventsPage';
import { EventDetailPage } from '@/pages/EventDetailPage';
import { RegisterPage } from '@/pages/RegisterPage';
import { RegistrationSuccessPage } from '@/pages/RegistrationSuccessPage';
import { MyRegistrationsPage } from '@/pages/MyRegistrationsPage';
import { NotFoundPage } from '@/pages/NotFoundPage';
import { RouteErrorPage } from '@/pages/RouteErrorPage';

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
  {
    element: <RootLayout />,
    errorElement: <RouteErrorPage />,
    children: [
      { path: 'events', element: <EventsPage /> },
      { path: 'events/:eventId', element: <EventDetailPage /> },
      { path: 'register/:eventId', element: <RegisterPage /> },
      { path: 'registration/success', element: <RegistrationSuccessPage /> },
      { path: 'my-registrations', element: <MyRegistrationsPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
