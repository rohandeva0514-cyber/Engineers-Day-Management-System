package in.mittechkernel.registration.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;

/**
 * One person registered for one event.
 *
 * <p>A team of ten produces ten registration rows sharing a {@code team_id}. That keeps
 * "is this person registered for this event" a single indexed lookup regardless of whether
 * they entered solo or in a team, and it is what the {@code UNIQUE (event_id, participant_id)}
 * constraint protects.
 */
@Entity
@Table(name = "registration")
public class Registration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, updatable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id", nullable = false, updatable = false)
    private Participant participant;

    /** Null for a solo registration. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", updatable = false)
    private Team team;

    /** Client-supplied retry key, unique per event. Null when the client did not send one. */
    @Column(name = "idempotency_key", length = 80, updatable = false)
    private String idempotencyKey;

    /**
     * Event-day access code, or null for events that do not issue one.
     *
     * <p>Assigned after the row is created, which is why this is the one field here
     * that is updatable: the code is written in the same transaction, immediately
     * after the insert, so it is never visible to anyone in a null state.
     */
    @Column(name = "access_code", length = 16)
    private String accessCode;

    /**
     * Set by PostgreSQL's {@code now()}, not by the JVM, so the timestamp on a registration
     * is the database's clock and is consistent across application instances.
     *
     * <p>{@code @Generated} is what makes Hibernate read the value back after the INSERT.
     * Without it the field stays null in the entity that was just persisted, and the
     * {@code registeredAt} returned by a successful POST would be null while the same
     * registration fetched a moment later showed the real time.
     */
    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Registration() {
        // for JPA
    }

    public Registration(Event event, Participant participant, Team team, String idempotencyKey) {
        this.event = event;
        this.participant = participant;
        this.team = team;
        this.idempotencyKey = idempotencyKey;
    }

    public Long getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public Participant getParticipant() {
        return participant;
    }

    public Team getTeam() {
        return team;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getAccessCode() {
        return accessCode;
    }

    public void setAccessCode(String accessCode) {
        this.accessCode = accessCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
