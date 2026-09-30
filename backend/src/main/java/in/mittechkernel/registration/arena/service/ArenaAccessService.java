package in.mittechkernel.registration.arena.service;

import in.mittechkernel.registration.arena.dto.ArenaDtos.AccessResponse;
import in.mittechkernel.registration.arena.dto.ArenaDtos;
import in.mittechkernel.registration.arena.dto.ArenaDtos.AttemptView;
import in.mittechkernel.registration.arena.dto.ArenaDtos.ParticipantIdentity;
import in.mittechkernel.registration.arena.entity.ArenaAttempt;
import in.mittechkernel.registration.arena.repository.ArenaAttemptRepository;
import in.mittechkernel.registration.arena.service.ArenaSessionService.IssuedToken;
import in.mittechkernel.registration.entity.Registration;
import in.mittechkernel.registration.service.AccessCodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Check-in: an access code becomes an arena session.
 *
 * <p>This is the only way into the arena, and the only place an attempt row is
 * created. It owns the order of the checks and nothing else - the code lookup
 * belongs to {@link AccessCodeService}, the switch to {@link ArenaControlService},
 * the token to {@link ArenaSessionService}, and what a started mission means to
 * {@link ArenaAttemptService}.
 *
 * <h2>The order of the checks is deliberate</h2>
 *
 * <ol>
 *   <li><b>Is the arena open?</b> First, before the code is looked at. The switch is
 *       public information, so refusing here reveals nothing - and it means a closed
 *       arena performs no code lookups at all, so it cannot be used to probe for
 *       valid codes while shut.</li>
 *   <li><b>Does the code resolve?</b> Reusing {@code AccessCodeService.resolve}, so
 *       the arena and the marshal desk cannot drift apart about what a valid code
 *       is.</li>
 *   <li><b>Is it a code for <em>this</em> arena?</b> A second terminal-run event
 *       later would issue its own codes, and one of them must not open Debugging.</li>
 *   <li><b>Attempt, then session.</b> Get-or-create, then bind.</li>
 * </ol>
 *
 * <p>Every refusal in steps 2 and 3 is the same message. Distinguishing "no such
 * code" from "that code is for another event" tells someone probing the endpoint
 * when they have found something real.
 */
@Service
public class ArenaAccessService {

    private static final Logger log = LoggerFactory.getLogger(ArenaAccessService.class);

    private final AccessCodeService accessCodes;
    private final ArenaControlService control;
    private final ArenaSessionService sessions;
    private final ArenaAttemptRepository attemptRepository;
    private final String eventId;

    public ArenaAccessService(AccessCodeService accessCodes,
                              ArenaControlService control,
                              ArenaSessionService sessions,
                              ArenaAttemptRepository attemptRepository,
                              @Value("${app.arena.event-id:debugging}") String eventId) {
        this.accessCodes = accessCodes;
        this.control = control;
        this.sessions = sessions;
        this.attemptRepository = attemptRepository;
        this.eventId = eventId;
    }

    /**
     * Exchange an access code for a session.
     *
     * <p>Safe to call repeatedly. A student who refreshes, whose browser crashes, or
     * who moves to another machine types their code again and lands exactly where
     * they left off - same attempt, same clock, same language. What changes is the
     * token: the previous one stops working, which is what stops two people sharing
     * one code from working in parallel.
     */
    @Transactional
    public AccessResponse checkIn(String rawCode) {
        control.requireActive();

        Registration registration = accessCodes.resolve(rawCode);

        // A code issued by a different terminal-run event is not a key to this one.
        // Same refusal as an unknown code - see the class note.
        if (!registration.getEvent().getId().equals(eventId)) {
            throw AccessCodeService.invalid();
        }

        Instant now = Instant.now();
        ArenaAttempt attempt = findOrCreate(registration);

        // The session outlives the mission by a short grace period once running, so
        // the request carrying a final submission at 00:03 is judged on its merits
        // rather than refused for having no session.
        Instant sessionExpiry = attempt.getExpiresAt() == null
                ? sessions.preMissionExpiry(now)
                : sessions.missionExpiry(attempt.getExpiresAt());

        IssuedToken token = sessions.mint();
        int takeoversBefore = attempt.getSessionTakeovers();
        attempt.bindSession(token.hash(), now, sessionExpiry);
        attemptRepository.save(attempt);

        if (attempt.getSessionTakeovers() > takeoversBefore) {
            // Not an error and not refused - a crashed browser produces one of these.
            // Logged because a student whose count climbs is worth an organiser
            // noticing, and the admin roster surfaces the same counter.
            log.info("Arena session taken over for attempt {} (takeovers now {})",
                    attempt.getId(), attempt.getSessionTakeovers());
        }

        return new AccessResponse(
                token.rawToken(),
                sessionExpiry,
                now,
                ParticipantIdentity.from(registration),
                AttemptView.from(attempt, now),
                ArenaDtos.languages());
    }

    /**
     * The attempt for this registration, creating it on first check-in.
     *
     * <p>The race: two devices exchange the same code at the same instant, both find
     * no attempt, and both insert. {@code UNIQUE (registration_id)} refuses the
     * second, and the catch re-reads the row the winner wrote. Checking first and
     * then inserting cannot be made correct here - the window between the two
     * statements is exactly where the second attempt would be created.
     */
    private ArenaAttempt findOrCreate(Registration registration) {
        return attemptRepository.findByRegistrationId(registration.getId())
                .orElseGet(() -> {
                    try {
                        return attemptRepository.saveAndFlush(
                                new ArenaAttempt(registration, registration.getParticipant()));
                    } catch (DataIntegrityViolationException lostTheRace) {
                        return attemptRepository.findByRegistrationId(registration.getId())
                                .orElseThrow(() -> lostTheRace);
                    }
                });
    }
}
