package in.mittechkernel.registration.entity;

/**
 * How an event is entered.
 *
 * <p>A {@link #SOLO} event is stored as a team size of exactly one, so the roster
 * validator never needs a special case. The distinction survives as an enum because
 * the two shapes are presented and requested differently: a solo entry carries no
 * team name and exactly one participant.
 */
public enum ParticipationType {
    SOLO,
    TEAM
}
