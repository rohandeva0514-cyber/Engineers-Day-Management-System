package in.mittechkernel.registration.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.mittechkernel.registration.entity.Event;
import in.mittechkernel.registration.entity.RegistrationStatus;

import java.util.List;

/**
 * An event as the API presents it.
 *
 * <p>The list and the detail endpoint return the same shape. There is no second, thinner
 * summary DTO because there is nothing on an event worth hiding from the list - and a
 * client that has to re-fetch to learn the team size will just re-fetch everything anyway.
 *
 * @param registrationStatus the <em>effective</em> status, with SOLD_OUT derived from live
 *                           seat counts, so it can never contradict {@code seatsRemaining}
 * @param capacity           null when the event has no automatic limit (closed manually)
 * @param seatsRemaining     null when {@code capacity} is null
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record EventResponse(
        String eventId,
        String name,
        String description,
        List<Short> eligibleYears,
        String participationType,
        int minTeamSize,
        int maxTeamSize,
        Integer capacity,
        String capacityUnit,
        Integer seatsTaken,
        Integer seatsRemaining,
        RegistrationStatus registrationStatus,
        boolean registrationOpen,
        /**
         * The registration group this event belongs to: a student may hold at most one
         * registration per group.
         *
         * <p>Sent per event so the client never needs to know which events compete with
         * which. Grouping the catalogue by this value is enough to show a student the
         * choice they are actually making. Presentation only - the rule is enforced
         * server-side on every submit.
         */
        String registrationSlot,

        /** How that group is named to a student, e.g. "Build". */
        String registrationSlotLabel) {

    public static EventResponse from(Event event) {
        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getDescription(),
                event.getEligibleYears().stream().sorted().toList(),
                event.getParticipationType().name(),
                event.getMinTeamSize(),
                event.getMaxTeamSize(),
                event.getCapacity(),
                event.getCapacityUnit().name(),
                event.hasCapacityLimit() ? event.getSeatsTaken() : null,
                event.seatsRemaining(),
                event.effectiveRegistrationStatus(),
                event.isOpenForRegistration(),
                event.getRegistrationSlot().name(),
                event.getRegistrationSlot().label());
    }
}
