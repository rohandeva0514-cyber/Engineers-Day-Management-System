package in.mittechkernel.registration.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * The one error shape every endpoint returns. No endpoint invents its own.
 *
 * @param timestamp when the failure was produced, server clock
 * @param status    the HTTP status, repeated in the body so logs and captures are self-contained
 * @param code      the stable {@link ApiErrorCode} name - the field clients branch on
 * @param message   human-readable, safe to show a student
 * @param path      the request path
 * @param details   structured context; omitted from the JSON when empty
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        Map<String, Object> details) {

    public static ApiErrorResponse of(ApiErrorCode code, String message, String path,
                                      Map<String, Object> details) {
        return new ApiErrorResponse(Instant.now(), code.status().value(), code.name(),
                message, path, details == null ? Map.of() : details);
    }
}
