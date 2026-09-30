package in.mittechkernel.registration.arena.service;

import in.mittechkernel.registration.arena.dto.admin.EvaluationDtos.SubmissionDetail;
import in.mittechkernel.registration.arena.dto.admin.EvaluationDtos.SubmittedProblem;

import java.util.List;

/**
 * Renders every submission as CSV for offline evaluation.
 *
 * <p>One row per problem per participant, with the source code inline, so an
 * organiser can open it in a spreadsheet beside the problem bank and work down the
 * list. Identity is repeated on every row rather than grouped: grouped output looks
 * tidier and is far worse to sort or filter, which is the only thing anybody will
 * actually do with this file.
 *
 * <h2>Quoting</h2>
 *
 * <p>Source code is the hostile case for CSV - it contains commas, double quotes and
 * newlines as a matter of course. Every field is therefore unconditionally quoted and
 * every embedded quote doubled, per RFC 4180. Quoting only the fields that appear to
 * need it is how a program's source ends up shifting the columns of the row it is on.
 *
 * <p>A UTF-8 BOM is prepended. Excel on Windows otherwise reads a UTF-8 CSV as the
 * system codepage and mangles any non-ASCII character in a participant's name, and
 * this file exists to be opened in a spreadsheet.
 *
 * <p>Deliberately absent: corrected code, bug notes, hidden tests. Those stay in the
 * problem bank on disk. An organiser reads them there; there is no reason to route
 * answer material through an HTTP response.
 */
public final class SubmissionCsv {

    private static final String BOM = "﻿";

    private static final List<String> HEADERS = List.of(
            "full_name", "roll_no", "email", "branch", "year", "division",
            "language", "attempt_state", "final_submitted_at", "total_score",
            "evaluation_status", "problem_ref", "problem_title", "difficulty",
            "points", "awarded_points", "attempted", "source_code");

    private SubmissionCsv() {
    }

    public static String render(List<SubmissionDetail> submissions) {
        StringBuilder csv = new StringBuilder(BOM);
        row(csv, HEADERS);

        for (SubmissionDetail submission : submissions) {
            var p = submission.participant();

            for (SubmittedProblem problem : submission.problems()) {
                row(csv, List.of(
                        text(p.fullName()),
                        text(p.rollNo()),
                        text(p.email()),
                        text(p.branch()),
                        String.valueOf(p.yearLevel()),
                        text(p.division()),
                        text(p.language()),
                        text(p.state()),
                        p.finalSubmittedAt() == null ? "" : p.finalSubmittedAt().toString(),
                        p.totalScore() == null ? "" : String.valueOf(p.totalScore()),
                        text(p.evaluationStatus()),
                        text(problem.ref()),
                        text(problem.title()),
                        text(problem.difficulty()),
                        String.valueOf(problem.points()),
                        problem.awardedPoints() == null ? "" : String.valueOf(problem.awardedPoints()),
                        problem.attempted() ? "yes" : "no",
                        text(problem.sourceCode())));
            }
        }
        return csv.toString();
    }

    private static void row(StringBuilder csv, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(quote(values.get(i)));
        }
        // CRLF, as RFC 4180 specifies and as Excel expects.
        csv.append("\r\n");
    }

    /** Every field quoted, every embedded quote doubled. No exceptions. */
    private static String quote(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
