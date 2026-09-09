package ministra.poll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import ministra.PostgresTest;
import ministra.calendar.ServiceDay;
import ministra.TestBeans;
import ministra.mail.OutboxRepository;
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
class PollServiceTest extends PostgresTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 9);

    @Autowired PollService polls;
    @Autowired OutboxRepository outbox;
    @Autowired ParticipantRepository participants;

    private CreatePollForm form(LocalDate start, LocalDate end) {
        return new CreatePollForm(
                "Sakristaner fram till påsk",
                "Hör av dig vid frågor.",
                start,
                end,
                1,
                "Markus",
                "markus@example.se");
    }

    private Poll create() {
        return create(form(LocalDate.of(2026, 10, 4), LocalDate.of(2026, 11, 1)));
    }

    /** Skapar med alla dagar kvar. Bortval testas för sig. */
    private Poll create(CreatePollForm form) {
        return polls.create(form, TODAY, allDates(form));
    }

    private Set<LocalDate> allDates(CreatePollForm form) {
        return polls.daysBetween(form).stream()
                .map(ServiceDay::date)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private Map<LocalDate, Availability> allAnswered(Poll poll, Availability value) {
        var answers = new HashMap<LocalDate, Availability>();
        polls.daysOf(poll).forEach(day -> answers.put(day.date(), value));
        return answers;
    }

    // ---------- Skapa ----------

    @Test
    void creates_with_two_different_tokens() {
        var poll = create();

        assertThat(poll.getResponseToken()).hasSize(22);
        assertThat(poll.getAdminToken()).hasSize(22);
        assertThat(poll.getResponseToken()).isNotEqualTo(poll.getAdminToken());
    }

    @Test
    void valid_until_is_counted_from_today() {
        var poll = create();

        assertThat(poll.getValidUntil()).isEqualTo(TODAY.plusMonths(1));
    }

    @Test
    void queues_the_creation_email_in_the_same_transaction() {
        var poll = create();

        assertThat(outbox.findAll())
                .singleElement()
                .satisfies(email -> {
                    assertThat(email.getRecipient()).isEqualTo("markus@example.se");
                    assertThat(email.getBody()).contains(poll.getAdminToken());
                });
    }

    @Test
    void rejects_a_period_longer_than_six_months() {
        var start = LocalDate.of(2026, 10, 4);

        assertThatThrownBy(() -> create(form(start, start.plusMonths(7))))
                .isInstanceOf(SubmissionException.class)
                .hasMessageContaining("högst 6 månader");
    }

    @Test
    void allows_a_period_of_exactly_six_months() {
        var start = LocalDate.of(2026, 10, 4);

        assertThat(create(form(start, start.plusMonths(6)))).isNotNull();
    }

    @Test
    void rejects_end_before_start() {
        assertThatThrownBy(() ->
                        create(form(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 10, 4))))
                .isInstanceOf(SubmissionException.class)
                .hasMessageContaining("före startdagen");
    }

    @Test
    void rejects_a_start_day_in_the_past() {
        // Formuläret erbjuder bara framtida söndagar, men det går att posta förbi det.
        assertThatThrownBy(() ->
                        create(form(LocalDate.of(2026, 8, 2), LocalDate.of(2026, 9, 6))))
                .isInstanceOf(SubmissionException.class)
                .hasMessageContaining("måste ligga i framtiden");
    }

    @Test
    void rejects_a_start_day_that_is_today() {
        var sunday = LocalDate.of(2026, 9, 13);

        assertThatThrownBy(() ->
                        polls.create(
                                form(sunday, sunday.plusWeeks(4)),
                                sunday,
                                allDates(form(sunday, sunday.plusWeeks(4)))))
                .isInstanceOf(SubmissionException.class)
                .hasMessageContaining("måste ligga i framtiden");
    }

    @Test
    void offers_only_days_in_the_future() {
        var sunday = LocalDate.of(2026, 9, 13);

        var offered = polls.selectableDays(sunday);

        assertThat(offered).isNotEmpty();
        assertThat(offered.getFirst().date()).isAfter(sunday);
    }

    // ---------- Lägga till egna dagar (D-043) ----------

    @Test
    void includes_a_date_the_church_year_does_not_have() {
        // En församling kan fira ett lokalt helgon eller en egen högtid.
        var form = form(LocalDate.of(2026, 10, 4), LocalDate.of(2026, 11, 1));
        var kept = allDates(form);
        kept.add(LocalDate.of(2026, 10, 14));

        var poll = polls.create(form, TODAY, kept);

        assertThat(polls.daysOf(poll)).extracting(ServiceDay::date)
                .contains(LocalDate.of(2026, 10, 14));
    }

    @Test
    void extra_days_land_in_date_order() {
        var form = form(LocalDate.of(2026, 10, 4), LocalDate.of(2026, 11, 1));
        var kept = allDates(form);
        kept.add(LocalDate.of(2026, 10, 14));

        var poll = polls.create(form, TODAY, kept);

        assertThat(polls.daysOf(poll)).extracting(ServiceDay::date).isSorted();
    }

    @Test
    void an_extra_day_without_a_church_year_name_has_no_name() {
        var form = form(LocalDate.of(2026, 10, 4), LocalDate.of(2026, 11, 1));
        var kept = allDates(form);
        kept.add(LocalDate.of(2026, 10, 14));
        var poll = polls.create(form, TODAY, kept);

        assertThat(polls.daysOf(poll))
                .filteredOn(day -> day.date().equals(LocalDate.of(2026, 10, 14)))
                .singleElement()
                .satisfies(day -> assertThat(day.hasName()).isFalse());
    }

    @Test
    void dates_outside_the_period_are_not_saved() {
        var form = form(LocalDate.of(2026, 10, 4), LocalDate.of(2026, 11, 1));
        var kept = allDates(form);
        kept.add(LocalDate.of(2027, 3, 2));

        var poll = polls.create(form, TODAY, kept);

        assertThat(poll.getExtraDays()).isEmpty();
        assertThat(polls.daysOf(poll)).hasSameSizeAs(polls.daysBetween(form));
    }

    @Test
    void an_extra_day_must_be_answered_like_any_other() {
        var form = form(LocalDate.of(2026, 10, 4), LocalDate.of(2026, 11, 1));
        var kept = allDates(form);
        kept.add(LocalDate.of(2026, 10, 14));
        var poll = polls.create(form, TODAY, kept);
        var answers = allAnswered(poll, Availability.CAN);
        answers.remove(LocalDate.of(2026, 10, 14));

        assertThatThrownBy(() -> polls.submit(poll, "Anna", answers))
                .isInstanceOf(SubmissionException.class)
                .hasMessageContaining("Alla dagar måste besvaras");
    }

    @Test
    void a_poll_may_consist_of_extra_days_only() {
        var form = form(LocalDate.of(2026, 10, 4), LocalDate.of(2026, 11, 1));

        var poll = polls.create(form, TODAY, Set.of(LocalDate.of(2026, 10, 14)));

        assertThat(polls.daysOf(poll)).extracting(ServiceDay::date)
                .containsExactly(LocalDate.of(2026, 10, 14));
    }

    // ---------- Svara ----------

    @Test
    void saves_one_answer_per_day() {
        var poll = create();
        var days = polls.daysOf(poll);

        var participant = polls.submit(poll, "Anna", allAnswered(poll, Availability.CAN));

        assertThat(participant.getResponses()).hasSize(days.size());
    }

    @Test
    void trims_the_name() {
        var poll = create();

        var participant = polls.submit(poll, "  Anna  ", allAnswered(poll, Availability.CAN));

        assertThat(participant.getName()).isEqualTo("Anna");
    }

    // ---------- Välja bort dagar vid skapandet (D-042) ----------

    @Test
    void includes_only_the_chosen_days() {
        var form = form(LocalDate.of(2026, 10, 4), LocalDate.of(2026, 11, 1));
        var all = polls.daysBetween(form);
        var dropped = all.getFirst().date();
        var kept = allDates(form);
        kept.remove(dropped);

        var poll = polls.create(form, TODAY, kept);

        assertThat(polls.daysOf(poll)).hasSize(all.size() - 1);
        assertThat(polls.daysOf(poll)).extracting(ServiceDay::date).doesNotContain(dropped);
    }

    @Test
    void excluded_days_are_not_part_of_an_answer() {
        var form = form(LocalDate.of(2026, 10, 4), LocalDate.of(2026, 11, 1));
        var dropped = polls.daysBetween(form).getFirst().date();
        var kept = allDates(form);
        kept.remove(dropped);
        var poll = polls.create(form, TODAY, kept);

        var participant = polls.submit(poll, "Anna", allAnswered(poll, Availability.CAN));

        assertThat(participant.getResponses())
                .extracting(Response::getServiceDate)
                .doesNotContain(dropped);
    }

    @Test
    void the_view_does_not_show_excluded_days() {
        var form = form(LocalDate.of(2026, 10, 4), LocalDate.of(2026, 11, 1));
        var dropped = polls.daysBetween(form).getFirst().date();
        var kept = allDates(form);
        kept.remove(dropped);
        var poll = polls.create(form, TODAY, kept);

        assertThat(polls.view(poll).days())
                .extracting(row -> row.day().date())
                .doesNotContain(dropped);
    }

    @Test
    void at_least_one_day_must_be_included() {
        var form = form(LocalDate.of(2026, 10, 4), LocalDate.of(2026, 11, 1));

        assertThatThrownBy(() -> polls.create(form, TODAY, Set.of()))
                .isInstanceOf(SubmissionException.class)
                .hasMessageContaining("Minst en dag");
    }

    @Test
    void days_outside_the_period_make_no_difference() {
        var form = form(LocalDate.of(2026, 10, 4), LocalDate.of(2026, 11, 1));
        var kept = allDates(form);
        kept.add(LocalDate.of(2030, 1, 6));

        var poll = polls.create(form, TODAY, kept);

        assertThat(polls.daysOf(poll)).hasSameSizeAs(polls.daysBetween(form));
    }

    // ---------- Lägga till egna dagar (D-043) ----------

    @Test
    void requires_every_day_to_be_answered() {
        var poll = create();
        var answers = allAnswered(poll, Availability.CAN);
        answers.remove(polls.daysOf(poll).getFirst().date());

        assertThatThrownBy(() -> polls.submit(poll, "Anna", answers))
                .isInstanceOf(SubmissionException.class)
                .hasMessageContaining("Alla dagar måste besvaras");
    }

    @Test
    void requires_a_name() {
        var poll = create();

        assertThatThrownBy(() -> polls.submit(poll, "   ", allAnswered(poll, Availability.CAN)))
                .isInstanceOf(SubmissionException.class)
                .hasMessageContaining("Skriv ditt namn");
    }

    @Test
    void rejects_the_same_name_twice_regardless_of_case_and_whitespace() {
        var poll = create();
        polls.submit(poll, "Anna", allAnswered(poll, Availability.CAN));

        assertThatThrownBy(() ->
                        polls.submit(poll, "  anna ", allAnswered(poll, Availability.CANNOT)))
                .isInstanceOf(SubmissionException.class)
                .hasMessageContaining("redan ett svar");
    }

    @Test
    void suggests_a_distinguishing_name_on_collision() {
        var poll = create();
        polls.submit(poll, "Anna", allAnswered(poll, Availability.CAN));

        assertThatThrownBy(() -> polls.submit(poll, "Anna", allAnswered(poll, Availability.CAN)))
                .hasMessageContaining("Anna J");
    }

    @Test
    void the_same_name_is_fine_in_different_polls() {
        var first = create();
        var second = create();

        polls.submit(first, "Anna", allAnswered(first, Availability.CAN));

        assertThat(polls.submit(second, "Anna", allAnswered(second, Availability.CAN))).isNotNull();
    }

    @Test
    void ignores_dates_outside_the_period() {
        var poll = create();
        var answers = allAnswered(poll, Availability.CAN);
        answers.put(LocalDate.of(2030, 1, 6), Availability.CANNOT);

        var participant = polls.submit(poll, "Anna", answers);

        assertThat(participant.getResponses())
                .extracting(Response::getServiceDate)
                .doesNotContain(LocalDate.of(2030, 1, 6));
    }

    // ---------- Vyn ----------

    @Test
    void groups_names_by_answer() {
        var poll = create();
        polls.submit(poll, "Anna", allAnswered(poll, Availability.CAN));
        polls.submit(poll, "Bengt", allAnswered(poll, Availability.IF_NEEDED));
        polls.submit(poll, "Cecilia", allAnswered(poll, Availability.CANNOT));

        var view = polls.view(poll);

        assertThat(view.participantNames()).containsExactly("Anna", "Bengt", "Cecilia");
        assertThat(view.days()).allSatisfy(row -> {
            assertThat(row.can()).containsExactly("Anna");
            assertThat(row.ifNeeded()).containsExactly("Bengt");
            assertThat(row.cannot()).containsExactly("Cecilia");
        });
    }

    @Test
    void shows_every_day_even_without_answers() {
        var poll = create();

        var view = polls.view(poll);

        assertThat(view.days()).hasSameSizeAs(polls.daysOf(poll));
        assertThat(view.days()).allSatisfy(row -> assertThat(row.hasAnswers()).isFalse());
    }

    // ---------- Radera ----------

    @Test
    void deletion_takes_participants_and_answers_with_it() {
        var poll = create();
        polls.submit(poll, "Anna", allAnswered(poll, Availability.CAN));

        polls.delete(poll);

        assertThat(participants.findAll()).isEmpty();
    }
}
