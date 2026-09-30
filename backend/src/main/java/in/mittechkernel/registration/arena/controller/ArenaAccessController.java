package in.mittechkernel.registration.arena.controller;

import in.mittechkernel.registration.arena.dto.ArenaDtos.AccessRequest;
import in.mittechkernel.registration.arena.dto.ArenaDtos.AccessResponse;
import in.mittechkernel.registration.arena.service.ArenaAccessService;
import in.mittechkernel.registration.service.AccessCodeService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Check-in.
 *
 * <p>A POST with the code in the body, never a GET with it in the query string. The
 * existing marshal endpoint uses a query parameter because it is a lookup someone
 * performs on another person; this one mints a credential, and a URL carrying either
 * the code or the token it returns would end up in browser history, proxy logs and
 * referrer headers.
 *
 * <p>NOTE FOR DEPLOYMENT: this is the arena's one guessable surface, so it is the one
 * that should sit behind a rate limit at the proxy - the same note
 * {@code VerificationController} carries, and for the same reason. Nothing in the
 * application throttles it today.
 */
@RestController
@RequestMapping("/api/arena")
public class ArenaAccessController {

    private final ArenaAccessService access;

    public ArenaAccessController(ArenaAccessService access) {
        this.access = access;
    }

    @PostMapping("/access")
    public ResponseEntity<AccessResponse> checkIn(@RequestBody(required = false) AccessRequest body) {
        // A missing body is refused exactly as a wrong code is. It is not a distinct
        // failure a caller should be able to learn anything from.
        if (body == null || body.code() == null || body.code().isBlank()) {
            throw AccessCodeService.invalid();
        }

        return ResponseEntity.ok()
                // The response carries a session token. No cache, anywhere, ever.
                .cacheControl(CacheControl.noStore())
                .body(access.checkIn(body.code()));
    }
}
