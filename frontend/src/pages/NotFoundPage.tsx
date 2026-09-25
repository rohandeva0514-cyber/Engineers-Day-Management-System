import { ButtonLink } from '@/components/Button';
import { PageMessage } from '@/components/ErrorState';

export function NotFoundPage() {
  return (
    <div className="mx-auto max-w-3xl px-4 sm:px-6">
      <PageMessage
        label="404 / No signal"
        title="This page does not exist"
        action={
          <>
            <ButtonLink to="/events">Browse events</ButtonLink>
            <ButtonLink to="/" variant="ghost">
              Home
            </ButtonLink>
          </>
        }
      >
        <p>The address you followed does not lead anywhere on this site.</p>
      </PageMessage>
    </div>
  );
}
