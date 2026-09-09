package ministra.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import ministra.calendar.ServiceDay;
import ministra.poll.Availability;
import ministra.poll.Poll;
import ministra.poll.PollView;
import ministra.poll.ServiceDayView;
import org.junit.jupiter.api.Test;

/** Ren JUnit. Vyn byggs ur listvyn, utan databas. */
class ScheduleViewTest {

    private static final LocalDate D1 = LocalDate.of(2026, 10, 4);
    private static final LocalDate D2 = LocalDate.of(2026, 10, 11);

    private static Poll poll() {
        return new Poll("SVARSTOKEN", "ADMINTOKEN", "Textläsare i höst", null,
                D1, D2, LocalDate.of(2026, 10, 1), "Markus", "markus@example.se");
    }

    private static PollView view(List<String> names, ServiceDayView... rows) {
        return new PollView(poll(), List.of(rows), names);
    }

    @Test
    void button_requires_three_responses() {
        assertThat(ScheduleView.canPropose(view(List.of("Frida", "Ola")))).isFalse();
        assertThat(ScheduleView.canPropose(view(List.of("Frida", "Ola", "Maja")))).isTrue();
    }

    @Test
    void per_day_outside_one_to_four_becomes_one() {
        assertThat(ScheduleView.clampPerDay(null)).isEqualTo(1);
        assertThat(ScheduleView.clampPerDay("0")).isEqualTo(1);
        assertThat(ScheduleView.clampPerDay("5")).isEqualTo(1);
        assertThat(ScheduleView.clampPerDay("-2")).isEqualTo(1);
        assertThat(ScheduleView.clampPerDay("skräp")).isEqualTo(1);
        assertThat(ScheduleView.clampPerDay(" 3 ")).isEqualTo(3);
        assertThat(ScheduleView.clampPerDay("4")).isEqualTo(4);
    }

    @Test
    void tiles_carry_the_persons_answer_for_that_day() {
        var view = view(
                List.of("Frida", "Ola"),
                new ServiceDayView(new ServiceDay(D1, "Den helige Mikaels dag"),
                        List.of("Frida"), List.of("Ola"), List.of()),
                new ServiceDayView(new ServiceDay(D2, "Tacksägelsedagen"),
                        List.of(), List.of("Ola"), List.of("Frida")));

        var schedule = ScheduleView.of(view, 1);

        assertThat(schedule.days().get(0).tiles())
                .containsExactly(new ScheduleView.Tile("Frida", Availability.CAN));
        assertThat(schedule.days().get(1).tiles())
                .containsExactly(new ScheduleView.Tile("Ola", Availability.IF_NEEDED));
    }

    @Test
    void people_keep_response_order_with_their_answers_and_counts() {
        var view = view(
                List.of("Ola", "Frida"),
                new ServiceDayView(new ServiceDay(D1, null), List.of("Frida", "Ola"), List.of(), List.of()),
                new ServiceDayView(new ServiceDay(D2, null), List.of("Frida"), List.of(), List.of("Ola")));

        var schedule = ScheduleView.of(view, 1);

        assertThat(schedule.people()).extracting(ScheduleView.Person::name)
                .containsExactly("Ola", "Frida");
        var ola = schedule.people().getFirst();
        assertThat(ola.answers()).containsEntry(D1, Availability.CAN).containsEntry(D2, Availability.CANNOT);
        assertThat(schedule.people()).extracting(ScheduleView.Person::count).containsExactly(1, 1);
    }

    @Test
    void a_day_nobody_can_take_is_empty() {
        var view = view(
                List.of("Frida"),
                new ServiceDayView(new ServiceDay(D1, null), List.of(), List.of(), List.of("Frida")));

        var schedule = ScheduleView.of(view, 2);

        assertThat(schedule.days().getFirst().isEmpty()).isTrue();
        assertThat(schedule.people().getFirst().count()).isZero();
    }
}
