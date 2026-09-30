package in.mittechkernel.registration.arena.service;

import in.mittechkernel.registration.arena.entity.ArenaControl;
import in.mittechkernel.registration.arena.entity.ArenaStatus;
import in.mittechkernel.registration.arena.repository.ArenaControlRepository;
import in.mittechkernel.registration.exception.ApiErrorCode;
import in.mittechkernel.registration.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/**
 * The Debugging Arena's lifecycle switch.
 *
 * <p>One row, read on every arena request and written only by an authenticated
 * admin. It is the thing that makes the arena <em>not</em> reachable merely because
 * the frontend for it exists: a deployed page and an open competition are separate
 * facts, and only this row decides the second one.
 *
 * <h2>Read from the database every time</h2>
 *
 * <p>Not cached, for the same reason {@code RegistrationControlService} is not: an
 * organiser pressing Stop expects it to take effect everywhere immediately, and a
 * cache would mean one instance still admitting participants after the panel said
 * it had stopped. The row is a single indexed primary-key lookup.
 *
 * <h2>Which event</h2>
 *
 * <p>The arena's event id is configuration, not a constant in Java. V12 seeds a row
 * for every event with {@code requires_access_code}, and this property picks which
 * of them this service operates. Nothing here names Debugging, which is the same
 * discipline V9 established when it made terminal check-in a column rather than a
 * check for {@code "debugging"} in code.
 */
@Service
public class ArenaControlService {

    private static final Logger log = LoggerFactory.getLogger(ArenaControlService.class);

    private final ArenaControlRepository controlRepository;
    private final String eventId;

    public ArenaControlService(ArenaControlRepository controlRepository,
                               @Value("${app.arena.event-id:debugging}") String eventId) {
        this.controlRepository = controlRepository;
        this.eventId = eventId;
    }

    /**
     * The arena row.
     *
     * <p>Its absence is a deployment fault, not a runtime condition: V12 seeds it and
     * it must never be deleted. Failing loudly here is right - an arena that silently
     * reported OFFLINE because its row had vanished would look exactly like an arena
     * an organiser had not started yet, and they would wait for each other.
     */
    @Transactional(readOnly = true)
    public ArenaControl current() {
        return controlRepository.findById(eventId)
                .orElseThrow(() -> new IllegalStateException(
                        "arena_control row is missing for event '" + eventId
                                + "'; V12 seeds it and it must never be deleted"));
    }

    @Transactional(readOnly = true)
    public ArenaStatus status() {
        return current().getStatus();
    }

    /**
     * Move the arena to a new status.
     *
     * <p>Idempotent: setting the status it already holds succeeds and changes
     * nothing, so a double-click or a retried request is not an error.
     *
     * <p>Every other illegal move is refused with the transition named, because the
     * one an organiser will actually attempt - reopening an ended arena in a single
     * step - deserves to be told why rather than silently ignored.
     */
    @Transactional
    public ArenaControl transitionTo(ArenaStatus target, String actor) {
        ArenaControl control = current();
        ArenaStatus from = control.getStatus();

        if (!from.canTransitionTo(target)) {
            throw new ApiException(ApiErrorCode.ARENA_TRANSITION_INVALID,
                    "The arena cannot go from " + from + " to " + target + "."
                            + (from == ArenaStatus.ENDED && target == ArenaStatus.ACTIVE
                            ? " Reopen it to OFFLINE first."
                            : ""),
                    Map.of("from", from.name(), "to", target.name()));
        }

        if (from == target) {
            return control;
        }

        control.transitionTo(target, actor, Instant.now());
        ArenaControl saved = controlRepository.save(control);

        // Worth a log line at INFO: this is the single most consequential control in
        // the event, and "when did the arena open" is asked afterwards every time.
        log.info("Arena '{}' moved {} -> {} by {}", eventId, from, target, actor);
        return saved;
    }

    /**
     * Refuse the request unless the arena is running.
     *
     * <p>Called first in every participant-facing arena operation, before the access
     * code is even looked at. Two reasons, and the second is the one that matters:
     * an organiser who has not opened the arena has said nobody may be in it, and
     * checking the public switch before touching a code means a closed arena does no
     * code lookups at all, so it cannot be used to probe for valid codes while shut.
     *
     * <p>OFFLINE and ENDED are told apart on purpose. "Wait, it has not started" and
     * "it is over" are different things for a student to read.
     */
    @Transactional(readOnly = true)
    public void requireActive() {
        ArenaStatus status = status();
        if (status == ArenaStatus.ACTIVE) {
            return;
        }
        if (status == ArenaStatus.ENDED) {
            throw new ApiException(ApiErrorCode.ARENA_ENDED,
                    "Mission control has ended the arena.",
                    Map.of("arenaStatus", status.name()));
        }
        throw new ApiException(ApiErrorCode.ARENA_OFFLINE,
                "The arena has not started yet. Please wait for the event administrator.",
                Map.of("arenaStatus", status.name()));
    }

    /** Parse an admin-supplied status, refusing anything outside the enum. */
    public static ArenaStatus requireKnownStatus(String raw) {
        try {
            return ArenaStatus.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException cause) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED,
                    "Unknown arena status '" + raw + "'.",
                    Map.of("allowed", "OFFLINE, ACTIVE, ENDED"));
        }
    }
}
