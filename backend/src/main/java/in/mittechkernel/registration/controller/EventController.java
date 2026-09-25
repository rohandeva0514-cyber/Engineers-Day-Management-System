package in.mittechkernel.registration.controller;

import in.mittechkernel.registration.dto.EventResponse;
import in.mittechkernel.registration.service.EventService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Event discovery.
 *
 * <p>Public and read-only. Both endpoints return the effective registration status, with
 * SOLD_OUT derived from the live seat count, so a client never has to work out for itself
 * whether an open event still has room.
 */
@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    /** All seven events, in display order. The registration page renders straight from this. */
    @GetMapping
    public ResponseEntity<List<EventResponse>> listEvents() {
        return ResponseEntity.ok(eventService.listEvents());
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<EventResponse> getEvent(@PathVariable String eventId) {
        return ResponseEntity.ok(eventService.getEvent(eventId));
    }
}
