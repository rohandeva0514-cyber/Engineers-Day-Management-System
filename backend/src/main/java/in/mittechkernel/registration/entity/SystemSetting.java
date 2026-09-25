package in.mittechkernel.registration.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Operational state that outlives a restart. Exactly one row, forever.
 *
 * <p>The primary key is a boolean fixed to TRUE with a CHECK behind it (see V8), so
 * "the settings" can never become ambiguous by someone inserting a second row.
 *
 * <p>Holds the master registration switch only. Per-event state stays on the event
 * row where it belongs; this sits above it.
 */
@Entity
@Table(name = "system_setting")
public class SystemSetting {

    public static final boolean ID = true;

    @Id
    @Column(name = "id", nullable = false)
    private Boolean id = ID;

    @Column(name = "registrations_open", nullable = false)
    private boolean registrationsOpen;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private Instant updatedAt;

    protected SystemSetting() {
        // for JPA
    }

    public boolean isRegistrationsOpen() {
        return registrationsOpen;
    }

    public void setRegistrationsOpen(boolean open) {
        this.registrationsOpen = open;
        this.updatedAt = Instant.now();
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
