package in.mittechkernel.registration.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A team entered into one event.
 *
 * <p>Membership is modelled as a join table rather than its own entity: {@code team_member}
 * carries no state beyond the pairing, so an entity for it would be an abstraction with
 * nothing in it.
 *
 * <p>A team is always created complete, in the same transaction as its registrations.
 * There is deliberately no path that produces a team with no members or a team whose size
 * violates its event's rules.
 */
@Entity
@Table(name = "team")
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, updatable = false)
    private Event event;

    @Column(name = "name", length = 120, nullable = false)
    private String name;

    /** The first participant on the submitted roster. The team's point of contact. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "captain_participant_id", nullable = false, updatable = false)
    private Participant captain;

    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "team_member",
            joinColumns = @JoinColumn(name = "team_id"),
            inverseJoinColumns = @JoinColumn(name = "participant_id"))
    private Set<Participant> members = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected Team() {
        // for JPA
    }

    public Team(Event event, String name, List<Participant> roster) {
        this.event = event;
        this.name = name;
        this.captain = roster.get(0);
        this.members.addAll(roster);
    }

    public Long getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public String getName() {
        return name;
    }

    public Participant getCaptain() {
        return captain;
    }

    public Set<Participant> getMembers() {
        return members;
    }

    public int size() {
        return members.size();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
