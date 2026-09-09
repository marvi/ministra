package ministra.poll;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Skapa-formuläret.
 *
 * <p>Första och sista dagen väljs ur en lista av verkliga gudstjänstdagar, så det går inte
 * att skicka in ett datum utan gudstjänst (D-008: svårt att göra fel). Servern
 * kontrollerar ändå — formuläret går att posta förbi.
 */
public record CreatePollForm(
        @NotBlank(message = "Skriv en titel, till exempel \"Sakristaner fram till påsk\".")
                @Size(max = 120, message = "Titeln får vara högst 120 tecken.")
                String title,
        @Size(max = 1000, message = "Kommentaren får vara högst 1000 tecken.") String comment,
        @NotNull(message = "Välj första dagen.") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                LocalDate startDate,
        @NotNull(message = "Välj sista dagen.") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                LocalDate endDate,
        @Min(value = 1, message = "Välj en eller två månader.")
                @Max(value = 2, message = "Välj en eller två månader.")
                int validMonths,
        @NotBlank(message = "Skriv ditt förnamn.")
                @Size(max = 80, message = "Namnet får vara högst 80 tecken.")
                String creatorName,
        @NotBlank(message = "Skriv din e-postadress.")
                @Email(message = "Kontrollera e-postadressen.")
                @Size(max = 254, message = "E-postadressen är för lång.")
                String creatorEmail) {}
