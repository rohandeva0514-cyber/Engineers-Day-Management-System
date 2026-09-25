package in.mittechkernel.registration.service;

import in.mittechkernel.registration.entity.RegistrationStatus;
import in.mittechkernel.registration.entity.SystemSetting;
import in.mittechkernel.registration.exception.ApiErrorCode;
import in.mittechkernel.registration.exception.ApiException;
import in.mittechkernel.registration.repository.SystemSettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * The master registration switch.
 *
 * <p>One flag, read on every registration and written only by an authenticated
 * admin. It sits ABOVE per-event status: global closed beats event open, which is
 * what makes "stop everything" actually stop everything rather than requiring an
 * organiser to close seven events one at a time while entries keep arriving.
 *
 * <p>Read from the database rather than cached in memory. An organiser flipping the
 * switch expects it to take effect everywhere immediately, and a cache would mean
 * one instance still accepting entries after the panel says it stopped.
 */
@Service
public class RegistrationControlService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationControlService.class);

    private final SystemSettingRepository settingRepository;

    public RegistrationControlService(SystemSettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    @Transactional(readOnly = true)
    public boolean isRegistrationOpen() {
        return settings().isRegistrationsOpen();
    }

    /**
     * Refuse the request outright when the system is paused.
     *
     * <p>Checked before anything else in the registration flow: no participant is
     * created, no seat is claimed, nothing is written.
     */
    @Transactional(readOnly = true)
    public void requireRegistrationOpen() {
        if (!isRegistrationOpen()) {
            throw new ApiException(ApiErrorCode.REGISTRATION_SYSTEM_CLOSED,
                    "Registration is currently closed for all events.",
                    Map.of("scope", "SYSTEM"));
        }
    }

    @Transactional
    public boolean setRegistrationOpen(boolean open) {
        SystemSetting setting = settings();
        setting.setRegistrationsOpen(open);
        settingRepository.save(setting);

        log.info("Registration system switched {} by admin", open ? "OPEN" : "CLOSED");
        return open;
    }

    private SystemSetting settings() {
        return settingRepository.findById(SystemSetting.ID)
                .orElseThrow(() -> new IllegalStateException(
                        "system_setting row is missing; V8 seeds it and it must never be deleted"));
    }

    /**
     * The only statuses an admin may set by hand.
     *
     * <p>SOLD_OUT is deliberately not one of them. It is <em>derived</em> from seats
     * against capacity, not stored, so allowing it to be set would create a second
     * source of truth that could disagree with the seat count. An admin who wants to
     * stop entries uses REGISTRATION_CLOSED; an event becomes SOLD_OUT by filling up.
     */
    public static RegistrationStatus requireSettableStatus(String raw) {
        RegistrationStatus status;
        try {
            status = RegistrationStatus.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException cause) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED,
                    "Unknown registration status '" + raw + "'.",
                    Map.of("allowed", "REGISTRATION_OPEN, REGISTRATION_CLOSED"));
        }

        if (status == RegistrationStatus.SOLD_OUT) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED,
                    "SOLD_OUT is derived from capacity and cannot be set by hand. "
                            + "Close the event, or change its capacity.",
                    Map.of("allowed", "REGISTRATION_OPEN, REGISTRATION_CLOSED"));
        }
        return status;
    }
}
