package ministra.mail;

import java.time.Clock;
import java.time.Instant;
import ministra.MinistraProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tömmer outboxen.
 *
 * <p>Det här är det enda stället i appen som rör {@link JavaMailSender}. Skickar du mejl
 * någon annanstans ifrån återinför du dubbelskrivningen som outboxen finns för att ta
 * bort (D-030).
 */
@Component
public class OutboxSender {

    private static final Logger log = LoggerFactory.getLogger(OutboxSender.class);

    /** Så många per varv. Volymen är liten; taket finns för att inte hålla en lång transaktion. */
    private static final Limit BATCH = Limit.of(25);

    private final OutboxRepository outbox;
    private final JavaMailSender mailSender;
    private final MinistraProperties properties;
    private final Clock clock;

    public OutboxSender(
            OutboxRepository outbox,
            JavaMailSender mailSender,
            MinistraProperties properties,
            Clock clock) {
        this.outbox = outbox;
        this.mailSender = mailSender;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 10_000)
    @Transactional
    public void drain() {
        var now = clock.instant();
        var due =
                outbox.findByNextAttemptAtBeforeAndAttemptsLessThanOrderByCreatedAtAsc(
                        now, OutboxEmail.MAX_ATTEMPTS, BATCH);
        for (var email : due) {
            send(email, now);
        }
    }

    private void send(OutboxEmail email, Instant now) {
        var message = new SimpleMailMessage();
        message.setFrom(properties.mailFrom());
        message.setTo(email.getRecipient());
        message.setSubject(email.getSubject());
        message.setText(email.getBody());
        try {
            mailSender.send(message);
            // Raden raderas när mejlet gått iväg. Brödtexten innehåller förnamn och ska
            // inte bli ett arkiv (D-028).
            outbox.delete(email);
        } catch (MailException e) {
            email.recordFailure(e.getMessage(), now);
            outbox.save(email);
            // Aldrig mottagaradressen i loggen, inte ens på DEBUG.
            if (email.isExhausted()) {
                log.error(
                        "Mejl {} gav upp efter {} försök: {}",
                        email.getId(),
                        email.getAttempts(),
                        e.getMessage());
            } else {
                log.warn(
                        "Mejl {} misslyckades, försök {}: {}",
                        email.getId(),
                        email.getAttempts(),
                        e.getMessage());
            }
        }
    }
}
