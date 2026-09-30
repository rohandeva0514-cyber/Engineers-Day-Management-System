package in.mittechkernel.registration.arena.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.mittechkernel.registration.arena.ArenaLanguage;
import in.mittechkernel.registration.arena.entity.ArenaControl;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * What the arena gate shows before anyone has identified themselves.
 *
 * <p>Public, and polled every ten seconds by every waiting participant, so the field
 * list is chosen for what a closed door needs to say and nothing else: is it open,
 * how long the mission runs, which languages exist, and what time the server thinks
 * it is.
 *
 * <p>Deliberately ABSENT: participant counts, attempt counts, problem counts, who is
 * running. None of that is needed to render a waiting screen, and all of it would be
 * readable by anyone on the internet the moment the arena went live.
 *
 * <h2>Why serverTime is here</h2>
 *
 * <p>Every countdown the client renders is anchored to the server's clock, not the
 * browser's. Sending the server's view of "now" alongside every response lets the
 * client compute a skew offset once and keep using it - which is what stops a laptop
 * whose clock is nine minutes fast from showing a mission deadline that has already
 * passed. The client timer is decoration either way; the backend refuses late
 * requests regardless.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ArenaStatusResponse(
        String eventId,
        String status,
        Instant serverTime,
        int durationSeconds,
        List<Language> languages) {

    /** One selectable language, as the gate and the language cards need it. */
    public record Language(String id, String label, String runtime) {
    }

    public static ArenaStatusResponse from(ArenaControl control, Instant serverTime) {
        return new ArenaStatusResponse(
                control.getEventId(),
                control.getStatus().name(),
                serverTime,
                control.getDurationSeconds(),
                Arrays.stream(ArenaLanguage.values())
                        .map(language -> new Language(
                                language.id(), language.label(), language.runtime()))
                        .toList());
    }
}
