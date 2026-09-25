package in.mittechkernel.registration.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * A student. Identified publicly by roll number, which is the only identifier a student
 * reliably knows about themselves on registration day.
 *
 * <p>There is no password and no authentication in this milestone. A participant record is
 * created on first registration and reused for every subsequent one.
 */
@Entity
@Table(name = "participant")
public class Participant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** Normalised to upper case on write; a database CHECK enforces it. */
    @Column(name = "roll_no", length = 32, nullable = false, updatable = false)
    private String rollNo;

    @Column(name = "full_name", length = 120, nullable = false)
    private String fullName;

    @Column(name = "email", length = 160, nullable = false)
    private String email;

    /** 1 or 2. Drives every eligibility decision, so it is verified against the stored record. */
    @Column(name = "year_level", nullable = false)
    private short yearLevel;

    /**
     * Contact number, branch and division.
     *
     * <p>Nullable in the schema because participants created before V3 do not have them.
     * New submissions are required to carry all three - {@link
     * in.mittechkernel.registration.dto.ParticipantRequest} enforces that - so a null here
     * means "registered before these were collected", never "optional".
     */
    @Column(name = "phone", length = 24)
    private String phone;

    @Column(name = "branch", length = 64)
    private String branch;

    @Column(name = "division", length = 16)
    private String division;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected Participant() {
        // for JPA
    }

    public Participant(String rollNo, String fullName, String email, short yearLevel,
                       String phone, String branch, String division) {
        this.rollNo = Objects.requireNonNull(rollNo, "rollNo");
        this.fullName = Objects.requireNonNull(fullName, "fullName");
        this.email = Objects.requireNonNull(email, "email");
        this.yearLevel = yearLevel;
        this.phone = phone;
        this.branch = branch;
        this.division = division;
    }

    /**
     * Fill in details this record does not have yet.
     *
     * <p>Only writes where the stored value is absent, so a record created before V3
     * completes itself the next time the student registers - without a later submission
     * being able to overwrite details already on file. Identity fields (roll number, email,
     * year) are not touched here; those are verified, never merged.
     *
     * @return true if anything changed, so the caller knows whether a save is needed
     */
    public boolean fillMissingProfile(String phone, String branch, String division) {
        boolean changed = false;
        if (isBlank(this.phone) && !isBlank(phone)) {
            this.phone = phone;
            changed = true;
        }
        if (isBlank(this.branch) && !isBlank(branch)) {
            this.branch = branch;
            changed = true;
        }
        if (isBlank(this.division) && !isBlank(division)) {
            this.division = division;
            changed = true;
        }
        return changed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public Long getId() {
        return id;
    }

    public String getRollNo() {
        return rollNo;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public short getYearLevel() {
        return yearLevel;
    }

    public String getPhone() {
        return phone;
    }

    public String getBranch() {
        return branch;
    }

    public String getDivision() {
        return division;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
