package ministra.calendar;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lectio.cal.Day;
import lectio.cal.HolyDay;
import lectio.cal.LiturgicalYear;
import lectio.cal.LiturgicalYearFactory;
import org.springframework.stereotype.Component;

/**
 * Svenska kyrkans kyrkoår, via lektionarium-API:t.
 *
 * <p>Urvalet är alla dagar som firas med egen gudstjänst — inte bara söndagar. Juldagen,
 * annandagarna, Långfredagen, Kristi himmelsfärds dag, Midsommardagen och Alla helgons
 * dag behöver bemanning precis som en söndag (D-040).
 *
 * <p>API:t skiljer redan på {@link HolyDay} och {@code OrdinaryDay}. Vardagarna i Stilla
 * veckan är {@code OrdinaryDay} och faller därmed bort av sig själva. Varje söndag är en
 * {@code HolyDay} — verifierat för 2026 till 2030.
 */
@Component
public class ChurchCalendar {

    /** Fabriken cachar internt, så samma instans återanvänds. */
    private final LiturgicalYearFactory factory = new LiturgicalYearFactory();

    public static final int FIRST_SUPPORTED_YEAR = LiturgicalYear.FIRST_SUPPORTED_YEAR;

    /**
     * Alla gudstjänstdagar i intervallet, inklusive ändpunkterna, i datumordning.
     *
     * <p>En period som korsar ett årsskifte kräver ett anrop per kalenderår.
     *
     * @throws IllegalArgumentException om perioden börjar före det år API:t stödjer
     */
    public List<ServiceDay> serviceDaysBetween(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            return List.of();
        }
        if (from.getYear() < FIRST_SUPPORTED_YEAR) {
            throw new IllegalArgumentException(
                    "Kyrkoåret stöds från och med " + FIRST_SUPPORTED_YEAR + ", fick " + from.getYear());
        }
        var days = new ArrayList<ServiceDay>();
        for (int year = from.getYear(); year <= to.getYear(); year++) {
            for (Day day : factory.getDaysOfCalendarYear(year).values()) {
                var date = day.date();
                if (day instanceof HolyDay && !date.isBefore(from) && !date.isAfter(to)) {
                    days.add(new ServiceDay(date, day.name()));
                }
            }
        }
        days.sort(Comparator.comparing(ServiceDay::date));
        return List.copyOf(days);
    }

    /**
     * Kyrkoårets dag för ett godtyckligt datum, med namn om det finns ett.
     *
     * <p>{@code getCurrentDay} returnerar närmast <em>föregående</em> liturgiska dag, inte
     * den man frågar om — en tisdag i fastan ger tillbaka söndagen före. Därför jämförs
     * datumet i svaret med det efterfrågade, och namnet används bara när de stämmer.
     *
     * <p>Idag träffar det bara dagar API:t känner till, som "Tisdag i Stilla veckan".
     * Lär sig lektionarium namnge fler vardagar dyker de upp här av sig själva.
     */
    public ServiceDay dayAt(LocalDate date) {
        var day = factory.getCurrentDay(date);
        return day != null && date.equals(day.date())
                ? new ServiceDay(date, day.name())
                : new ServiceDay(date);
    }
}
