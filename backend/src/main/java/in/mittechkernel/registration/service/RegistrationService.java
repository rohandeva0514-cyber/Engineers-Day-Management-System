package in.mittechkernel.registration.service;

import in.mittechkernel.registration.dto.ParticipantRegistrationsResponse;
import in.mittechkernel.registration.dto.RegistrationRequest;
import in.mittechkernel.registration.dto.RegistrationResponse;
import in.mittechkernel.registration.entity.Event;
import in.mittechkernel.registration.entity.Participant;
import in.mittechkernel.registration.entity.Registration;
import in.mittechkernel.registration.entity.Team;
import in.mittechkernel.registration.exception.ApiErrorCode;
import in.mittechkernel.registration.exception.ApiException;
import in.mittechkernel.registration.repository.EventRepository;
import in.mittechkernel.registration.repository.ParticipantRepository;
import in.mittechkernel.registration.repository.RegistrationRepository;
import in.mittechkernel.registration.repository.TeamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The one place a registration is created.
 *
 * <p>Everything happens in a single transaction: resolving participants, validating them
 * against the event's rules, claiming seats, creating the team and writing the registration
 * rows. Any failure rolls back the whole thing, including the seats - so there is no state in
 * which a seat is consumed by a registration that does not exist.
 *
 * <p>The frontend is not trusted for any of it. The year used for eligibility comes from the
 * stored participant record, the team size comes from the event row, the clock is the
 * server's, and capacity is claimed by a conditional UPDATE that cannot be raced.
 */
@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final EventService eventService;
    private final EventRepository eventRepository;
    private final ParticipantService participantService;
    private final ParticipantRepository participantRepository;
    private final TeamRepository teamRepository;
    private final RegistrationRepository registrationRepository;
    private final RegistrationRules rules;

    public RegistrationService(EventService eventService,
                               EventRepository eventRepository,
                               ParticipantService participantService,
                               ParticipantRepository participantRepository,
                               TeamRepository teamRepository,
                               RegistrationRepository registrationRepository,
                               RegistrationRules rules) {
        this.eventService = eventService;
        this.eventRepository = eventRepository;
        this.participantService = participantService;
        this.participantRepository = participantRepository;
        this.teamRepository = teamRepository;
        this.registrationRepository = registrationRepository;
        this.rules = rules;
    }

    /**
     * @param response the registration, new or replayed
     * @param created  false when an idempotency key replayed an earlier success, so the
     *                 controller can answer 200 instead of claiming it created something
     */
    public record RegistrationOutcome(RegistrationResponse response, boolean created) {
    }

    @Transactional
    public RegistrationOutcome register(RegistrationRequest request) {
        String eventId = request.normalisedEventId();
        String idempotencyKey = request.normalisedIdempotencyKey();

        Event event = eventService.requireEvent(eventId);

        // 0. A retried request that already succeeded returns the original registration.
        //    Checked first so a replay cannot be refused by a rule that has since changed
        //    (the event closing, or the last seat going).
        if (idempotencyKey != null) {
            Optional<RegistrationResponse> replay = findReplay(event, idempotencyKey);
            if (replay.isPresent()) {
                log.info("Replayed registration for event={} key={}", eventId, idempotencyKey);
                return new RegistrationOutcome(replay.get(), false);
            }
        }

        // 1. Is the event accepting anyone at all? Cheapest check, clearest refusal.
        rules.validateRegistrationOpen(event);

        // 2. Is the roster a legal shape for this event? Pure, no database work.
        String teamName = request.normalisedTeamName();
        rules.validateRosterShape(event, teamName, request.participants());

        // 3. Resolve roll numbers to real records, creating first-timers.
        List<Participant> roster = participantService.resolveAll(request.participants());

        // 4. Eligibility, from the stored year rather than the submitted one.
        rules.validateEligibility(event, roster);

        // 5. Nobody on the roster may already hold a registration for this event.
        //    The UNIQUE constraint is the real guard; this is here to give a better message.
        rejectAlreadyRegistered(event, roster);

        // 6. Team name uniqueness, same arrangement: index is authoritative, this is courtesy.
        rejectTakenTeamName(event, teamName);

        // 7. Claim the seats. This is the only step that can lose a race, and it loses it
        //    cleanly: the UPDATE either takes the seats or reports that it could not.
        int seatsRequired = event.seatsRequiredFor(roster.size());
        claimSeatsOrFail(event, seatsRequired);

        // 8. The seat claim cleared the persistence context, so reload what we still need.
        Event current = eventRepository.findById(eventId).orElseThrow();
        List<Participant> managedRoster = reattach(roster);

        // 9. Write the team and one registration per person.
        Team team = current.isSolo()
                ? null
                : teamRepository.saveAndFlush(new Team(current, teamName, managedRoster));

        List<Registration> registrations = persistRegistrations(
                current, managedRoster, team, idempotencyKey);

        log.info("Registered {} participant(s) for event={} team={} seatsTaken={}/{}",
                registrations.size(), eventId,
                team == null ? "-" : team.getName(),
                current.getSeatsTaken(),
                current.getCapacity() == null ? "unlimited" : current.getCapacity());

        return new RegistrationOutcome(
                RegistrationResponse.of(registrations, team, current.seatsRemaining()), true);
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public ParticipantRegistrationsResponse getRegistrationsByParticipantId(Long participantId) {
        Participant participant = participantService.requireById(participantId);
        return ParticipantRegistrationsResponse.of(
                participant, registrationRepository.findAllByParticipantId(participantId));
    }

    @Transactional(readOnly = true)
    public ParticipantRegistrationsResponse getRegistrationsByRollNo(String rollNo) {
        Participant participant = participantService.requireByRollNo(rollNo);
        return ParticipantRegistrationsResponse.of(
                participant, registrationRepository.findAllByParticipantId(participant.getId()));
    }

    // ------------------------------------------------------------------ steps

    /**
     * The capacity mechanism, in one call.
     *
     * <p>A zero row count means the predicate {@code seats_taken + n <= capacity} was false
     * at the moment the row was locked. That is authoritative: no amount of concurrency can
     * produce a false negative here, and the {@code ck_event_seats_taken} CHECK constraint
     * stands behind it in case a future write path forgets to use this method.
     */
    private void claimSeatsOrFail(Event event, int seatsRequired) {
        int claimed = eventRepository.tryClaimSeats(event.getId(), seatsRequired);
        if (claimed == 0) {
            Event current = eventRepository.findById(event.getId()).orElseThrow();
            log.info("Seat claim refused for event={} wanted={} taken={}/{}",
                    event.getId(), seatsRequired, current.getSeatsTaken(), current.getCapacity());
            throw ApiException.capacityFull(
                    event.getId(), current.getCapacity(), current.getSeatsTaken());
        }
    }

    private void rejectAlreadyRegistered(Event event, List<Participant> roster) {
        List<Long> ids = roster.stream().map(Participant::getId).toList();
        List<String> already = registrationRepository.findAlreadyRegisteredRollNos(event.getId(), ids);
        if (!already.isEmpty()) {
            throw ApiException.duplicateRegistration(event.getId(), already);
        }
    }

    private void rejectTakenTeamName(Event event, String teamName) {
        if (teamName != null
                && teamRepository.existsByEventAndNameIgnoreCase(event.getId(), teamName)) {
            throw new ApiException(ApiErrorCode.TEAM_NAME_TAKEN,
                    "Another team in " + event.getName() + " is already called \"" + teamName + "\".",
                    Map.of("eventId", event.getId(), "teamName", teamName));
        }
    }

    private List<Registration> persistRegistrations(Event event, List<Participant> roster,
                                                    Team team, String idempotencyKey) {
        List<Registration> registrations = new ArrayList<>(roster.size());
        for (int i = 0; i < roster.size(); i++) {
            // The key goes on the captain's row only: the partial unique index is per event,
            // so ten rows carrying the same key would collide with each other.
            String key = (i == 0) ? idempotencyKey : null;
            registrations.add(new Registration(event, roster.get(i), team, key));
        }
        return registrationRepository.saveAllAndFlush(registrations);
    }

    /**
     * Re-read roster members as managed entities, preserving order.
     *
     * <p>{@code tryClaimSeats} clears the persistence context - deliberately, so nothing can
     * hold a stale seat count - which detaches everything loaded before it. Order matters
     * because entry 0 becomes the team captain, and {@code findAllById} does not promise any.
     */
    private List<Participant> reattach(List<Participant> detached) {
        List<Long> ids = detached.stream().map(Participant::getId).toList();
        Map<Long, Participant> byId = participantRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Participant::getId, Function.identity()));
        return ids.stream().map(byId::get).toList();
    }

    /** Rebuilds the original response for a replayed idempotency key. */
    private Optional<RegistrationResponse> findReplay(Event event, String idempotencyKey) {
        return registrationRepository
                .findByEventIdAndIdempotencyKey(event.getId(), idempotencyKey)
                .map(anchor -> {
                    Team team = anchor.getTeam();
                    List<Registration> group = (team == null)
                            ? List.of(anchor)
                            : registrationRepository.findAllByTeamId(team.getId());
                    return RegistrationResponse.of(group, team, event.seatsRemaining());
                });
    }
}
