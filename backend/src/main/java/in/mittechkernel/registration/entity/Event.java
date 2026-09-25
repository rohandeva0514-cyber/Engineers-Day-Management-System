package in.mittechkernel.registration.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * An event and the complete set of rules governing who may register for it and how.
 *
 * <p>This row <em>is</em> the registration policy. No service hardcodes "BuildX is first
 * year only" or "Debate needs ten people" - they read it from here. Adding an event or
 * changing a team size is a migration, not a code change.
 */
@Entity
@Table(name = "event")
public class Event {

    /** Human-readable slug, also the public API identifier: {@code buildx}, {@code tech-debate}. */
    @Id
    @Column(name = "id", length = 32, nullable = false)
    private String id;

    @Column(name = "name", length = 80, nullable = false)
    private String name;

    @Column(name = "description", nullable = false)
    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "event_eligible_year", joinColumns = @JoinColumn(name = "event_id"))
    @Column(name = "year_level", nullable = false)
    private Set<Short> eligibleYears = new LinkedHashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "participation_type", length = 16, nullable = false)
    private ParticipationType participationType;

    /**
     * Whether entering this event uses up the student's one primary-event slot.
     *
     * <p>Data, not a code branch: see V6__event_registration_slot.sql for why.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "registration_slot", length = 16, nullable = false)
    private RegistrationSlot registrationSlot;

    /**
     * Whether registering for this event issues an event-day access code.
     *
     * <p>Data, not a code branch: see V9. Nothing in Java names Debugging.
     */
    @Column(name = "requires_access_code", nullable = false)
    private boolean requiresAccessCode;

    @Column(name = "min_team_size", nullable = false)
    private int minTeamSize;

    @Column(name = "max_team_size", nullable = false)
    private int maxTeamSize;

    /** {@code null} means no automatic limit - the event is closed manually by an operator. */
    @Column(name = "capacity")
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "capacity_unit", length = 16, nullable = false)
    private CapacityUnit capacityUnit;

    /**
     * Seats consumed so far. Only ever moved by the conditional UPDATE in
     * {@code EventRepository.tryClaimSeats}, never by a read-modify-write from Java.
     */
    @Column(name = "seats_taken", nullable = false)
    private int seatsTaken;

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_status", length = 24, nullable = false)
    private RegistrationStatus registrationStatus;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected Event() {
        // for JPA
    }

    // ---------------------------------------------------------------- behaviour

    /**
     * The status a client should act on.
     *
     * <p>A stored status of CLOSED or SOLD_OUT wins outright. An OPEN event whose seats
     * are gone reports SOLD_OUT, derived from the live seat count so it can never
     * contradict it - no scheduled job, no chance of an open event with no seats left.
     */
    public RegistrationStatus effectiveRegistrationStatus() {
        if (registrationStatus != RegistrationStatus.REGISTRATION_OPEN) {
            return registrationStatus;
        }
        return isFull() ? RegistrationStatus.SOLD_OUT : RegistrationStatus.REGISTRATION_OPEN;
    }

    public boolean isOpenForRegistration() {
        return effectiveRegistrationStatus() == RegistrationStatus.REGISTRATION_OPEN;
    }

    public boolean hasCapacityLimit() {
        return capacity != null;
    }

    public boolean isFull() {
        return capacity != null && seatsTaken >= capacity;
    }

    /** Remaining seats, or {@code null} for an event with no automatic limit. */
    public Integer seatsRemaining() {
        return capacity == null ? null : Math.max(0, capacity - seatsTaken);
    }

    public boolean isYearEligible(short yearLevel) {
        return eligibleYears.contains(yearLevel);
    }

    /** How many seats a roster of this size will consume. */
    public int seatsRequiredFor(int rosterSize) {
        return capacityUnit.seatsFor(rosterSize);
    }

    public boolean isSolo() {
        return participationType == ParticipationType.SOLO;
    }

    /** True when this event can be held alongside a primary one without consuming it. */
    public boolean isOpenSlot() {
        return registrationSlot == RegistrationSlot.OPEN;
    }

    /** True when entering this event uses up the student's single primary slot. */
    public boolean isPrimarySlot() {
        return registrationSlot == RegistrationSlot.PRIMARY;
    }

    /** True when the event requires a team of one fixed size, e.g. Tech Debate's exactly 10. */
    public boolean requiresExactTeamSize() {
        return participationType == ParticipationType.TEAM && minTeamSize == maxTeamSize;
    }

    // ---------------------------------------------------------------- accessors

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Set<Short> getEligibleYears() {
        return eligibleYears;
    }

    public ParticipationType getParticipationType() {
        return participationType;
    }

    public RegistrationSlot getRegistrationSlot() {
        return registrationSlot;
    }

    /** True when a registration for this event must be issued an access code. */
    public boolean requiresAccessCode() {
        return requiresAccessCode;
    }

    /**
     * Open or close this event by hand.
     *
     * <p>Only the admin path calls this. It changes whether entries are accepted; it
     * does NOT touch capacity or seats, so reopening a full event still refuses the
     * next entry at the seat claim. Those are separate concepts on purpose.
     */
    public void setRegistrationStatus(RegistrationStatus status) {
        // updated_at is mapped read-only and has no ON UPDATE trigger, so it is not
        // touched here rather than assigned a value that would never be persisted.
        this.registrationStatus = java.util.Objects.requireNonNull(status, "status");
    }

    public int getMinTeamSize() {
        return minTeamSize;
    }

    public int getMaxTeamSize() {
        return maxTeamSize;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public CapacityUnit getCapacityUnit() {
        return capacityUnit;
    }

    public int getSeatsTaken() {
        return seatsTaken;
    }

    public RegistrationStatus getRegistrationStatus() {
        return registrationStatus;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
