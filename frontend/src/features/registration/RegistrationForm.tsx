import { useNavigate } from 'react-router-dom';
import type { Event } from '@/domain/types';
import { useRegistrationForm } from './useRegistrationForm';
import { ParticipantFields } from './ParticipantFields';
import { TextField } from '@/components/Field';
import { Button } from '@/components/Button';
import { TechLabel } from '@/components/Panel';
import { ErrorState } from '@/components/ErrorState';
import { identitySession } from '@/services/identitySession';
import { isSolo, requiresExactTeamSize, teamSizeLabel } from '@/domain/rules';

/**
 * The registration form.
 *
 * One component for all seven events. Whether it renders one participant or ten,
 * whether the roster can grow, whether a team name is asked for — all of it is read
 * from the `Event` the API returned. There is no `if (eventId === 'tech-debate')`
 * anywhere in this file, and adding an eighth event would require no change to it.
 */
export function RegistrationForm({ event }: { event: Event }) {
  const navigate = useNavigate();
  const form = useRegistrationForm(event);
  const solo = isSolo(event);
  const fixedSize = requiresExactTeamSize(event);

  const serverError = form.submitState.status === 'error' ? form.submitState.error : null;
  const serverFieldErrors = serverError?.fieldErrors ?? {};
  const isSubmitting = form.submitState.status === 'submitting';

  async function handleSubmit(formEvent: React.FormEvent) {
    formEvent.preventDefault();

    const receipt = await form.submit();
    if (receipt === null) return;

    // Remember who registered on this device so /my-registrations can populate
    // itself. This is a convenience, not a credential — see identitySession.
    const captain = receipt.registrations[0]?.participant;
    if (captain !== undefined) identitySession.remember(captain);

    navigate('/registration/success', { state: { receipt }, replace: true });
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-6">
      {/* Server refusals lead the form. A student who submitted and was rejected
          needs the reason at the top, not somewhere below a ten-member roster. */}
      {serverError !== null && (
        <ErrorState
          error={serverError}
          {...(serverError.isTransient ? { onRetry: () => void form.submit() } : {})}
          {...(serverError.isEventStateProblem
            ? { backTo: { to: `/events/${event.eventId}`, label: 'Back to event' } }
            : {})}
        />
      )}

      {!solo && (
        <div className="border border-line bg-panel px-4 py-4 sm:px-5 sm:py-5">
          <TextField
            label="Team name"
            required
            maxLength={120}
            value={form.teamName}
            onChange={(e) => form.setTeamName(e.target.value)}
            error={form.fieldErrors['teamName']}
            hint="Must be unique within this event"
            placeholder="e.g. Kernel Panic"
            wrapperClassName="max-w-md"
          />
        </div>
      )}

      <div className="space-y-4">
        {form.roster.map((member, index) => (
          <ParticipantFields
            key={index}
            event={event}
            index={index}
            value={member}
            solo={solo}
            isCaptain={index === 0}
            onChange={(patch) => form.updateMember(index, patch)}
            {...(form.canRemove && index > 0
              ? { onRemove: () => form.removeMember(index) }
              : {})}
            fieldErrors={form.fieldErrors}
            serverFieldErrors={serverFieldErrors}
          />
        ))}
      </div>

      {/* Team builder controls. Absent entirely for solo and fixed-size events,
          because there is nothing to add or remove. */}
      {form.canAdd && (
        <div className="flex flex-wrap items-center gap-4">
          <Button type="button" variant="secondary" onClick={form.addMember}>
            + Add member
          </Button>
          <p className="label-tech" data-tabular>
            {form.roster.length} of {event.maxTeamSize} · {teamSizeLabel(event).toLowerCase()}
          </p>
        </div>
      )}

      {fixedSize && (
        <p className="label-tech" data-tabular>
          Roster {form.roster.length} / {event.minTeamSize} — all {event.minTeamSize} members
          required
        </p>
      )}

      {/* Why the button is disabled, stated plainly. A disabled control with no
          explanation is the most common dead end in a registration form. */}
      {form.blockers.length > 0 && (
        <div className="border-l-2 border-line-bright bg-panel/60 py-3 pl-4">
          <TechLabel className="mb-2">Before you can submit</TechLabel>
          <ul className="space-y-1 text-sm text-muted">
            {form.blockers.map((blocker) => (
              <li key={blocker}>{blocker}</li>
            ))}
          </ul>
        </div>
      )}

      <div className="flex flex-col gap-4 border-t border-line pt-6 sm:flex-row sm:items-center">
        <Button type="submit" size="lg" disabled={!form.isSubmittable}>
          {isSubmitting ? 'Submitting…' : `Register for ${event.name}`}
        </Button>

        <p className="text-xs leading-relaxed text-faint sm:max-w-sm">
          {isSubmitting
            ? 'Do not close this page. Your registration is being confirmed by the server.'
            : 'Eligibility, team size and capacity are verified by the server. Your place is confirmed only when it accepts.'}
        </p>
      </div>
    </form>
  );
}
