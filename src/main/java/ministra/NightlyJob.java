package ministra;

import java.time.Clock;
import java.time.LocalDate;
import ministra.mail.DigestService;
import ministra.poll.PurgeService;
import ministra.web.RateLimiter;
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
        digests.queueDigests(now);
        purge.purgeExpired(LocalDate.now(clock));
        rateLimiter.evictStale(now);
    }
}
