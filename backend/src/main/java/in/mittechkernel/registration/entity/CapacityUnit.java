package in.mittechkernel.registration.entity;

/**
 * What one seat of an event's capacity counts.
 *
 * <p>This has to be explicit. "Ideathon has a capacity of 40" is ambiguous between
 * 40 people and 40 teams, and the two differ by a factor of four. Making it a column
 * means the answer is configuration rather than an assumption buried in a service.
 *
 * <p>Seeded values: BuildX counts {@link #PARTICIPANT} (30 seats, solo event, so the
 * two coincide); Ideathon counts {@link #TEAM}.
 */
public enum CapacityUnit {

    /** One seat per registered person. A team of four consumes four seats. */
    PARTICIPANT,

    /** One seat per registered team, regardless of its size. */
    TEAM;

    /** How many seats a roster of {@code rosterSize} people consumes under this unit. */
    public int seatsFor(int rosterSize) {
        return this == PARTICIPANT ? rosterSize : 1;
    }
}
