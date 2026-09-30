package in.mittechkernel.registration.arena.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One terminal-run event's arena lifecycle.
 *
 * <p>Keyed by event id rather than being a singleton: V9 established
 * {@code event.requires_access_code} as the data that says which events are run
 * against a terminal, and this row exists for exactly those. Adding a second such
 * event later is an INSERT, not a second mechanism.
 *
 * <p>Deliberately holds no attempt state. An attempt's deadline is stamped on the
 * attempt itself, so changing {@link #durationSeconds} mid-event cannot move the
 * finish line for someone already running.
 */
@Entity
@Table(name = "arena_control")
public class ArenaControl {

    @Id
    @Column(name = "event_id", length = 32, nullable = false, updatable = false)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private ArenaStatus status;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    /** When the arena was most recently started. Null until it first goes ACTIVE. */
    @Column(name = "opened_at")
    private Instant openedAt;

    /** When the arena was most recently ended. Null until it first goes ENDED. */
    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    protected ArenaControl() {
        // for JPA
    }

    /**
     * Apply a status change.
     *
     * <p>Whether the change is legal is {@link ArenaStatus#canTransitionTo} business
     * and is checked by the service before this is called. What happens here is only
     * the recording of it.
     *
     * <p>{@code openedAt} and {@code endedAt} are set on entry to their state rather
     * than being derived later. An organiser asking "when did this start" after the
     * fact needs an answer that a subsequent pause cannot have overwritten.
     */
    public void transitionTo(ArenaStatus target, String actor, Instant now) {
        if (target == ArenaStatus.ACTIVE && this.status != ArenaStatus.ACTIVE) {
            this.openedAt = now;
        }
        if (target == ArenaStatus.ENDED && this.status != ArenaStatus.ENDED) {
            this.endedAt = now;
        }
        this.status = target;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public String getEventId() {
        return eventId;
    }

    public ArenaStatus getStatus() {
        return status;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }
}
