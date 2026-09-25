package in.mittechkernel.registration.dto.admin;

import java.time.Instant;
import java.util.List;

/**
 * Admin-only payloads.
 *
 * <p>Kept in one file because they are small, related, and only ever used together
 * by the admin controller. Splitting six records across six files would add
 * navigation cost without adding clarity.
 *
 * <p>These carry participant contact details, which the public API never does.
 * That is the reason they live under a package the security config protects as a
 * whole rather than alongside the public DTOs where the distinction is one import
 * away from being lost.
 */
public final class AdminDtos {

    private AdminDtos() {
    }

    /** Headline counters for the dashboard. */
    public record Overview(
            long totalParticipants,
            long totalRegistrations,
            int openEvents,
            int closedEvents,
            boolean registrationSystemOpen) {
    }

    /**
     * One event as an operator needs to see it.
     *
     * @param status         the EFFECTIVE status, with SOLD_OUT folded in from seats
     * @param storedStatus   what the admin actually set, so a manual close is
     *                       distinguishable from an automatic one
     * @param closedByCapacity true when the event filled up rather than being closed
     *                       by hand - this is what drives "CLOSED AUTOMATICALLY"
     */
    public record EventRow(
            String eventId,
            String name,
            String status,
            String storedStatus,
            boolean closedByCapacity,
            long registrations,
            Integer seatsTaken,
            Integer capacity,
            String capacityUnit,
            String participationType,
            String registrationSlot,
            List<Short> eligibleYears) {
    }

    public record Dashboard(Overview overview, List<EventRow> events) {
    }

    /** A registered student, with the contact details only an organiser sees. */
    public record ParticipantRow(
            Long registrationId,
            Instant registeredAt,
            String eventId,
            String eventName,
            Long participantId,
            String fullName,
            String email,
            String rollNo,
            String phone,
            String branch,
            String division,
            short yearLevel,
            String teamName,
            /** Event-day access code, or null for events that issue none. */
            String accessCode) {
    }

    public record ParticipantPage(long total, List<ParticipantRow> participants) {
    }

    /** PATCH bodies. */
    public record StatusChange(String status) {
    }

    public record SystemStatusChange(Boolean open) {
    }
}
