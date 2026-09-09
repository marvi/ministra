package ministra.calendar;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Svensk datumformatering. All text mot användaren är svensk, även i mejlen.
 *
 * <p>Ligger i kalenderpaketet och inte i webblagret, eftersom både domänen och
 * mejltexterna behöver den. Ett paket som är en löv i beroendegrafen kan alla luta sig
 * mot utan att det blir en cirkel.
 */
public final class Dates {

    public static final Locale SWEDISH = Locale.of("sv", "SE");

    private static final DateTimeFormatter LONG_FORM =
            DateTimeFormatter.ofPattern("d MMMM yyyy", SWEDISH);
    private static final DateTimeFormatter WITH_WEEKDAY =
            DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", SWEDISH);

    private Dates() {}

    /** "9 oktober 2026" */
    public static String format(LocalDate date) {
        return LONG_FORM.format(date);
    }

    /**
     * "fredag 9 oktober 2026" — för daglistorna. Folk planerar i veckodagar, och en dag
     * utan kyrkoårsnamn bär sig själv med veckodag och fullt datum (D-044).
     */
    public static String formatWithWeekday(LocalDate date) {
        return WITH_WEEKDAY.format(date);
    }
}
