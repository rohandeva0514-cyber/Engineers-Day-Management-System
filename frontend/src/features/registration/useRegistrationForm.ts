import { useCallback, useMemo, useRef, useState } from 'react';
import type { Event, ParticipantDraft, RegistrationReceipt, YearLevel } from '@/domain/types';
import {
  canAddMembers,
  canRemoveMembers,
  duplicateRollNumbers,
  emptyParticipant,
  initialRosterSize,
  isParticipantComplete,
  isRosterSizeValid,
  isSolo,
  isYearEligible,
} from '@/domain/rules';
import { submitRegistration } from '@/services/registrationsApi';
import { ApiError } from '@/services/apiError';
import { createIdempotencyKey } from '@/lib/idempotency';

/**
 * Registration form state and submission.
 *
 * The client-side checks here exist to give immediate feedback and to stop a
 * request that is certain to be refused. They are NOT authoritative: the server
 * re-validates eligibility, team size, capacity and duplicates on every submit, and
 * when the two disagree the server's answer is what the student sees.
 *
 * A concrete example of why that matters: this hook can tell a second-year student
 * that BuildX is first-year only, but only the server knows whether the roll number
 * they typed is *already on record* as a second year. The form guides; the backend
 * decides.
 */

export type SubmitState =
  | { status: 'idle' }
  | { status: 'submitting' }
  | { status: 'error'; error: ApiError }
  | { status: 'success'; receipt: RegistrationReceipt };

export interface RegistrationFormApi {
  roster: ParticipantDraft[];
  teamName: string;
  submitState: SubmitState;

  setTeamName: (value: string) => void;
  updateMember: (index: number, patch: Partial<ParticipantDraft>) => void;
  addMember: () => void;
  removeMember: (index: number) => void;

  canAdd: boolean;
  canRemove: boolean;

  /** Per-field client errors, keyed the same way the backend keys its own. */
  fieldErrors: Record<string, string>;
  /** Form-level blockers shown above the submit button. */
  blockers: string[];
  isSubmittable: boolean;

  submit: () => Promise<RegistrationReceipt | null>;
  reset: () => void;
}

export function useRegistrationForm(event: Event): RegistrationFormApi {
  const [roster, setRoster] = useState<ParticipantDraft[]>(() =>
    Array.from({ length: initialRosterSize(event) }, emptyParticipant),
  );
  const [teamName, setTeamName] = useState('');
  const [submitState, setSubmitState] = useState<SubmitState>({ status: 'idle' });

  /**
   * One idempotency key per *attempt*, held across retries of the same payload.
   *
   * Regenerating on every click would defeat the mechanism entirely: if the server
   * committed and the response was lost, a retry with a fresh key creates a second
   * registration. So the key is tied to the payload — edit the roster and you get a
   * new key, press "try again" unchanged and you get the same one.
   */
  const keyRef = useRef<{ key: string; payload: string }>({ key: '', payload: '' });

  const solo = isSolo(event);

  const updateMember = useCallback((index: number, patch: Partial<ParticipantDraft>) => {
    setRoster((current) =>
      current.map((member, i) => (i === index ? { ...member, ...patch } : member)),
    );
  }, []);

  const addMember = useCallback(() => {
    setRoster((current) =>
      current.length < event.maxTeamSize ? [...current, emptyParticipant()] : current,
    );
  }, [event.maxTeamSize]);

  const removeMember = useCallback(
    (index: number) => {
      setRoster((current) =>
        current.length > event.minTeamSize ? current.filter((_, i) => i !== index) : current,
      );
    },
    [event.minTeamSize],
  );

  const reset = useCallback(() => {
    setSubmitState({ status: 'idle' });
  }, []);

  /* ------------------------------------------------------------ validation */

  const { fieldErrors, blockers } = useMemo(() => {
    const errors: Record<string, string> = {};
    const problems: string[] = [];

    if (!solo && teamName.trim() === '') {
      errors['teamName'] = 'A team name is required for this event.';
    }

    const duplicates = duplicateRollNumbers(roster);
    if (duplicates.length > 0) {
      problems.push(`The same roll number appears more than once: ${duplicates.join(', ')}.`);
    }

    roster.forEach((member, index) => {
      const prefix = `participants[${index}]`;

      // Year is checked against the event's own rules so the student sees the
      // problem where it happens, rather than as a rejection after submitting.
      if (member.yearLevel !== null && !isYearEligible(event, member.yearLevel)) {
        errors[`${prefix}.yearLevel`] = `${event.name} is not open to year ${member.yearLevel}.`;
      }
      // Shape-only email check. The server does the real one.
      if (member.email.trim() !== '' && !member.email.includes('@')) {
        errors[`${prefix}.email`] = 'Enter a valid email address.';
      }
      if (member.rollNo.trim() !== '' && !/^[A-Za-z0-9/_-]+$/.test(member.rollNo.trim())) {
        errors[`${prefix}.rollNo`] =
          'Only letters, digits, hyphen, underscore and slash are allowed.';
      }
    });

    if (!isRosterSizeValid(event, roster.length)) {
      problems.push(
        event.minTeamSize === event.maxTeamSize
          ? `This event requires exactly ${event.minTeamSize} members.`
          : `This event requires ${event.minTeamSize} to ${event.maxTeamSize} members.`,
      );
    }

    const incomplete = roster.filter((member) => !isParticipantComplete(member)).length;
    if (incomplete > 0) {
      problems.push(
        incomplete === 1
          ? 'One participant is missing required details.'
          : `${incomplete} participants are missing required details.`,
      );
    }

    return { fieldErrors: errors, blockers: problems };
  }, [event, roster, teamName, solo]);

  const isSubmittable =
    submitState.status !== 'submitting' &&
    blockers.length === 0 &&
    Object.keys(fieldErrors).length === 0;

  /* ---------------------------------------------------------------- submit */

  const submit = useCallback(async (): Promise<RegistrationReceipt | null> => {
    // Guard against a double-click landing two requests before the first responds.
    if (submitState.status === 'submitting') return null;

    const participants = roster.map((member) => ({
      rollNo: member.rollNo.trim().toUpperCase(),
      fullName: member.fullName.trim(),
      email: member.email.trim(),
      yearLevel: member.yearLevel as YearLevel,
    }));

    const payloadSignature = JSON.stringify({ participants, teamName: teamName.trim() });
    if (keyRef.current.payload !== payloadSignature) {
      keyRef.current = { key: createIdempotencyKey(), payload: payloadSignature };
    }

    setSubmitState({ status: 'submitting' });

    try {
      const receipt = await submitRegistration({
        eventId: event.eventId,
        ...(solo ? {} : { teamName: teamName.trim() }),
        participants,
        idempotencyKey: keyRef.current.key,
      });
      setSubmitState({ status: 'success', receipt });
      return receipt;
    } catch (cause) {
      setSubmitState({
        status: 'error',
        error: cause instanceof ApiError ? cause : new ApiError('UNKNOWN', 'Registration failed.'),
      });
      return null;
    }
  }, [event.eventId, roster, teamName, solo, submitState.status]);

  return {
    roster,
    teamName,
    submitState,
    setTeamName,
    updateMember,
    addMember,
    removeMember,
    canAdd: canAddMembers(event, roster.length),
    canRemove: canRemoveMembers(event, roster.length),
    fieldErrors,
    blockers,
    isSubmittable,
    submit,
    reset,
  };
}
