package in.mittechkernel.registration.arena.controller;

import in.mittechkernel.registration.arena.dto.ArenaStatusResponse;
import in.mittechkernel.registration.arena.service.ArenaControlService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * The arena's front door, before anyone has identified themselves.
 *
 * <p>Public by necessity: a participant standing at a terminal needs to know whether
 * the competition has started, and there is nothing to authenticate them with yet.
 * What makes that acceptable is the response - it says whether a door is open and
 * nothing whatever about who is behind it.
 *
 * <p>NOTE FOR DEPLOYMENT: every waiting participant polls this every ten seconds, so
 * it should be the cheapest endpoint in the service. It is one primary-key lookup
 * and no joins, and it must stay that way.
 */
@RestController
@RequestMapping("/api/arena")
public class ArenaPublicController {

    private final ArenaControlService control;

    public ArenaPublicController(ArenaControlService control) {
        this.control = control;
    }

    /**
     * Is the arena open, and for how long does a mission run.
     *
     * <p>{@code no-store} deliberately. The window between an organiser pressing
     * Start and thirty waiting screens noticing is the one moment this endpoint has
     * to be right, and a proxy holding a stale OFFLINE through it would mean students
     * staring at a closed door that had already opened. There is no caching win worth
     * that for a handful of requests a second.
     */
    @GetMapping("/status")
    public ResponseEntity<ArenaStatusResponse> status() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ArenaStatusResponse.from(control.current(), Instant.now()));
    }
}
