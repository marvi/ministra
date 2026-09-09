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
    void includes_holy_days_that_are_not_sundays() {
        // Kyrkan firar gudstjänst även juldagen och annandagarna (D-040).
        var days = calendar.serviceDaysBetween(LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 31));

        assertThat(days).extracting(ServiceDay::name)
                .contains("Juldagen", "Annandag jul");
    }

    @Test
    void includes_whit_monday_and_good_friday() {
        var days = calendar.serviceDaysBetween(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31));

        assertThat(days).extracting(ServiceDay::name)
                .contains("Långfredagen", "Annandag påsk", "Annandag pingst",
                        "Kristi himmelsfärds dag", "Alla helgons dag");
    }

    @Test
    void omits_weekdays_without_a_service_of_their_own() {
        // Måndag till onsdag i Stilla veckan är OrdinaryDay i API:t och ska inte med.
        var days = calendar.serviceDaysBetween(LocalDate.of(2027, 3, 20), LocalDate.of(2027, 3, 26));

        assertThat(days).extracting(ServiceDay::name)
                .doesNotContain("Måndag i Stilla veckan", "Tisdag i Stilla veckan",
                        "Onsdag i Stilla veckan");
    }

    @Test
    void takes_the_church_year_name_from_the_api() {
        var days = calendar.serviceDaysBetween(LocalDate.of(2026, 11, 29), LocalDate.of(2026, 11, 29));

        assertThat(days).singleElement().satisfies(day ->
                assertThat(day.name()).isEqualTo("Första söndagen i advent"));
    }

    @Test
    void covers_every_sunday_even_across_a_year_boundary() {
        var from = LocalDate.of(2026, 12, 1);
        var to = LocalDate.of(2027, 2, 1);

        var days = calendar.serviceDaysBetween(from, to);

        var everySunday = from.datesUntil(to.plusDays(1))
                .filter(date -> date.getDayOfWeek() == DayOfWeek.SUNDAY)
                .toList();
        assertThat(days).extracting(ServiceDay::date).containsAll(everySunday);
    }

    @Test
    void is_in_date_order() {
        var days = calendar.serviceDaysBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 6, 30));

        assertThat(days).extracting(ServiceDay::date).isSorted();
    }

    @Test
    void names_an_arbitrary_date_only_when_the_api_really_knows_it() {
        // getCurrentDay ger närmast FÖREGÅENDE liturgiska dag. Utan datumjämförelsen
        // skulle en vanlig tisdag få söndagens namn.
        assertThat(calendar.dayAt(LocalDate.of(2027, 2, 16)).hasName()).isFalse();
        assertThat(calendar.dayAt(LocalDate.of(2027, 7, 14)).hasName()).isFalse();
    }

    @Test
    void uses_the_church_year_name_when_the_date_matches() {
        // Lär sig lektionarium namnge fler vardagar dyker de upp här av sig själva.
        var day = calendar.dayAt(LocalDate.of(2027, 3, 23));

        assertThat(day.name()).isEqualTo("Tisdag i Stilla veckan");
    }

    @Test
    void empty_range_when_end_is_before_start() {
        assertThat(calendar.serviceDaysBetween(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 1)))
                .isEmpty();
    }

    @Test
    void rejects_years_the_api_does_not_support() {
        assertThatThrownBy(() ->
                        calendar.serviceDaysBetween(LocalDate.of(2003, 1, 1), LocalDate.of(2003, 3, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2004");
    }

    @Test
    void takes_the_church_year_name_for_holy_days_too() {
        var days = calendar.serviceDaysBetween(LocalDate.of(2027, 5, 17), LocalDate.of(2027, 5, 17));

        assertThat(days).singleElement().satisfies(day ->
                assertThat(day.name()).isEqualTo("Annandag pingst"));
    }
}
