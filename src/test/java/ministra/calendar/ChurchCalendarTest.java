package ministra.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Ren JUnit, ingen Spring-kontext. Kalendern har inga beroenden att koppla upp. */
class ChurchCalendarTest {

    private final ChurchCalendar calendar = new ChurchCalendar();

    @Test
    void tar_med_helgdagar_som_inte_är_söndagar() {
        // Kyrkan firar gudstjänst även juldagen och annandagarna (D-040).
        var days = calendar.serviceDaysBetween(LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 31));

        assertThat(days).extracting(ServiceDay::name)
                .contains("Juldagen", "Annandag jul");
    }

    @Test
    void tar_med_annandag_pingst_och_långfredagen() {
        var days = calendar.serviceDaysBetween(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31));

        assertThat(days).extracting(ServiceDay::name)
                .contains("Långfredagen", "Annandag påsk", "Annandag pingst",
                        "Kristi himmelsfärds dag", "Alla helgons dag");
    }

    @Test
    void utelämnar_vardagar_utan_egen_gudstjänst() {
        // Måndag till onsdag i Stilla veckan är OrdinaryDay i API:t och ska inte med.
        var days = calendar.serviceDaysBetween(LocalDate.of(2027, 3, 20), LocalDate.of(2027, 3, 26));

        assertThat(days).extracting(ServiceDay::name)
                .doesNotContain("Måndag i Stilla veckan", "Tisdag i Stilla veckan",
                        "Onsdag i Stilla veckan");
    }

    @Test
    void hämtar_kyrkoårets_namn_från_apiet() {
        var days = calendar.serviceDaysBetween(LocalDate.of(2026, 11, 29), LocalDate.of(2026, 11, 29));

        assertThat(days).singleElement().satisfies(day ->
                assertThat(day.name()).isEqualTo("Första söndagen i advent"));
    }

    @Test
    void täcker_varje_söndag_även_över_ett_årsskifte() {
        var from = LocalDate.of(2026, 12, 1);
        var to = LocalDate.of(2027, 2, 1);

        var days = calendar.serviceDaysBetween(from, to);

        var everySunday = from.datesUntil(to.plusDays(1))
                .filter(date -> date.getDayOfWeek() == DayOfWeek.SUNDAY)
                .toList();
        assertThat(days).extracting(ServiceDay::date).containsAll(everySunday);
    }

    @Test
    void är_i_datumordning() {
        var days = calendar.serviceDaysBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 6, 30));

        assertThat(days).extracting(ServiceDay::date).isSorted();
    }

    @Test
    void namnger_ett_godtyckligt_datum_bara_när_apiet_verkligen_känner_det() {
        // getCurrentDay ger närmast FÖREGÅENDE liturgiska dag. Utan datumjämförelsen
        // skulle en vanlig tisdag få söndagens namn.
        assertThat(calendar.dayAt(LocalDate.of(2027, 2, 16)).hasName()).isFalse();
        assertThat(calendar.dayAt(LocalDate.of(2027, 7, 14)).hasName()).isFalse();
    }

    @Test
    void använder_kyrkoårets_namn_när_datumet_stämmer() {
        // Lär sig lektionarium namnge fler vardagar dyker de upp här av sig själva.
        var day = calendar.dayAt(LocalDate.of(2027, 3, 23));

        assertThat(day.name()).isEqualTo("Tisdag i Stilla veckan");
    }

    @Test
    void tomt_intervall_när_slutet_ligger_före_starten() {
        assertThat(calendar.serviceDaysBetween(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 1)))
                .isEmpty();
    }

    @Test
    void vägrar_år_som_apiet_inte_stödjer() {
        assertThatThrownBy(() ->
                        calendar.serviceDaysBetween(LocalDate.of(2003, 1, 1), LocalDate.of(2003, 3, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2004");
    }

    @Test
    void hämtar_kyrkoårets_namn_även_för_helgdagar() {
        var days = calendar.serviceDaysBetween(LocalDate.of(2027, 5, 17), LocalDate.of(2027, 5, 17));

        assertThat(days).singleElement().satisfies(day ->
                assertThat(day.name()).isEqualTo("Annandag pingst"));
    }
}
