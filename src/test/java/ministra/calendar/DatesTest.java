package ministra.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DatesTest {

    @Test
    void long_forms_are_swedish() {
        assertThat(Dates.format(LocalDate.of(2026, 10, 9))).isEqualTo("9 oktober 2026");
        assertThat(Dates.formatWithWeekday(LocalDate.of(2026, 10, 9))).isEqualTo("fredag 9 oktober 2026");
    }

    @Test
    void short_form_has_no_trailing_period_after_the_month() {
        assertThat(Dates.formatShort(LocalDate.of(2026, 10, 4))).isEqualTo("sön 4 okt");
        assertThat(Dates.formatShort(LocalDate.of(2027, 5, 2))).isEqualTo("sön 2 maj");
    }
}
