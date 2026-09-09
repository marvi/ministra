package ministra;

import java.time.Clock;
import java.time.LocalDate;
import ministra.mail.DigestService;
import ministra.poll.PurgeService;
import ministra.web.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nattpasset, klockan 22 svensk tid (D-024).
 *
 * <p>Ordningen är inte godtycklig: en förfrågan som går ut idag ska ge skaparen en sista
 * sammanfattning av dagens svar innan den raderas. Omvänd ordning tappar den tyst.
 */
@Component
public class NightlyJob {

    private static final Logger log = LoggerFactory.getLogger(NightlyJob.class);

    private final DigestService digests;
    private final PurgeService purge;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    public NightlyJob(
            DigestService digests, PurgeService purge, RateLimiter rateLimiter, Clock clock) {
        this.digests = digests;
        this.purge = purge;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 22 * * *", zone = "Europe/Stockholm")
    public void run() {
        var now = clock.instant();
        log.info("Nattpasset börjar");
        var digestCount = digests.queueDigests(now);
        var purged = purge.purgeExpired(LocalDate.now(clock));
        rateLimiter.evictStale(now);
        log.info(
                "Nattpasset klart: {} sammanfattningar köade, {} förfrågningar raderade",
                digestCount,
                purged);
    }
}
