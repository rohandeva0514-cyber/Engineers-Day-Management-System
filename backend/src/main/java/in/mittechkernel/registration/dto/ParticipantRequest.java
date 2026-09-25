package in.mittechkernel.registration.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * One person on a submitted roster.
 *
 * <p>Bean Validation here only checks the <em>shape</em> of the payload. Whether this
 * person is eligible, already registered, or exists under a different email is decided by
 * the service against the database - never by these annotations and never by the client.
 */
public record ParticipantRequest(

        @NotBlank(message = "roll number is required")
        @Size(max = 32, message = "roll number must be at most 32 characters")
        @Pattern(regexp = "^[A-Za-z0-9/_-]+$",
                 message = "roll number may only contain letters, digits, hyphen, underscore and slash")
        String rollNo,

        @NotBlank(message = "full name is required")
        @Size(max = 120, message = "full name must be at most 120 characters")
        String fullName,

        @NotBlank(message = "email is required")
        @Email(message = "must be a valid email address")
        @Size(max = 160, message = "email must be at most 160 characters")
        String email,

        @NotNull(message = "year level is required")
        @Min(value = 1, message = "year level must be 1 or 2")
        @Max(value = 2, message = "year level must be 1 or 2")
        Short yearLevel,

        /*
         * Deliberately permissive: digits, with an optional country code, after
         * separators are stripped. Anything stricter starts refusing real numbers -
         * the pattern accepts 7 to 15 digits, which is the E.164 range and therefore
         * every valid number on earth. Confirming the number is a delivery problem,
         * not a regex problem.
         */
        @NotBlank(message = "phone number is required")
        @Pattern(regexp = "^\\+?[0-9][0-9 ()-]{5,22}[0-9]$",
                 message = "must be a valid phone number")
        String phone,

        @NotBlank(message = "branch is required")
        @Size(max = 64, message = "branch must be at most 64 characters")
        String branch,

        @NotBlank(message = "division is required")
        @Size(max = 16, message = "division must be at most 16 characters")
        String division) {

    /** Roll numbers are compared and stored upper-cased; emails are stored as typed. */
    public String normalisedRollNo() {
        return rollNo == null ? null : rollNo.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public String normalisedEmail() {
        return email == null ? null : email.trim();
    }

    public String normalisedFullName() {
        return fullName == null ? null : fullName.trim();
    }

    /**
     * Separators are dropped so "+91 98765 43210" and "+919876543210" are one number.
     *
     * <p>Stored canonically for the same reason roll numbers are upper-cased: the same
     * person typing it two different ways must not become two different records.
     */
    public String normalisedPhone() {
        return phone == null ? null : phone.replaceAll("[^0-9+]", "");
    }

    public String normalisedBranch() {
        return branch == null ? null : branch.trim();
    }

    public String normalisedDivision() {
        return division == null ? null : division.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
