package ministra.poll;

import java.time.LocalDate;
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
 */
@Service
public class PurgeService {

    private static final Logger log = LoggerFactory.getLogger(PurgeService.class);

    private final PollRepository polls;

    public PurgeService(PollRepository polls) {
        this.polls = polls;
    }

    /** @return antal raderade förfrågningar */
    @Transactional
    public int purgeExpired(LocalDate today) {
        var expired = polls.findByValidUntilBefore(today);
        if (expired.isEmpty()) {
            return 0;
        }
        polls.deleteAll(expired);
        log.info("Raderade {} utgångna förfrågningar", expired.size());
        return expired.size();
    }
}
