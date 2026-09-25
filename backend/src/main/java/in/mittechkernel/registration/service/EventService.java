package in.mittechkernel.registration.service;

import in.mittechkernel.registration.dto.EventResponse;
import in.mittechkernel.registration.entity.Event;
import in.mittechkernel.registration.exception.ApiException;
import in.mittechkernel.registration.repository.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/** Event discovery. Read-only in this milestone - events are created and configured by migration. */
@Service
public class EventService {

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional(readOnly = true)
    public List<EventResponse> listEvents() {
        return eventRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(EventResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(String eventId) {
        return EventResponse.from(requireEvent(eventId));
    }

    /**
     * Load an event or refuse the request.
     *
     * <p>Ids are lower-cased so {@code /api/events/BuildX} resolves - the id is a slug in a
     * URL a student may well type by hand.
     */
    @Transactional(readOnly = true)
    public Event requireEvent(String eventId) {
        String normalised = eventId == null ? "" : eventId.trim().toLowerCase(Locale.ROOT);
        return eventRepository.findById(normalised)
                .orElseThrow(() -> ApiException.eventNotFound(eventId));
    }
}
