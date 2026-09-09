package ministra.mail;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Ett mejl på väg ut.
 *
 * <p>Raden skrivs i samma transaktion som den domänändring som utlöser mejlet. Det är
 * hela poängen: utan outboxen blir "markera svaren som rapporterade" och "skicka mejlet"
 * två skrivningar mot olika system, och en krasch däremellan tappar svar tyst (D-030).
 *
 * <p>Raden raderas när mejlet gått iväg. Brödtexten innehåller förnamn och ska inte bli
 * ett arkiv (D-028).
 */
@Entity
@Table(name = "outbox_email")
public class OutboxEmail {

    /** Efter så här många misslyckade försök lämnas raden och felet loggas. */
    public static final int MAX_ATTEMPTS = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private @Nullable Long id;

    @Column(nullable = false, updatable = false)
    private String recipient;

    @Column(nullable = false, updatable = false)
    private String subject;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 500)
    private @Nullable String lastError;

    protected OutboxEmail() {
        // för JPA
    }

    public OutboxEmail(String recipient, String subject, String body) {
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
        this.createdAt = Instant.now();
        this.nextAttemptAt = this.createdAt;
        this.attempts = 0;
    }

    /**
     * Räknar upp försöket och skjuter nästa framåt med exponentiell backoff, en minut
     * fördubblad per försök upp till en timme.
     */
    public void recordFailure(@Nullable String error, Instant now) {
        attempts++;
        lastError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
        var backoff = Duration.ofMinutes(1L << Math.min(attempts - 1, 6));
        var capped = backoff.compareTo(Duration.ofHours(1)) > 0 ? Duration.ofHours(1) : backoff;
        nextAttemptAt = now.plus(capped);
    }

    public boolean isExhausted() {
        return attempts >= MAX_ATTEMPTS;
    }

    public @Nullable Long getId() {
        return id;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public @Nullable String getLastError() {
        return lastError;
    }
}
