package in.mittechkernel.registration;

import in.mittechkernel.registration.dto.ParticipantRequest;
import in.mittechkernel.registration.dto.RegistrationRequest;

import java.util.List;
import java.util.stream.IntStream;

/** Builders for readable test payloads. Roll numbers are unique per call so tests cannot collide. */
final class TestRequests {

    private TestRequests() {
    }

    static ParticipantRequest participant(String rollNo, short year) {
        return new ParticipantRequest(rollNo, "Student " + rollNo,
                rollNo.toLowerCase() + "@mit.example.edu", year,
                "9876543210", "Computer Engineering", "A");
    }

    /** A roster of {@code count} distinct students, all in the given year. */
    static List<ParticipantRequest> roster(String prefix, int count, short year) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> participant(String.format("%s%03d", prefix, i), year))
                .toList();
    }

    static RegistrationRequest solo(String eventId, String rollNo, short year) {
        return new RegistrationRequest(eventId, null, List.of(participant(rollNo, year)), null);
    }

    static RegistrationRequest team(String eventId, String teamName,
                                    List<ParticipantRequest> members) {
        return new RegistrationRequest(eventId, teamName, members, null);
    }

    static RegistrationRequest team(String eventId, String teamName, String prefix,
                                    int size, short year) {
        return team(eventId, teamName, roster(prefix, size, year));
    }
}
