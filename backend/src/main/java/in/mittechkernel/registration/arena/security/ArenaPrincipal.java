package in.mittechkernel.registration.arena.security;

import in.mittechkernel.registration.arena.entity.ArenaAttempt;

/**
 * Who is behind an arena request.
 *
 * <p>An id and a name, and nothing else. The filter resolves the attempt once to
 * authenticate the request; controllers then load it fresh inside their own
 * transaction rather than carrying a detached entity through the security context,
 * where it would be a stale copy by the time anything wrote to it.
 *
 * <p>{@code displayName} exists only so a log line can say who did something. It is
 * never used to make a decision.
 */
public record ArenaPrincipal(Long attemptId, String displayName) {

    public static ArenaPrincipal of(ArenaAttempt attempt) {
        return new ArenaPrincipal(
                attempt.getId(),
                attempt.getParticipant().getFullName());
    }

    @Override
    public String toString() {
        return "attempt#" + attemptId;
    }
}
