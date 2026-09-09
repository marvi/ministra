package ministra.schedule;

import static ministra.poll.Availability.CAN;
import static ministra.poll.Availability.CANNOT;
import static ministra.poll.Availability.IF_NEEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import ministra.poll.Availability;
import ministra.schedule.Scheduler.Participant;
import org.junit.jupiter.api.Test;

/** Ren JUnit. Algoritmen är en funktion av svaren, så ingen databas behövs. */
class SchedulerTest {

    private static final LocalDate FIRST_SUNDAY = LocalDate.of(2026, 10, 4);

    @Test
    void never_places_anyone_on_a_day_they_cannot() {
        var days = sundays(6);
        var people = List.of(
                person("Frida", days, CAN, CANNOT, CAN, CANNOT, CAN, CANNOT),
                person("Ola", days, CANNOT, CAN, CANNOT, CAN, CANNOT, CAN),
                person("Maja", days, CANNOT, CANNOT, CANNOT, CANNOT, CANNOT, CANNOT));

        var plan = Scheduler.propose(days, people, 2);

        for (var day : days) {
            for (var name : plan.get(day)) {
                assertThat(availabilityOf(people, name, day)).isNotEqualTo(CANNOT);
            }
        }
        assertThat(plan.values()).flatMap(names -> names).doesNotContain("Maja");
    }

    @Test
    void shares_days_fairly_when_everyone_can() {
        var days = sundays(10);
        var people = List.of(
                allDays("Frida", days, CAN),
                allDays("Ola", days, CAN),
                allDays("Maja", days, CAN));

        var plan = Scheduler.propose(days, people, 1);

        var counts = counts(plan);
        assertThat(counts).containsKeys("Frida", "Ola", "Maja");
        assertThat(Collections.max(counts.values()) - Collections.min(counts.values()))
                .isLessThanOrEqualTo(1);
    }

    @Test
    void avoids_consecutive_days_when_another_green_exists() {
        var days = sundays(8);
        var people = List.of(
                allDays("Frida", days, CAN),
                allDays("Ola", days, CAN));

        var plan = Scheduler.propose(days, people, 1);

        for (var i = 1; i < days.size(); i++) {
            assertThat(plan.get(days.get(i))).doesNotContainAnyElementsOf(plan.get(days.get(i - 1)));
        }
    }

    @Test
    void uses_yellow_only_when_no_green_is_left() {
        // Frida kan alla dagar; Ola kan bara om det behövs. Frida får allt, även om det
        // betyder att hon går varje söndag och har flest dagar.
        var days = sundays(4);
        var people = List.of(
                allDays("Frida", days, CAN),
                allDays("Ola", days, IF_NEEDED));

        var plan = Scheduler.propose(days, people, 1);

        assertThat(plan.values()).allSatisfy(names -> assertThat(names).containsExactly("Frida"));
    }

    @Test
    void takes_the_busiest_green_before_a_yellow() {
        // Ola kan bara dag 1. Frida kan dag 1–4. Maja kan om det behövs alla dagar.
        // Dag 2–4 ska bli Fridas trots att hon redan har flest och gick i går.
        var days = sundays(4);
        var people = List.of(
                person("Frida", days, CAN, CAN, CAN, CAN),
                person("Ola", days, CAN, CANNOT, CANNOT, CANNOT),
                allDays("Maja", days, IF_NEEDED));

        var plan = Scheduler.propose(days, people, 1);

        assertThat(plan.get(days.get(1))).containsExactly("Frida");
        assertThat(plan.get(days.get(2))).containsExactly("Frida");
        assertThat(plan.get(days.get(3))).containsExactly("Frida");
        assertThat(plan.values()).flatMap(names -> names).doesNotContain("Maja");
    }

    @Test
    void falls_back_to_yellow_when_greens_are_used_up() {
        // Två per dag, en grön och en gul: båda får plats, gul sist.
        var days = sundays(1);
        var people = List.of(
                allDays("Ola", days, IF_NEEDED),
                allDays("Frida", days, CAN));

        var plan = Scheduler.propose(days, people, 2);

        assertThat(plan.get(days.getFirst())).containsExactly("Frida", "Ola");
    }

    @Test
    void leaves_a_day_empty_when_nobody_can() {
        var days = sundays(2);
        var people = List.of(
                person("Frida", days, CAN, CANNOT),
                person("Ola", days, CAN, CANNOT));

        var plan = Scheduler.propose(days, people, 1);

        assertThat(plan.get(days.get(0))).hasSize(1);
        assertThat(plan.get(days.get(1))).isEmpty();
    }

    @Test
    void fills_two_per_day_where_possible_and_one_where_only_one_can() {
        var days = sundays(2);
        var people = List.of(
                person("Frida", days, CAN, CAN),
                person("Ola", days, CAN, CANNOT),
                person("Maja", days, CANNOT, CANNOT));

        var plan = Scheduler.propose(days, people, 2);

        assertThat(plan.get(days.get(0))).containsExactlyInAnyOrder("Frida", "Ola");
        assertThat(plan.get(days.get(1))).containsExactly("Frida");
    }

    @Test
    void never_puts_the_same_person_twice_on_one_day() {
        var days = sundays(1);
        var people = List.of(allDays("Frida", days, CAN));

        var plan = Scheduler.propose(days, people, 4);

        assertThat(plan.get(days.getFirst())).containsExactly("Frida");
    }

    @Test
    void fills_scarce_days_first() {
        // Dag 3 kan bara Frida ta. Hon ska få den, och sedan inte "kosta" så att Ola och
        // Maja täcker resten — utan att Frida ändå hamnar på dag 2 eller 4 intill.
        var days = sundays(5);
        var people = List.of(
                allDays("Frida", days, CAN),
                person("Ola", days, CAN, CAN, CANNOT, CAN, CAN),
                person("Maja", days, CAN, CAN, CANNOT, CAN, CAN));

        var plan = Scheduler.propose(days, people, 1);

        assertThat(plan.get(days.get(2))).containsExactly("Frida");
        assertThat(plan.get(days.get(1))).doesNotContain("Frida");
        assertThat(plan.get(days.get(3))).doesNotContain("Frida");
        var counts = counts(plan);
        assertThat(Collections.max(counts.values()) - Collections.min(counts.values()))
                .isLessThanOrEqualTo(1);
    }

    @Test
    void breaks_ties_by_response_order_not_by_name() {
        // "Åke" svarade först och ska få första dagen före "Anna".
        var days = sundays(1);
        var people = List.of(allDays("Åke", days, CAN), allDays("Anna", days, CAN));

        var plan = Scheduler.propose(days, people, 1);

        assertThat(plan.get(days.getFirst())).containsExactly("Åke");
    }

    @Test
    void is_deterministic() {
        var days = sundays(12);
        var random = new Random(42);
        var people = new ArrayList<Participant>();
        for (var i = 0; i < 6; i++) {
            var answers = new HashMap<LocalDate, Availability>();
            for (var day : days) {
                answers.put(day, Availability.values()[random.nextInt(3)]);
            }
            people.add(new Participant("P" + i, answers));
        }

        var first = Scheduler.propose(days, people, 2);
        var second = Scheduler.propose(days, people, 2);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void treats_a_missing_answer_as_cannot() {
        var days = sundays(2);
        var people = List.of(new Participant("Frida", Map.of(days.get(0), CAN)));

        var plan = Scheduler.propose(days, people, 1);

        assertThat(plan.get(days.get(0))).containsExactly("Frida");
        assertThat(plan.get(days.get(1))).isEmpty();
    }

    @Test
    void output_is_chronological_and_covers_every_day() {
        var days = new ArrayList<>(sundays(4));
        Collections.reverse(days);
        var plan = Scheduler.propose(days, List.of(), 1);

        assertThat(plan.keySet()).containsExactlyElementsOf(sundays(4));
        assertThat(plan.values()).allSatisfy(names -> assertThat(names).isEmpty());
    }

    @Test
    void rejects_per_day_below_one() {
        assertThatThrownBy(() -> Scheduler.propose(sundays(1), List.of(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- Hjälpare ----------

    private static List<LocalDate> sundays(int n) {
        var days = new ArrayList<LocalDate>();
        for (var i = 0; i < n; i++) {
            days.add(FIRST_SUNDAY.plusWeeks(i));
        }
        return days;
    }

    private static Participant person(String name, List<LocalDate> days, Availability... answers) {
        var map = new HashMap<LocalDate, Availability>();
        for (var i = 0; i < days.size(); i++) {
            map.put(days.get(i), answers[i]);
        }
        return new Participant(name, map);
    }

    private static Participant allDays(String name, List<LocalDate> days, Availability answer) {
        var map = new HashMap<LocalDate, Availability>();
        days.forEach(day -> map.put(day, answer));
        return new Participant(name, map);
    }

    private static Map<String, Integer> counts(Map<LocalDate, List<String>> plan) {
        var counts = new HashMap<String, Integer>();
        plan.values().forEach(names -> names.forEach(name -> counts.merge(name, 1, Integer::sum)));
        return counts;
    }

    private static Availability availabilityOf(List<Participant> people, String name, LocalDate day) {
        return people.stream().filter(p -> p.name().equals(name)).findFirst().orElseThrow().on(day);
    }
}
