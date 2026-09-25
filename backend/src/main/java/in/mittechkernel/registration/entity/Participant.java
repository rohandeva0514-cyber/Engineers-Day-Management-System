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

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected Participant() {
        // for JPA
    }

    public Participant(String rollNo, String fullName, String email, short yearLevel) {
        this.rollNo = Objects.requireNonNull(rollNo, "rollNo");
        this.fullName = Objects.requireNonNull(fullName, "fullName");
        this.email = Objects.requireNonNull(email, "email");
        this.yearLevel = yearLevel;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
