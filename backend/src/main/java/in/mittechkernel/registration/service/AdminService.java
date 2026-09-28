package in.mittechkernel.registration.service;

import in.mittechkernel.registration.dto.admin.AdminDtos.Dashboard;
import in.mittechkernel.registration.dto.admin.AdminDtos.EventRow;
import in.mittechkernel.registration.dto.admin.AdminDtos.Overview;
import in.mittechkernel.registration.dto.admin.AdminDtos.ParticipantPage;
import in.mittechkernel.registration.dto.admin.AdminDtos.ParticipantRow;
import in.mittechkernel.registration.entity.Event;
import in.mittechkernel.registration.entity.Participant;
import in.mittechkernel.registration.entity.Registration;
import in.mittechkernel.registration.entity.RegistrationStatus;
import in.mittechkernel.registration.repository.EventRepository;
import in.mittechkernel.registration.repository.ParticipantRepository;
import in.mittechkernel.registration.repository.RegistrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the operations panel reads and writes.
 *
 * <p>Reads only, apart from one thing: changing an event's registration status.
 * Notably it does NOT create, edit or delete registrations. The panel is a control
 * surface over the same rules students are subject to, not a way around them - so
 * there is no admin path that could hand someone a second event in one slot or a
 * thirty-first seat.
 *
 * <p>Capacity is not editable here either. Reopening a full event is allowed and
 * changes nothing about its limit: the seat claim still refuses the next entry.
 * Raising a limit is a deliberate capacity change, not a side effect of reopening.
 */
@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final EventRepository eventRepository;
    private final ParticipantRepository participantRepository;
    private final RegistrationRepository registrationRepository;
    private final RegistrationControlService control;

    public AdminService(EventRepository eventRepository,
                        ParticipantRepository participantRepository,
                        RegistrationRepository registrationRepository,
                        RegistrationControlService control) {
        this.eventRepository = eventRepository;
        this.participantRepository = participantRepository;
        this.registrationRepository = registrationRepository;
        this.control = control;
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        List<EventRow> rows = eventRows();

        int open = (int) rows.stream().filter(row ->
                RegistrationStatus.REGISTRATION_OPEN.name().equals(row.status())).count();

        return new Dashboard(
                new Overview(
                        participantRepository.count(),
                        registrationRepository.count(),
                        open,
                        rows.size() - open,
                        control.isRegistrationOpen()),
                rows);
    }

    @Transactional(readOnly = true)
    public List<EventRow> eventRows() {
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : registrationRepository.countByEvent()) {
            counts.put((String) row[0], (Long) row[1]);
        }

        return eventRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(event -> toRow(event, counts.getOrDefault(event.getId(), 0L)))
                .toList();
    }

    private EventRow toRow(Event event, long registrations) {
        RegistrationStatus effective = event.effectiveRegistrationStatus();

        // An event the admin left OPEN that reports SOLD_OUT filled up by itself.
        // That distinction is the difference between "we closed this" and "this
        // closed because it reached capacity", and the panel shows both differently.
        boolean closedByCapacity =
                event.getRegistrationStatus() == RegistrationStatus.REGISTRATION_OPEN
                        && effective == RegistrationStatus.SOLD_OUT;

        return new EventRow(
                event.getId(),
                event.getName(),
                effective.name(),
                event.getRegistrationStatus().name(),
                closedByCapacity,
                registrations,
                event.hasCapacityLimit() ? event.getSeatsTaken() : null,
                event.getCapacity(),
                event.getCapacityUnit().name(),
                event.getParticipationType().name(),
                event.getRegistrationSlot().name(),
                event.getEligibleYears().stream().sorted().toList());
    }

    /**
     * Open or close one event.
     *
     * <p>Only REGISTRATION_OPEN and REGISTRATION_CLOSED are accepted - see
     * {@link RegistrationControlService#requireSettableStatus}. Reopening a full
     * event restores its status but not its seats, so the capacity rule stays
     * authoritative and the next entry is still refused.
     */
    @Transactional
    public EventRow setEventStatus(String eventId, String rawStatus) {
        RegistrationStatus status = RegistrationControlService.requireSettableStatus(rawStatus);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> in.mittechkernel.registration.exception.ApiException
                        .eventNotFound(eventId));

        event.setRegistrationStatus(status);
        eventRepository.save(event);

        log.info("Admin set event={} registration_status={}", eventId, status);

        long registrations = registrationRepository.countByEvent().stream()
                .filter(row -> eventId.equals(row[0]))
                .map(row -> (Long) row[1])
                .findFirst()
                .orElse(0L);

        return toRow(event, registrations);
    }

    /**
     * The participant list, filtered.
     *
     * <p>Blank filters are normalised to null so an empty search box means "no
     * filter" rather than "match the empty string", which would return nothing.
     */
    @Transactional(readOnly = true)
    public ParticipantPage participants(String eventId, Short year, String branch,
                                        String division, String search) {
        List<Registration> rows = registrationRepository.findForAdmin(
                blankToNull(eventId), year, blankToNull(branch),
                blankToNull(division), blankToNull(search));

        List<ParticipantRow> participants = rows.stream().map(AdminService::toParticipantRow).toList();
        return new ParticipantPage(participants.size(), participants);
    }

    private static ParticipantRow toParticipantRow(Registration registration) {
        Participant participant = registration.getParticipant();
        return new ParticipantRow(
                registration.getId(),
                registration.getCreatedAt(),
                registration.getEvent().getId(),
                registration.getEvent().getName(),
                participant.getId(),
                participant.getFullName(),
                participant.getEmail(),
                participant.getRollNo(),
                participant.getPhone(),
                participant.getBranch(),
                participant.getDivision(),
                participant.getYearLevel(),
                registration.getTeam() == null ? null : registration.getTeam().getName(),
                registration.getAccessCode());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
