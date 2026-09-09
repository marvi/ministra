package ministra.poll;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import ministra.PostgresTest;
import ministra.TestBeans;
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
class PurgeServiceTest extends PostgresTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 9);

    @Autowired PollService polls;
    @Autowired PurgeService purge;
    @Autowired PollRepository pollRepository;
    @Autowired ParticipantRepository participants;
    @Autowired ResponseRepository responses;

    private Poll createdOn(LocalDate created) {
        return polls.create(
                new CreatePollForm(
                        "Sakristaner",
                        null,
                        LocalDate.of(2026, 10, 4),
                        LocalDate.of(2026, 11, 1),
                        1,
                        "Markus",
                        "markus@example.se"),
                created,
                allDates(LocalDate.of(2026, 10, 4), LocalDate.of(2026, 11, 1)));
    }

    private void answer(Poll poll, String name) {
        var answers = new HashMap<LocalDate, Availability>();
        polls.daysOf(poll).forEach(day -> answers.put(day.date(), Availability.CAN));
        polls.submit(poll, name, Map.copyOf(answers));
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
    void deletes_polls_whose_validity_has_expired() {
        var expired = createdOn(TODAY.minusMonths(2));

        assertThat(purge.purgeExpired(TODAY)).isEqualTo(1);
        assertThat(pollRepository.findById(expired.getId())).isEmpty();
    }

    @Test
    void leaves_valid_polls_alone() {
        createdOn(TODAY);

        assertThat(purge.purgeExpired(TODAY)).isZero();
        assertThat(pollRepository.findAll()).hasSize(1);
    }

    @Test
    void keeps_the_poll_on_its_last_valid_day() {
        // "Giltig till" verkställs av nattjobbet, inte på sekunden (D-021). Dagen ut gäller.
        var poll = createdOn(TODAY.minusMonths(1));
        assertThat(poll.getValidUntil()).isEqualTo(TODAY);

        assertThat(purge.purgeExpired(TODAY)).isZero();
    }

    @Test
    void deletion_takes_participants_and_answers_with_it() {
        var poll = createdOn(TODAY.minusMonths(2));
        answer(poll, "Anna");
        assertThat(participants.findAll()).isNotEmpty();

        purge.purgeExpired(TODAY);

        assertThat(participants.findAll()).isEmpty();
        assertThat(responses.findAll()).isEmpty();
    }
}
