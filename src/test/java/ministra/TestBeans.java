package ministra;

import ministra.calendar.ChurchCalendar;
import ministra.mail.DigestService;
import ministra.mail.MailTexts;
import ministra.mail.OutboxRepository;
import ministra.poll.ParticipantRepository;
import ministra.poll.PollRepository;
import ministra.poll.PollService;
import ministra.poll.PurgeService;
import ministra.poll.ResponseRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Tjänsterna som persistenstesterna behöver.
 *
 * <p>{@code @DataJpaTest} plockar bara upp repositories, så resten kopplas ihop här i
 * stället för att dra igång hela applikationen med {@code @SpringBootTest}.
 */
@TestConfiguration
public class TestBeans {

    public static final String BASE_URL = "https://test.example";

    @Bean
    MinistraProperties properties() {
        return new MinistraProperties(BASE_URL, 10, "ministra@example.se");
    }

    @Bean
    ChurchCalendar calendar() {
        return new ChurchCalendar();
    }

    @Bean
    MailTexts texts(MinistraProperties properties) {
        return new MailTexts(properties);
    }

    @Bean
    PollService pollService(
            PollRepository polls,
            ParticipantRepository participants,
            ChurchCalendar calendar,
            OutboxRepository outbox,
            MailTexts texts) {
        return new PollService(polls, participants, calendar, outbox, texts);
    }

    @Bean
    DigestService digestService(
            ResponseRepository responses, OutboxRepository outbox, MailTexts texts) {
        return new DigestService(responses, outbox, texts);
    }

    @Bean
    PurgeService purgeService(PollRepository polls) {
        return new PurgeService(polls);
    }
}
