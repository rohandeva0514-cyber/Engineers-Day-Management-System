package in.mittechkernel.registration.service;

import in.mittechkernel.registration.dto.AccessCodeVerificationResponse;
import in.mittechkernel.registration.entity.Registration;
import in.mittechkernel.registration.exception.ApiErrorCode;
import in.mittechkernel.registration.exception.ApiException;
import in.mittechkernel.registration.repository.RegistrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Issues and verifies event-day access codes.
 *
 * <p>Only registrations for an event with {@code requires_access_code} get one, so
 * nothing here names Debugging and adding a second terminal-checked event is a
 * data change (see V9).
 */
@Service
public class AccessCodeService {

    private static final Logger log = LoggerFactory.getLogger(AccessCodeService.class);

    /**
     * Collision retries.
     *
     * <p>With 40 bits of entropy against a few hundred issued codes, a collision is
     * already improbable; three independent attempts makes exhausting them a
     * non-event. If it ever did, failing loudly is right - silently issuing a
     * duplicate would let one code admit two people.
     */
    private static final int MAX_ATTEMPTS = 3;

    private final RegistrationRepository registrationRepository;
    private final AccessCodeGenerator generator;

    public AccessCodeService(RegistrationRepository registrationRepository,
                             AccessCodeGenerator generator) {
        this.registrationRepository = registrationRepository;
        this.generator = generator;
    }

    /**
     * Issue codes to any registration whose event requires one.
     *
     * <p>Called inside the registration transaction, immediately after the rows are
     * written, so a registration is never observable without its code and a failure
     * here rolls the whole registration back rather than leaving someone registered
     * with no way to check in.
     *
     * <p>Uniqueness is decided by the partial unique index. The flush is what makes
     * the retry meaningful: without it the violation would surface at commit, long
     * after this method could do anything about it.
     */
    @Transactional
    public void issueFor(List<Registration> registrations) {
        for (Registration registration : registrations) {
            if (!registration.getEvent().requiresAccessCode()) {
                continue;
            }
            if (registration.getAccessCode() != null) {
                continue;
            }
            assignUniqueCode(registration);
        }
    }

    private void assignUniqueCode(Registration registration) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            registration.setAccessCode(generator.generate());
            try {
                registrationRepository.saveAndFlush(registration);
                return;
            } catch (DataIntegrityViolationException collision) {
                log.warn("Access code collision on attempt {} for registration {}",
                        attempt, registration.getId());
                if (attempt == MAX_ATTEMPTS) {
                    throw new ApiException(ApiErrorCode.INTERNAL_ERROR,
                            "Could not issue an access code. Nothing was saved.",
                            Map.of());
                }
            }
        }
    }

    /**
     * Resolve a code presented at check-in.
     *
     * <p>Returns only what a marshal needs to confirm the right person is at the
     * machine: name, branch, division, year, event and status. Never the email,
     * the phone number, or the internal participant id - this endpoint is public,
     * and a code is not a password.
     *
     * <p>Every failure is the same refusal. Distinguishing "no such code" from
     * "that code is for another event" would tell someone probing the endpoint
     * when they had found something real.
     */
    @Transactional(readOnly = true)
    public AccessCodeVerificationResponse verify(String rawCode) {
        String code = AccessCodeGenerator.normalise(rawCode);

        if (code == null || code.isBlank()) {
            throw invalid();
        }

        Registration registration = registrationRepository.findByAccessCode(code)
                .orElseThrow(AccessCodeService::invalid);

        // A code is only meaningful for the event that issued it. Belt and braces:
        // only such events ever receive one, but this is the check the requirement
        // is actually about.
        if (!registration.getEvent().requiresAccessCode()) {
            throw invalid();
        }

        return AccessCodeVerificationResponse.from(registration);
    }

    private static ApiException invalid() {
        return new ApiException(ApiErrorCode.ACCESS_CODE_INVALID,
                "That access code was not recognised. Check it and try again.",
                Map.of());
    }
}
