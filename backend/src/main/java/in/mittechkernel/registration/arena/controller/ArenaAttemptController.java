package in.mittechkernel.registration.arena.controller;

import in.mittechkernel.registration.arena.dto.ArenaDtos.AttemptStateResponse;
import in.mittechkernel.registration.arena.dto.ArenaDtos.LanguageChoice;
import in.mittechkernel.registration.arena.security.ArenaPrincipal;
import in.mittechkernel.registration.arena.service.ArenaAttemptService;
import in.mittechkernel.registration.arena.service.ArenaSessionService;
import in.mittechkernel.registration.exception.ApiErrorCode;
import in.mittechkernel.registration.exception.ApiException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * The attempt: resume it, choose a language, start the mission.
 *
 * <p>Every path here requires a live session, which SecurityConfig enforces for the
 * whole {@code /api/arena/**} prefix. The attempt is always loaded from the id on
 * the authenticated principal and never from anything the client sent - there is no
 * attempt id in a path, a query string or a body anywhere in this class, so there is
 * nothing for a participant to change in order to reach somebody else's mission.
 *
 * <p>Thin, like every other controller here: parse, delegate, return.
 */
@RestController
@RequestMapping("/api/arena/attempt")
public class ArenaAttemptController {

    private final ArenaAttemptService attempts;

    public ArenaAttemptController(ArenaAttemptService attempts) {
        this.attempts = attempts;
    }

    /**
     * The rehydrate call.
     *
     * <p>Everything the client needs to rebuild the right screen after a refresh, a
     * crash, or a move to another machine. It is the only thing the frontend trusts
     * to decide which phase to render.
     */
    @GetMapping
    public ResponseEntity<AttemptStateResponse> state(@AuthenticationPrincipal ArenaPrincipal principal) {
        return noStore(attempts.state(attemptId(principal)));
    }

    /** Record a tentative language, before the mission starts. */
    @PutMapping("/language")
    public ResponseEntity<AttemptStateResponse> chooseLanguage(
            @AuthenticationPrincipal ArenaPrincipal principal,
            @RequestBody(required = false) LanguageChoice body) {
        return noStore(attempts.chooseLanguage(attemptId(principal), languageOf(body)));
    }

    /**
     * Start the mission. The 45 minutes begins here and nowhere else.
     *
     * <p>The language is sent again rather than read from the stored tentative
     * choice: this request is the confirmation, and the value the participant saw on
     * the button they pressed is the one that should be locked in.
     */
    @PostMapping("/start")
    public ResponseEntity<AttemptStateResponse> start(
            @AuthenticationPrincipal ArenaPrincipal principal,
            @RequestBody(required = false) LanguageChoice body) {
        return noStore(attempts.start(attemptId(principal), languageOf(body)));
    }

    /**
     * Submit the mission for manual evaluation. Once per attempt, irreversible.
     *
     * <p>No body. The code being submitted is whatever autosave has already persisted
     * - the server does not accept source here, so a client cannot submit content it
     * never saved, and the submission timestamp is the server's.
     */
    @PostMapping("/submit")
    public ResponseEntity<AttemptStateResponse> submitMission(
            @AuthenticationPrincipal ArenaPrincipal principal) {
        return noStore(attempts.submitMission(attemptId(principal)));
    }

    // ----------------------------------------------------------------- helpers

    private static String languageOf(LanguageChoice body) {
        if (body == null || body.language() == null || body.language().isBlank()) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED,
                    "A language is required.",
                    Map.of("allowed", in.mittechkernel.registration.arena.ArenaLanguage.ids()));
        }
        return body.language();
    }

    /**
     * The attempt this session authorises.
     *
     * <p>Only the id crosses into the service, which loads the row inside its own
     * transaction. Handing over an entity loaded here would hand over a detached one
     * - {@code open-in-view} is off - and every lazy association on it would throw
     * the moment a response tried to read the participant's name.
     *
     * <p>It comes from the authenticated principal and never from the request. There
     * is no attempt id in a path, a query string or a body anywhere in this class, so
     * there is nothing for a participant to edit in order to reach another mission.
     */
    private static Long attemptId(ArenaPrincipal principal) {
        if (principal == null) {
            throw ArenaSessionService.invalidSession();
        }
        return principal.attemptId();
    }

    /** Attempt state changes constantly and carries a clock. Nothing may cache it. */
    private static ResponseEntity<AttemptStateResponse> noStore(AttemptStateResponse body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
