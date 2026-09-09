package ministra.mail;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import ministra.poll.Poll;
import ministra.poll.ResponseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Den dagliga sammanfattningen: ett mejl per skapare, aldrig ett per svar (D-011).
 *
 * <p>Outbox-raden skrivs och svaren markeras som rapporterade i <em>samma transaktion</em>.
 * Det är hela poängen med outboxen — utan den är det två skrivningar mot olika system, och
 * en krasch däremellan tappar svar tyst (D-030).
 */
@Service
public class DigestService {

    private static final Logger log = LoggerFactory.getLogger(DigestService.class);

    private final ResponseRepository responses;
    private final OutboxRepository outbox;
    private final MailTexts texts;

    public DigestService(
            ResponseRepository responses, OutboxRepository outbox, MailTexts texts) {
        this.responses = responses;
        this.outbox = outbox;
        this.texts = texts;
    }

    /**
     * Köar sammanfattningar för allt som kommit in sedan förra passet.
     *
     * @return antal skapare som får ett mejl. Har inget nytt kommit in blir det noll och
     *     ingenting skickas — inga "det hände ingenting"-utskick (D-024).
     */
    @Transactional
    public int queueDigests(Instant now) {
        var unnotified = responses.findUnnotified();
        if (unnotified.isEmpty()) {
            return 0;
        }

        // Skapare -> förfrågan -> namnen på dem som svarat. LinkedHash* genomgående, så att
        // ordningen i mejlet blir densamma varje gång och går att testa.
        Map<String, Map<Poll, LinkedHashSet<String>>> byCreator = new LinkedHashMap<>();
        for (var response : unnotified) {
            var participant = response.getParticipant();
            var poll = participant.getPoll();
            byCreator
                    .computeIfAbsent(poll.getCreatorEmail(), unused -> new LinkedHashMap<>())
                    .computeIfAbsent(poll, unused -> new LinkedHashSet<>())
                    .add(participant.getName());
            response.markNotified(now);
        }

        for (var creator : byCreator.entrySet()) {
            var items = new ArrayList<MailTexts.DigestItem>();
            String creatorName = null;
            for (var entry : creator.getValue().entrySet()) {
                creatorName = entry.getKey().getCreatorName();
                items.add(new MailTexts.DigestItem(entry.getKey(), List.copyOf(entry.getValue())));
            }
            outbox.save(
                    new OutboxEmail(
                            creator.getKey(),
                            texts.digestSubject(items),
                            texts.digestBody(creatorName, items)));
        }

        // Aldrig adresser eller namn i loggen, bara antal.
        log.info(
                "Köade {} sammanfattningar för {} nya svar",
                byCreator.size(),
                unnotified.size());
        return byCreator.size();
    }
}
