package in.mittechkernel.registration.arena.dto.admin;

import in.mittechkernel.registration.arena.entity.ArenaControl;

import java.time.Instant;

/**
 * Admin-facing arena payloads.
 *
 * <p>Separate from the public {@code ArenaStatusResponse} even though the two
 * overlap. The public one is polled by anyone on the internet and its field list is
 * a security decision; this one is read by an authenticated organiser and carries
 * operational detail - who changed the switch and when - that has no business on the
 * public endpoint. Keeping them as one record would make every future field a
 * question about which audience it was for.
 */
public final class ArenaAdminDtos {

    private ArenaAdminDtos() {
    }

    /** The lifecycle switch as an operator needs to see it. */
    public record ArenaControlView(
            String eventId,
            String status,
            int durationSeconds,
            Instant openedAt,
            Instant endedAt,
            Instant updatedAt,
            String updatedBy) {

        public static ArenaControlView from(ArenaControl control) {
            return new ArenaControlView(
                    control.getEventId(),
                    control.getStatus().name(),
                    control.getDurationSeconds(),
                    control.getOpenedAt(),
                    control.getEndedAt(),
                    control.getUpdatedAt(),
                    control.getUpdatedBy());
        }
    }

    /**
     * The arena panel's payload.
     *
     * <p>A wrapper around one field today, which is deliberate rather than
     * redundant: attempt counters, language spread and judge health all belong at
     * this level, and shaping the response now means adding them later is an added
     * field rather than a changed contract the panel has to be rewritten around.
     */
    public record ArenaDashboard(ArenaControlView control) {
    }

    /** PATCH body for a lifecycle change. */
    public record ArenaStatusChange(String status) {
    }
}
