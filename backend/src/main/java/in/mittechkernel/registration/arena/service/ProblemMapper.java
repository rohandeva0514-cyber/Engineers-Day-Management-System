package in.mittechkernel.registration.arena.service;

import in.mittechkernel.registration.arena.bank.DebugProblem;
import in.mittechkernel.registration.arena.bank.TestCase;
import in.mittechkernel.registration.arena.dto.ProblemDtos.ProblemDetailResponse;
import in.mittechkernel.registration.arena.dto.ProblemDtos.ProblemSummary;
import in.mittechkernel.registration.arena.dto.ProblemDtos.VisibleTest;
import in.mittechkernel.registration.arena.entity.AttemptProblem;

import java.time.Instant;
import java.util.List;

/**
 * The one place a bank problem becomes something a participant can see.
 *
 * <p>Every field is listed explicitly. There is no reflection, no bean copy, no
 * "map everything except…" - because the safe version of this is the one where
 * adding a field to {@link DebugProblem} does <em>not</em> silently add it to a
 * response. A new field on the bank model reaches a participant only if someone
 * writes a line here and a matching field in the DTO.
 *
 * <p>The three things that must never cross: {@code correctedCode()},
 * {@code bugs()}, {@code hiddenTests()}. They are not read by this class at all -
 * not read and filtered, not read and dropped. Not read.
 */
final class ProblemMapper {

    private ProblemMapper() {
    }

    static ProblemSummary summary(DebugProblem problem, AttemptProblem slot) {
        return new ProblemSummary(
                problem.ref(),
                problem.title(),
                problem.difficulty().name(),
                problem.points(),
                problem.concepts(),
                slot.getStatus().name(),
                slot.hasDraft());
    }

    static ProblemDetailResponse detail(DebugProblem problem, AttemptProblem slot, Instant now) {
        return new ProblemDetailResponse(
                now,
                problem.ref(),
                problem.title(),
                problem.difficulty().name(),
                problem.points(),
                problem.concepts(),
                problem.problemStatement(),
                problem.inputFormat(),
                problem.outputFormat(),
                problem.constraints(),
                problem.language().id(),
                problem.buggyCode(),
                slot.getDraftCode(),
                slot.getDraftRevision(),
                slot.getStatus().name(),
                // visibleTests() only. hiddenTests() is the same shape, which is
                // exactly why this call names its source rather than taking a list
                // from a caller who might pass the wrong one.
                visible(problem.visibleTests()));
    }

    private static List<VisibleTest> visible(List<TestCase> tests) {
        return tests.stream()
                .map(test -> new VisibleTest(test.input(), test.expectedOutput()))
                .toList();
    }
}
