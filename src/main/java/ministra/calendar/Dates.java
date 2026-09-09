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
    private static final DateTimeFormatter SHORT_FORM =
            DateTimeFormatter.ofPattern("d MMM", SWEDISH);

    private Dates() {}

    /** "9 oktober 2026" */
    public static String format(LocalDate date) {
        return LONG_FORM.format(date);
    }

    /** "9 okt." — för listan, där årtalet framgår av sammanhanget. */
    public static String formatShort(LocalDate date) {
        return SHORT_FORM.format(date);
    }
}
