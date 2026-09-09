package ministra.poll;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import ministra.PostgresTest;
import ministra.TestBeans;
import ministra.mail.OutboxRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Samma tjänst, men <em>utan</em> en omslutande testtransaktion.
 *
 * <p>{@code @DataJpaTest} håller annars allt i en och samma transaktion, så en förfrågan
 * som lästs i ett anrop är fortfarande knuten till sessionen i nästa. I appen är den det
 * inte: {@code spring.jpa.open-in-view} är avstängt, och varje anrop har sin egen
 * transaktion. Skillnaden döljer fel med lata kopplingar tills appen körs på riktigt, och
 * det är precis vad som hände här.
 *
 * <p>Priset är att raderna blir kvar efter varje test och måste städas bort för hand.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import(TestBeans.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PollServiceDetachedTest extends PostgresTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 9);

    @Autowired PollService polls;
    @Autowired PollRepository pollRepository;
    @Autowired OutboxRepository outbox;

    @AfterEach
    void cleanUp() {
        pollRepository.deleteAll();
        outbox.deleteAll();
    }

    private Poll create() {
        return polls.create(
                new CreatePollForm(
                        "Sakristaner",
                        null,
                        LocalDate.of(2026, 10, 4),
                        LocalDate.of(2026, 11, 1),
                        1,
                        "Markus",
                        "markus@example.se"),
                TODAY,
                allDates(LocalDate.of(2026, 10, 4), LocalDate.of(2026, 11, 1)));
    }

    private Map<LocalDate, Availability> allAnswered(Poll poll, Availability value) {
        var answers = new HashMap<LocalDate, Availability>();
        polls.daysOf(poll).forEach(day -> answers.put(day.date(), value));
        return Map.copyOf(answers);
    }

    /** Så här ser det ut i appen: förfrågan slås upp i ett anrop och används i nästa. */
    private Poll reloaded(Poll poll) {
        return polls.byResponseToken(poll.getResponseToken());
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
    void accepts_an_answer_on_a_detached_poll() {
        var poll = reloaded(create());

        var participant = polls.submit(poll, "Anna", allAnswered(poll, Availability.CAN));

        assertThat(participant.getName()).isEqualTo("Anna");
    }

    @Test
    void builds_the_view_from_a_detached_poll() {
        var created = create();
        polls.submit(created, "Anna", allAnswered(created, Availability.CAN));

        var view = polls.view(reloaded(created));

        assertThat(view.participantNames()).containsExactly("Anna");
        assertThat(view.days()).allSatisfy(row -> assertThat(row.can()).containsExactly("Anna"));
    }

    @Test
    void deletes_a_detached_poll() {
        var created = create();
        polls.submit(created, "Anna", allAnswered(created, Availability.CAN));

        polls.delete(reloaded(created));

        assertThat(pollRepository.findById(created.getId())).isEmpty();
    }

    @Test
    void name_collision_is_detected_across_transaction_boundaries() {
        var poll = reloaded(create());
        polls.submit(poll, "Anna", allAnswered(poll, Availability.CAN));

        var again = reloaded(poll);
        assertThat(
                        org.assertj.core.api.Assertions.catchThrowable(
                                () -> polls.submit(again, "anna", allAnswered(again, Availability.CANNOT))))
                .isInstanceOf(SubmissionException.class);
    }
}
