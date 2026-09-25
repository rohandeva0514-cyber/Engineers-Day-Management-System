package in.mittechkernel.registration.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Turns every failure into one {@link ApiErrorResponse} shape.
 *
 * <p>The interesting case is {@link #handleDataIntegrityViolation}. Several rules are enforced
 * by database constraints rather than by application checks, because constraints hold under
 * concurrency and application checks do not. When one of them fires we translate the
 * constraint name back into the domain error it represents, so a student sees "you are
 * already registered" rather than a 500.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Database constraint name -> the domain refusal it encodes. */
    private static final Map<String, ApiErrorCode> CONSTRAINT_ERRORS = Map.of(
            "uq_registration_event_participant", ApiErrorCode.DUPLICATE_REGISTRATION,
            "uq_registration_idempotency", ApiErrorCode.DUPLICATE_REGISTRATION,
            "ck_event_seats_taken", ApiErrorCode.CAPACITY_FULL,
            "uq_team_event_name", ApiErrorCode.TEAM_NAME_TAKEN,
            "uq_participant_email", ApiErrorCode.PARTICIPANT_IDENTITY_CONFLICT,
            "uq_participant_roll", ApiErrorCode.PARTICIPANT_IDENTITY_CONFLICT);

    private static final Map<ApiErrorCode, String> CONSTRAINT_MESSAGES = Map.of(
            ApiErrorCode.DUPLICATE_REGISTRATION,
            "At least one participant is already registered for this event.",
            ApiErrorCode.CAPACITY_FULL,
            "This event is full. The last seats were taken while your request was being processed.",
            ApiErrorCode.TEAM_NAME_TAKEN,
            "Another team in this event already uses that name.",
            ApiErrorCode.PARTICIPANT_IDENTITY_CONFLICT,
            "That roll number or email address is already registered to a different student.");

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex,
                                                               HttpServletRequest request) {
        ApiErrorResponse body = ApiErrorResponse.of(
                ex.getCode(), ex.getMessage(), request.getRequestURI(), ex.getDetails());
        return ResponseEntity.status(ex.getCode().status()).body(body);
    }

    /** Bean Validation on a request body. Reports every offending field at once. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                             HttpServletRequest request) {
        Map<String, Object> fieldErrors = new TreeMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(),
                        error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getObjectName(),
                        error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage()));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("fieldErrors", fieldErrors);

        return respond(ApiErrorCode.VALIDATION_FAILED,
                "The request failed validation. See details.fieldErrors.", request, details);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
                       MethodArgumentTypeMismatchException.class,
                       MissingServletRequestParameterException.class})
    public ResponseEntity<ApiErrorResponse> handleMalformed(Exception ex,
                                                            HttpServletRequest request) {
        String message = switch (ex) {
            case MissingServletRequestParameterException missing ->
                    "Required query parameter '" + missing.getParameterName() + "' is missing.";
            case MethodArgumentTypeMismatchException mismatch ->
                    "Parameter '" + mismatch.getName() + "' has the wrong type.";
            default -> "The request body could not be parsed as JSON.";
        };
        return respond(ApiErrorCode.MALFORMED_REQUEST, message, request, Map.of());
    }

    /**
     * A database constraint refused the write.
     *
     * <p>Reaching here is normal, not exceptional: the duplicate-registration and capacity
     * constraints are the authoritative guards, and under concurrency they are what actually
     * stops the second writer. The application-level checks upstream exist only to produce a
     * cheaper, more specific message in the uncontended case.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {

        String text = String.valueOf(ex.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
        ApiErrorCode code = CONSTRAINT_ERRORS.entrySet().stream()
                .filter(entry -> text.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);

        if (code == null) {
            log.error("Unmapped data integrity violation on {}", request.getRequestURI(), ex);
            return respond(ApiErrorCode.INTERNAL_ERROR,
                    "The request could not be completed.", request, Map.of());
        }

        log.info("Constraint {} refused a write on {}", code, request.getRequestURI());
        return respond(code, CONSTRAINT_MESSAGES.get(code), request, Map.of());
    }

    /** An unmapped URL. Returned in the standard shape rather than as an HTML error page. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResource(NoResourceFoundException ex,
                                                             HttpServletRequest request) {
        return respond(ApiErrorCode.EVENT_NOT_FOUND,
                "No endpoint matches " + request.getMethod() + " " + request.getRequestURI() + ".",
                request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex,
                                                             HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(ApiErrorCode.INTERNAL_ERROR,
                "Something went wrong on our side. Please try again.", request, Map.of());
    }

    private ResponseEntity<ApiErrorResponse> respond(ApiErrorCode code, String message,
                                                     HttpServletRequest request,
                                                     Map<String, Object> details) {
        return ResponseEntity.status(code.status())
                .body(ApiErrorResponse.of(code, message, request.getRequestURI(), details));
    }
}
