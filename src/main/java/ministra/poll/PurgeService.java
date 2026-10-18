package ministra.poll;

import java.time.LocalDate;
import ministra.mail.OutboxEmail;
import ministra.mail.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gallring av utgångna förfrågningar.
 *
 * <p>Gallring är kod, inte rutin. När "Giltig till" passerats raderas förfrågan med alla
 * svar — det är så personuppgifterna försvinner (D-028). Verkställs av nattjobbet och
 * inte på sekunden, så en förfrågan kan ta emot svar några timmar efter datumet (D-021).
 *
 * <p>Mejl som gett upp efter alla försök följer med i samma pass, men först när
 * förfrågan de gäller har gått ut (D-030). Ett mejl som fortfarande försöker rörs inte.
 */
@Service
public class PurgeService {

    private static final Logger log = LoggerFactory.getLogger(PurgeService.class);

    private final PollRepository polls;
    private final OutboxRepository outbox;

    public PurgeService(PollRepository polls, OutboxRepository outbox) {
        this.polls = polls;
        this.outbox = outbox;
    }

    /** @return antal raderade förfrågningar */
    @Transactional
    public int purgeExpired(LocalDate today) {
        var mails = outbox.deleteExhaustedExpiredBefore(OutboxEmail.MAX_ATTEMPTS, today);
        if (mails > 0) {
            log.info("Raderade {} mejl som gett upp", mails);
        }

        var expired = polls.findByValidUntilBefore(today);
        if (expired.isEmpty()) {
            return 0;
        }
        polls.deleteAll(expired);
        log.info("Raderade {} utgångna förfrågningar", expired.size());
        return expired.size();
    }
}
