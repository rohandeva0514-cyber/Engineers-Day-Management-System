package in.mittechkernel.registration.controller;

import in.mittechkernel.registration.dto.AccessCodeVerificationResponse;
import in.mittechkernel.registration.service.AccessCodeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Event-day check-in.
 *
 * <p>Public by necessity: a student types their code at a terminal, and there is
 * no account to authenticate them with. What makes that acceptable is the shape of
 * the response rather than the shape of the endpoint - it releases only what a
 * marshal needs to confirm identity, and never contact details.
 *
 * <p>NOTE FOR DEPLOYMENT: this is the one public endpoint where guessing has any
 * value, so it is the one that should sit behind a rate limit at the proxy. An
 * 8-character code is 40 bits, which is far beyond casual guessing, but a limit
 * removes the question entirely.
 */
@RestController
@RequestMapping("/api/verify")
public class VerificationController {

    private final AccessCodeService accessCodeService;

    public VerificationController(AccessCodeService accessCodeService) {
        this.accessCodeService = accessCodeService;
    }

    @GetMapping
    public ResponseEntity<AccessCodeVerificationResponse> verify(@RequestParam String code) {
        return ResponseEntity.ok(accessCodeService.verify(code));
    }
}
