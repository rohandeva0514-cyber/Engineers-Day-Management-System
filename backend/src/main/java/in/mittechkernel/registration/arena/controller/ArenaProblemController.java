package in.mittechkernel.registration.arena.controller;

import in.mittechkernel.registration.arena.dto.ProblemDtos.DraftSaveRequest;
import in.mittechkernel.registration.arena.dto.ProblemDtos.DraftSavedResponse;
import in.mittechkernel.registration.arena.dto.ProblemDtos.ExecuteRequest;
import in.mittechkernel.registration.arena.dto.ProblemDtos.ProblemBoardResponse;
import in.mittechkernel.registration.arena.dto.ProblemDtos.ProblemDetailResponse;
import in.mittechkernel.registration.arena.dto.ProblemDtos.RunResultResponse;
import in.mittechkernel.registration.arena.dto.ProblemDtos.SubmitResultResponse;
import in.mittechkernel.registration.arena.security.ArenaPrincipal;
import in.mittechkernel.registration.arena.service.ArenaExecutionService;
import in.mittechkernel.registration.arena.service.ArenaSessionService;
import in.mittechkernel.registration.arena.service.ArenaWorkspaceService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The debugging workspace.
 *
 * <p>Three endpoints, all under {@code /api/arena/**}, which SecurityConfig requires
 * a live arena session for.
 *
 * <p><b>There is no language parameter anywhere in this class</b> - not in a path,
 * not in a query string, not in a body. The language comes from the authenticated
 * attempt, so a request for another language's problems is not refused, it is
 * inexpressible. The same goes for the attempt itself: no endpoint here accepts an
 * attempt id, a participant id or a registration id, so there is nothing for a
 * client to tamper with in order to reach someone else's mission.
 *
 * <p>Problems are addressed by board handle - {@code E-01} through {@code H-04} -
 * which is unique only within an attempt. The bank's own ids are never echoed to a
 * participant, and would not be usable in a path if they were: {@code C++-E-001}
 * carries characters some proxies rewrite inside a path segment.
 */
@RestController
@RequestMapping("/api/arena/problems")
public class ArenaProblemController {

    private final ArenaWorkspaceService workspace;
    private final ArenaExecutionService execution;

    public ArenaProblemController(ArenaWorkspaceService workspace,
                                  ArenaExecutionService execution) {
        this.workspace = workspace;
        this.execution = execution;
    }

    /** The whole board: three zones, twelve problems, none of them locked. */
    @GetMapping
    public ResponseEntity<ProblemBoardResponse> board(
            @AuthenticationPrincipal ArenaPrincipal principal) {
        return noStore(workspace.board(attemptId(principal)));
    }

    /** One problem, with the participant's draft if they have one. */
    @GetMapping("/{ref}")
    public ResponseEntity<ProblemDetailResponse> problem(
            @AuthenticationPrincipal ArenaPrincipal principal,
            @PathVariable String ref) {
        return noStore(workspace.problem(attemptId(principal), ref));
    }

    /**
     * Save what the participant has typed.
     *
     * <p>Not a submission. It runs nothing and cannot mark a problem solved.
     */
    @PutMapping("/{ref}/draft")
    public ResponseEntity<DraftSavedResponse> saveDraft(
            @AuthenticationPrincipal ArenaPrincipal principal,
            @PathVariable String ref,
            @RequestBody(required = false) DraftSaveRequest body) {
        String code = body == null ? null : body.code();
        Integer revision = body == null ? null : body.revision();

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(workspace.saveDraft(attemptId(principal), ref, code, revision));
    }

    /**
     * Run the participant's code against the visible tests.
     *
     * <p>Not a submission. Changes no status, carries no penalty, and may be
     * repeated freely.
     */
    @PostMapping("/{ref}/run")
    public ResponseEntity<RunResultResponse> run(
            @AuthenticationPrincipal ArenaPrincipal principal,
            @PathVariable String ref,
            @RequestBody(required = false) ExecuteRequest body) {
        return noStore(execution.run(attemptId(principal), ref, sourceOf(body)));
    }

    /**
     * Submit this problem for judging against the hidden tests.
     *
     * <p>Final for this problem, whatever the verdict. The other eleven are
     * untouched - this is not the end of the mission.
     */
    @PostMapping("/{ref}/submit")
    public ResponseEntity<SubmitResultResponse> submit(
            @AuthenticationPrincipal ArenaPrincipal principal,
            @PathVariable String ref,
            @RequestBody(required = false) ExecuteRequest body) {
        return noStore(execution.submit(attemptId(principal), ref, sourceOf(body)));
    }

    // ----------------------------------------------------------------- helpers

    /**
     * The one participant-controlled input to execution.
     *
     * <p>Only {@code code} is read. A body carrying a language, a Judge0 id, a CPU
     * limit or an attempt id has nowhere to bind those - {@code ExecuteRequest} has
     * a single component and Jackson drops the rest - so there is nothing to
     * validate away.
     */
    private static String sourceOf(ExecuteRequest body) {
        return body == null ? null : body.code();
    }

    private static Long attemptId(ArenaPrincipal principal) {
        if (principal == null) {
            throw ArenaSessionService.invalidSession();
        }
        return principal.attemptId();
    }

    /** Workspace content is per-participant and carries a clock. Nothing caches it. */
    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
