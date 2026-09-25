package in.mittechkernel.registration.controller;

import in.mittechkernel.registration.dto.ParticipantRegistrationsResponse;
import in.mittechkernel.registration.dto.RegistrationRequest;
import in.mittechkernel.registration.dto.RegistrationResponse;
import in.mittechkernel.registration.service.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * Registration.
 *
 * <p>Three endpoints, not a dozen. Creating a team, adding its members and registering it are
 * one atomic POST rather than three calls, because a partially-built team is a state nobody
 * can use and somebody would have to clean up - and with Tech Debate requiring exactly ten
 * members, abandoned half-teams would be the common case, not the edge case.
 */
@RestController
@RequestMapping("/api/registrations")
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    /**
     * Register one participant or one complete team.
     *
     * <p>Returns <b>201 Created</b> with a {@code Location} header for a new registration, or
     * <b>200 OK</b> when an idempotency key replayed an earlier success - a retry did not
     * create anything, and saying otherwise would be a lie the client may act on.
     */
    @PostMapping
    public ResponseEntity<RegistrationResponse> register(
            @Valid @RequestBody RegistrationRequest request) {

        RegistrationService.RegistrationOutcome outcome = registrationService.register(request);
        RegistrationResponse body = outcome.response();

        if (!outcome.created()) {
            return ResponseEntity.ok(body);
        }

        URI location = UriComponentsBuilder.fromPath("/api/registrations/{participantId}")
                .buildAndExpand(body.registrations().get(0).participant().participantId())
                .toUri();
        return ResponseEntity.created(location).body(body);
    }

    /** Everything this participant is registered for. */
    @GetMapping("/{participantId}")
    public ResponseEntity<ParticipantRegistrationsResponse> getByParticipantId(
            @PathVariable Long participantId) {
        return ResponseEntity.ok(registrationService.getRegistrationsByParticipantId(participantId));
    }

    /**
     * The same view, found by roll number.
     *
     * <p>Exists because a student returning on a different device knows their roll number and
     * not their numeric id. A query parameter rather than a second path so there is one
     * resource here, addressed two ways.
     */
    @GetMapping(params = "rollNo")
    public ResponseEntity<ParticipantRegistrationsResponse> getByRollNo(
            @RequestParam String rollNo) {
        return ResponseEntity.ok(registrationService.getRegistrationsByRollNo(rollNo));
    }
}
