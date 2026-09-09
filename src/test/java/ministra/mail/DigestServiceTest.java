package ministra.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import ministra.PostgresTest;
import ministra.TestBeans;
import ministra.poll.Availability;
import ministra.poll.CreatePollForm;
import ministra.poll.Poll;
import ministra.poll.PollService;
import ministra.poll.ResponseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import(TestBeans.class)
class DigestServiceTest extends PostgresTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 9);
    private static final Instant NOW = Instant.parse("2026-09-09T20:00:00Z");

    @Autowired PollService polls;
    @Autowired DigestService digests;
    @Autowired OutboxRepository outbox;
    @Autowired ResponseRepository responses;

    private Poll createPoll(String title, String creatorEmail) {
        return polls.create(
                new CreatePollForm(
                        title,
                        null,
                        LocalDate.of(2026, 10, 4),
                        LocalDate.of(2026, 11, 1),
                        1,
                        "Markus",
                        creatorEmail),
                TODAY,
                allDates(LocalDate.of(2026, 10, 4), LocalDate.of(2026, 11, 1)));
    }

    private void answer(Poll poll, String name) {
        var answers = new HashMap<LocalDate, Availability>();
        polls.daysOf(poll).forEach(day -> answers.put(day.date(), Availability.CAN));
        polls.submit(poll, name, Map.copyOf(answers));
    }

    /** Skapelsemejlen ligger också i outboxen; de räknas inte som sammanfattningar. */
    private long digestCount() {
        return outbox.findAll().stream()
                .filter(email -> email.getSubject().startsWith("Nya svar"))
                .count();
    }

    /** Alla gudstjänstdagar i perioden. Bortval testas i PollServiceTest. */
    private java.util.Set<java.time.LocalDate> allDates(LocalDate from, LocalDate to) {
        return polls
                .daysBetween(
                        new CreatePollForm("x", null, from, to, 1, "Markus", "markus@example.se"))
                .stream()
                .map(ministra.calendar.ServiceDay::date)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    @Test
    void skickar_inget_när_inget_nytt_kommit_in() {
        createPoll("Sakristaner", "markus@example.se");

        assertThat(digests.queueDigests(NOW)).isZero();
        assertThat(digestCount()).isZero();
    }

    @Test
    void ett_mejl_per_skapare_oavsett_antal_svar() {
        var poll = createPoll("Sakristaner", "markus@example.se");
        answer(poll, "Anna");
        answer(poll, "Bengt");
        answer(poll, "Cecilia");

        assertThat(digests.queueDigests(NOW)).isEqualTo(1);

        assertThat(outbox.findAll())
                .filteredOn(email -> email.getSubject().startsWith("Nya svar"))
                .singleElement()
                .satisfies(email -> {
                    assertThat(email.getRecipient()).isEqualTo("markus@example.se");
                    assertThat(email.getBody()).contains("Anna, Bengt, Cecilia");
                });
    }

    @Test
    void flera_förfrågningar_för_samma_skapare_blir_ett_mejl() {
        var first = createPoll("Sakristaner", "markus@example.se");
        var second = createPoll("Textläsare", "markus@example.se");
        answer(first, "Anna");
        answer(second, "Bengt");

        assertThat(digests.queueDigests(NOW)).isEqualTo(1);

        assertThat(outbox.findAll())
                .filteredOn(email -> email.getSubject().startsWith("Nya svar"))
                .singleElement()
                .satisfies(email -> {
                    assertThat(email.getSubject()).isEqualTo("Nya svar på dina förfrågningar");
                    assertThat(email.getBody()).contains("Sakristaner");
                    assertThat(email.getBody()).contains("Textläsare");
                });
    }

    @Test
    void olika_skapare_får_var_sitt_mejl() {
        var first = createPoll("Sakristaner", "markus@example.se");
        var second = createPoll("Textläsare", "hustrun@example.se");
        answer(first, "Anna");
        answer(second, "Bengt");

        assertThat(digests.queueDigests(NOW)).isEqualTo(2);
        assertThat(digestCount()).isEqualTo(2);
    }

    @Test
    void samma_svar_rapporteras_aldrig_två_gånger() {
        // Idempotensen är hela skälet till att outboxen finns (D-030).
        var poll = createPoll("Sakristaner", "markus@example.se");
        answer(poll, "Anna");

        digests.queueDigests(NOW);
        assertThat(digests.queueDigests(NOW.plusSeconds(86_400))).isZero();

        assertThat(digestCount()).isEqualTo(1);
    }

    @Test
    void markerar_svaren_i_samma_transaktion_som_outboxraden() {
        var poll = createPoll("Sakristaner", "markus@example.se");
        answer(poll, "Anna");
        assertThat(responses.findUnnotified()).isNotEmpty();

        digests.queueDigests(NOW);

        assertThat(responses.findUnnotified()).isEmpty();
    }

    @Test
    void nya_svar_efter_en_sammanfattning_ger_en_ny() {
        var poll = createPoll("Sakristaner", "markus@example.se");
        answer(poll, "Anna");
        digests.queueDigests(NOW);

        answer(poll, "Bengt");

        assertThat(digests.queueDigests(NOW.plusSeconds(86_400))).isEqualTo(1);
        assertThat(digestCount()).isEqualTo(2);
        assertThat(outbox.findAll())
                .filteredOn(email -> email.getSubject().startsWith("Nya svar"))
                .last()
                .satisfies(email -> {
                    assertThat(email.getBody()).contains("Bengt");
                    assertThat(email.getBody()).doesNotContain("Anna");
                });
    }
}
