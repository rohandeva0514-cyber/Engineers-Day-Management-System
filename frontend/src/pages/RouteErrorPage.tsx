import { isRouteErrorResponse, useRouteError } from 'react-router-dom';
import { ButtonLink } from '@/components/Button';
import { PageMessage } from '@/components/ErrorState';
import { SiteHeader } from '@/app/SiteHeader';

/**
 * The last line of defence: a rendering crash or an unmatched route boundary.
 *
 * Deliberately shows nothing technical. A student seeing a component stack trace
 * learns nothing and loses confidence in a site they are about to hand their
 * details to. The detail goes to the console, where it is useful.
 */
export function RouteErrorPage() {
  const error = useRouteError();

  if (import.meta.env.DEV) {
    // eslint-disable-next-line no-console
    console.error('Route error:', error);
  }

  const is404 = isRouteErrorResponse(error) && error.status === 404;

  return (
    <div className="flex min-h-dvh flex-col">
      <SiteHeader />
      <main className="mx-auto w-full max-w-3xl flex-1 px-4 sm:px-6">
        <PageMessage
          label={is404 ? '404 / No signal' : 'System fault'}
          title={is404 ? 'This page does not exist' : 'Something went wrong'}
          action={
            <>
              <ButtonLink to="/events">Browse events</ButtonLink>
              <ButtonLink to="/" variant="ghost">
                Home
              </ButtonLink>
            </>
          }
        >
          <p>
            {is404
              ? 'The address you followed does not lead anywhere on this site.'
              : 'The page could not be displayed. Your registrations are unaffected.'}
          </p>
        </PageMessage>
      </main>
    </div>
  );
}
