package in.mittechkernel.registration.controller;

import in.mittechkernel.registration.dto.admin.AdminDtos.Dashboard;
import in.mittechkernel.registration.dto.admin.AdminDtos.EventRow;
import in.mittechkernel.registration.dto.admin.AdminDtos.ParticipantPage;
import in.mittechkernel.registration.dto.admin.AdminDtos.StatusChange;
import in.mittechkernel.registration.dto.admin.AdminDtos.SystemStatusChange;
import in.mittechkernel.registration.exception.ApiErrorCode;
import in.mittechkernel.registration.exception.ApiException;
import in.mittechkernel.registration.service.AdminService;
import in.mittechkernel.registration.service.RegistrationControlService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * The operations panel's API.
 *
 * <p>Every path here is under {@code /api/admin}, which SecurityConfig requires an
 * authenticated ADMIN for. That single prefix rule is why no method needs its own
 * annotation and why a new endpoint added to this class is protected by default
 * rather than by someone remembering to protect it.
 *
 * <p>Thin, as the other controllers are: parse, delegate, return. Every decision
 * lives in AdminService or RegistrationControlService.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final RegistrationControlService control;

    public AdminController(AdminService adminService, RegistrationControlService control) {
        this.adminService = adminService;
        this.control = control;
    }

    /** Counters plus every event, in one call: the whole panel's first paint. */
    @GetMapping("/dashboard")
    public ResponseEntity<Dashboard> dashboard() {
        return ResponseEntity.ok(adminService.dashboard());
    }

    @GetMapping("/events")
    public ResponseEntity<List<EventRow>> events() {
        return ResponseEntity.ok(adminService.eventRows());
    }

    /**
     * The participant list, with every filter optional.
     *
     * <p>Carries contact details, which is exactly why it sits behind the admin
     * prefix and is never reachable from the public event-day lookup.
     */
    @GetMapping("/participants")
    public ResponseEntity<ParticipantPage> participants(
            @RequestParam(required = false) String eventId,
            @RequestParam(required = false) Short year,
            @RequestParam(required = false) String branch,
            @RequestParam(required = false) String division,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(
                adminService.participants(eventId, year, branch, division, search));
    }

    /** Event-scoped variant, so a list screen can link straight to one event. */
    @GetMapping("/events/{eventId}/participants")
    public ResponseEntity<ParticipantPage> eventParticipants(
            @PathVariable String eventId,
            @RequestParam(required = false) Short year,
            @RequestParam(required = false) String branch,
            @RequestParam(required = false) String division,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(
                adminService.participants(eventId, year, branch, division, search));
    }

    /** Open or close one event. Capacity is untouched - see AdminService. */
    @PatchMapping("/events/{eventId}/status")
    public ResponseEntity<EventRow> setEventStatus(@PathVariable String eventId,
                                                   @RequestBody StatusChange body) {
        if (body == null || body.status() == null) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED,
                    "A status is required.",
                    Map.of("allowed", "REGISTRATION_OPEN, REGISTRATION_CLOSED"));
        }
        return ResponseEntity.ok(adminService.setEventStatus(eventId, body.status()));
    }

    /** The master switch. Closing it refuses every registration, for every event. */
    @PatchMapping("/registration/status")
    public ResponseEntity<Map<String, Boolean>> setSystemStatus(
            @RequestBody SystemStatusChange body) {
        if (body == null || body.open() == null) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED,
                    "`open` must be true or false.", Map.of());
        }
        boolean open = control.setRegistrationOpen(body.open());
        return ResponseEntity.ok(Map.of("registrationSystemOpen", open));
    }

    @GetMapping("/registration/status")
    public ResponseEntity<Map<String, Boolean>> systemStatus() {
        return ResponseEntity.ok(Map.of("registrationSystemOpen", control.isRegistrationOpen()));
    }
}
