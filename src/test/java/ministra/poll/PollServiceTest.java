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
    void skapar_med_två_olika_token() {
        var poll = create();

        assertThat(poll.getResponseToken()).hasSize(22);
        assertThat(poll.getAdminToken()).hasSize(22);
        assertThat(poll.getResponseToken()).isNotEqualTo(poll.getAdminToken());
    }

    @Test
    void giltig_till_räknas_från_idag() {
        var poll = create();

        assertThat(poll.getValidUntil()).isEqualTo(TODAY.plusMonths(1));
    }

    @Test
    void köar_skapelsemejlet_i_samma_transaktion() {
        var poll = create();

        assertThat(outbox.findAll())
                .singleElement()
                .satisfies(email -> {
                    assertThat(email.getRecipient()).isEqualTo("markus@example.se");
                    assertThat(email.getBody()).contains(poll.getAdminToken());
                });
    }

    @Test
    void vägrar_period_längre_än_ett_halvår() {
        var start = LocalDate.of(2026, 10, 4);

        assertThatThrownBy(() -> create(form(start, start.plusMonths(7))))
                .isInstanceOf(SubmissionException.class)
                .hasMessageContaining("högst 6 månader");
    }

    @Test
    void tillåter_period_på_exakt_ett_halvår() {
        var start = LocalDate.of(2026, 10, 4);

        assertThat(create(form(start, start.plusMonths(6)))).isNotNull();
    }

    @Test
    void vägrar_slut_före_start() {
        assertThatThrownBy(() ->
                        create(form(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 10, 4))))
                .isInstanceOf(SubmissionException.class)
                .hasMessageContaining("före startdagen");
    }

    @Test
    void vägrar_startdag_som_redan_varit() {
        // Formuläret erbjuder bara framtida söndagar, men det går att posta förbi det.
        assertThatThrownBy(() ->
                        create(form(LocalDate.of(2026, 8, 2), LocalDate.of(2026, 9, 6))))
                .isInstanceOf(SubmissionException.class)
                .hasMessageContaining("måste ligga i framtiden");
    }

    @Test
    void vägrar_startdag_som_är_idag() {
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
    void erbjuder_bara_dagar_i_framtiden() {
        var sunday = LocalDate.of(2026, 9, 13);

        var offered = polls.selectableDays(sunday);

        assertThat(offered).isNotEmpty();
        assertThat(offered.getFirst().date()).isAfter(sunday);
    }

    // ---------- Lägga till egna dagar (D-043) ----------

    @Test
    void tar_med_ett_datum_som_kyrkoåret_inte_har() {
        // En församling kan fira ett lokalt helgon eller en egen högtid.
        var form = form(LocalDate.of(2026, 10, 4), LocalDate.of(2026, 11, 1));
        var kept = allDates(form);
        kept.add(LocalDate.of(2026, 10, 14));

        var poll = polls.create(form, TODAY, kept);

        assertThat(polls.daysOf(poll)).extracting(ServiceDay::date)
                .contains(LocalDate.of(2026, 10, 14));
    }

    @Test
    void egna_dagar_hamnar_i_datumordning() {
        var form = form(LocalDate.of(2026, 10, 4), LocalDate.of(2026, 11, 1));
        var kept = allDates(form);
        kept.add(LocalDate.of(2026, 10, 14));

        var poll = polls.create(form, TODAY, kept);

        assertThat(polls.daysOf(poll)).extracting(ServiceDay::date).isSorted();
    }

    @Test
    void en_egen_dag_utan_kyrkoårsnamn_har_inget_namn() {
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
    void datum_utanför_perioden_sparas_inte() {
        var form = form(LocalDate.of(2026, 10, 4), LocalDate.of(2026, 11, 1));
        var kept = allDates(form);
        kept.add(LocalDate.of(2027, 3, 2));

        var poll = polls.create(form, TODAY, kept);

        assertThat(poll.getExtraDays()).isEmpty();
        assertThat(polls.daysOf(poll)).hasSameSizeAs(polls.daysBetween(form));
    }

    @Test
    void en_egen_dag_måste_besvaras_som_alla_andra() {
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
    void en_förfrågan_kan_bestå_av_enbart_egna_dagar() {
        var form = form(LocalDate.of(2026, 10, 4), LocalDate.of(2026, 11, 1));

        var poll = polls.create(form, TODAY, Set.of(LocalDate.of(2026, 10, 14)));

        assertThat(polls.daysOf(poll)).extracting(ServiceDay::date)
                .containsExactly(LocalDate.of(2026, 10, 14));
    }

    // ---------- Svara ----------

    @Test
    void sparar_ett_svar_per_dag() {
        var poll = create();
        var days = polls.daysOf(poll);

        var participant = polls.submit(poll, "Anna", allAnswered(poll, Availability.CAN));

        assertThat(participant.getResponses()).hasSize(days.size());
    }

    @Test
    void trimmar_namnet() {
        var poll = create();

        var participant = polls.submit(poll, "  Anna  ", allAnswered(poll, Availability.CAN));

        assertThat(participant.getName()).isEqualTo("Anna");
    }

    // ---------- Välja bort dagar vid skapandet (D-042) ----------

    @Test
    void tar_bara_med_de_valda_dagarna() {
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
    void bortvalda_dagar_ingår_inte_i_ett_svar() {
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
    void vyn_visar_inte_bortvalda_dagar() {
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
    void minst_en_dag_måste_vara_med() {
        var form = form(LocalDate.of(2026, 10, 4), LocalDate.of(2026, 11, 1));

        assertThatThrownBy(() -> polls.create(form, TODAY, Set.of()))
                .isInstanceOf(SubmissionException.class)
                .hasMessageContaining("Minst en dag");
    }

    @Test
    void dagar_utanför_perioden_gör_ingen_skillnad() {
        var form = form(LocalDate.of(2026, 10, 4), LocalDate.of(2026, 11, 1));
        var kept = allDates(form);
        kept.add(LocalDate.of(2030, 1, 6));

        var poll = polls.create(form, TODAY, kept);

        assertThat(polls.daysOf(poll)).hasSameSizeAs(polls.daysBetween(form));
    }

    // ---------- Lägga till egna dagar (D-043) ----------

    @Test
    void kräver_att_alla_dagar_besvaras() {
        var poll = create();
        var answers = allAnswered(poll, Availability.CAN);
        answers.remove(polls.daysOf(poll).getFirst().date());

        assertThatThrownBy(() -> polls.submit(poll, "Anna", answers))
                .isInstanceOf(SubmissionException.class)
                .hasMessageContaining("Alla dagar måste besvaras");
    }

    @Test
    void kräver_ett_namn() {
        var poll = create();

        assertThatThrownBy(() -> polls.submit(poll, "   ", allAnswered(poll, Availability.CAN)))
                .isInstanceOf(SubmissionException.class)
                .hasMessageContaining("Skriv ditt namn");
    }

    @Test
    void vägrar_samma_namn_två_gånger_oavsett_skiftläge_och_blanksteg() {
        var poll = create();
        polls.submit(poll, "Anna", allAnswered(poll, Availability.CAN));

        assertThatThrownBy(() ->
                        polls.submit(poll, "  anna ", allAnswered(poll, Availability.CANNOT)))
                .isInstanceOf(SubmissionException.class)
                .hasMessageContaining("redan ett svar");
    }

    @Test
    void föreslår_ett_särskiljande_namn_vid_krock() {
        var poll = create();
        polls.submit(poll, "Anna", allAnswered(poll, Availability.CAN));

        assertThatThrownBy(() -> polls.submit(poll, "Anna", allAnswered(poll, Availability.CAN)))
                .hasMessageContaining("Anna J");
    }

    @Test
    void samma_namn_går_bra_i_olika_förfrågningar() {
        var first = create();
        var second = create();

        polls.submit(first, "Anna", allAnswered(first, Availability.CAN));

        assertThat(polls.submit(second, "Anna", allAnswered(second, Availability.CAN))).isNotNull();
    }

    @Test
    void struntar_i_datum_utanför_perioden() {
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
    void grupperar_namnen_per_svar() {
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
    void visar_alla_dagar_även_utan_svar() {
        var poll = create();

        var view = polls.view(poll);

        assertThat(view.days()).hasSameSizeAs(polls.daysOf(poll));
        assertThat(view.days()).allSatisfy(row -> assertThat(row.hasAnswers()).isFalse());
    }

    // ---------- Radera ----------

    @Test
    void radering_tar_med_deltagare_och_svar() {
        var poll = create();
        polls.submit(poll, "Anna", allAnswered(poll, Availability.CAN));

        polls.delete(poll);

        assertThat(participants.findAll()).isEmpty();
    }
}
