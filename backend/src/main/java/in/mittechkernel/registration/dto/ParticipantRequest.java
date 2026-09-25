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
        Short yearLevel) {

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
}
