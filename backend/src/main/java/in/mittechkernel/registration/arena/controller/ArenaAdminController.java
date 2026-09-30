package in.mittechkernel.registration.arena.controller;

import in.mittechkernel.registration.arena.dto.admin.ArenaAdminDtos.ArenaControlView;
import in.mittechkernel.registration.arena.dto.admin.ArenaAdminDtos.ArenaDashboard;
import in.mittechkernel.registration.arena.dto.admin.ArenaAdminDtos.ArenaStatusChange;
import in.mittechkernel.registration.arena.dto.admin.EvaluationDtos.AwardRequest;
import in.mittechkernel.registration.arena.dto.admin.EvaluationDtos.SubmissionDetail;
import in.mittechkernel.registration.arena.dto.admin.EvaluationDtos.SubmissionList;
import in.mittechkernel.registration.arena.entity.ArenaStatus;
import in.mittechkernel.registration.arena.service.ArenaControlService;
import in.mittechkernel.registration.arena.service.ArenaEvaluationService;
import in.mittechkernel.registration.arena.service.SubmissionCsv;
import in.mittechkernel.registration.exception.ApiErrorCode;
import in.mittechkernel.registration.exception.ApiException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Arena control for organisers.
 *
 * <p>Mapped under {@code /api/admin}, which SecurityConfig requires an authenticated
 * ADMIN for. That is the whole of this class's protection, and it is why a new
 * endpoint added here is guarded by default rather than by someone remembering to
 * guard it - the same property {@code AdminController} relies on.
 *
 * <p>Thin, like every other controller here: parse, delegate, return. The transition
 * rules live in {@code ArenaStatus} and the decision to apply them in
 * {@code ArenaControlService}.
 */
@RestController
@RequestMapping("/api/admin/arena")
public class ArenaAdminController {

    private final ArenaControlService control;
    private final ArenaEvaluationService evaluation;

    public ArenaAdminController(ArenaControlService control,
                                ArenaEvaluationService evaluation) {
        this.control = control;
        this.evaluation = evaluation;
    }

    @GetMapping
    public ResponseEntity<ArenaDashboard> dashboard() {
        return ResponseEntity.ok(
                new ArenaDashboard(ArenaControlView.from(control.current())));
    }

    /**
     * Start, stop, or end the arena.
     *
     * <p>The actor is taken from the authenticated principal rather than from the
     * request body. A client-supplied name on an audit field is not an audit field -
     * it is a text box.
     */
    @PatchMapping("/status")
    public ResponseEntity<ArenaControlView> setStatus(@RequestBody ArenaStatusChange body,
                                                      Authentication authentication) {
        if (body == null || body.status() == null) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED,
                    "A status is required.",
                    Map.of("allowed", "OFFLINE, ACTIVE, ENDED"));
        }

        ArenaStatus target = ArenaControlService.requireKnownStatus(body.status());
        String actor = authentication == null ? "unknown" : authentication.getName();

        return ResponseEntity.ok(ArenaControlView.from(control.transitionTo(target, actor)));
    }

    /* ------------------------------------------------ manual evaluation ----- */

    /**
     * Everyone with something to evaluate, earliest submission first.
     *
     * <p>That order is the documented tiebreak, so two equal scores already appear in
     * the order the rules would rank them. Nothing here names a winner - that stays an
     * organiser's decision after reading the code.
     */
    @GetMapping("/submissions")
    public ResponseEntity<SubmissionList> submissions() {
        return ResponseEntity.ok(evaluation.submissions());
    }

    /** One submission: every problem, and the code the participant left. */
    @GetMapping("/submissions/{attemptId}")
    public ResponseEntity<SubmissionDetail> submission(@PathVariable Long attemptId) {
        return ResponseEntity.ok(evaluation.submission(attemptId));
    }

    /**
     * Award 0 or the problem's full points, by hand.
     *
     * <p>A null {@code points} clears the award, so a mis-click is undoable without a
     * database edit. The actor comes from the authenticated principal, never the body.
     */
    @PutMapping("/submissions/{attemptId}/problems/{ref}/award")
    public ResponseEntity<SubmissionDetail> award(@PathVariable Long attemptId,
                                                  @PathVariable String ref,
                                                  @RequestBody(required = false) AwardRequest body,
                                                  Authentication authentication) {
        String actor = authentication == null ? "unknown" : authentication.getName();
        return ResponseEntity.ok(
                evaluation.award(attemptId, ref, body == null ? null : body.points(), actor));
    }

    /**
     * Everything needed to evaluate the event offline, as CSV.
     *
     * <p>One row per problem per participant, with the source inline. CSV rather than
     * JSON because the first thing an organiser will do is open it in a spreadsheet
     * beside the problem bank.
     *
     * <p>Carries organiser-only data - email, roll number, participant source - and is
     * therefore behind the same admin prefix as everything else here. It carries no
     * corrected code, no bug notes and no hidden tests: those live in the problem bank
     * on disk, and routing them through an HTTP response would put answer material
     * somewhere it has never needed to be.
     */
    @GetMapping(value = "/submissions/export.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> exportCsv() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"debugging-submissions.csv\"")
                .cacheControl(CacheControl.noStore())
                .body(SubmissionCsv.render(evaluation.allSubmissions()));
    }
}
