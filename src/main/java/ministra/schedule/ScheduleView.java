package ministra.schedule;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ministra.calendar.ServiceDay;
import ministra.poll.Availability;
import ministra.poll.Poll;
import ministra.poll.PollView;
import ministra.schedule.Scheduler.Participant;
import org.jspecify.annotations.Nullable;

/**
 * Arbetsbladet som det renderas (D-046): dagarna med sina brickor, brickraden med
 * räknare, och varje persons svar per dag så att skriptet kan färga en bricka rätt när
 * den landar på en ny dag utan att fråga servern.
 *
 * <p>Byggs ur samma vy som listan, {@link PollView}. Inget nytt hämtas ur databasen och
 * ingenting sparas.
 */
public record ScheduleView(Poll poll, int perDay, List<Day> days, List<Person> people) {

    /** Knappen visas först när det finns något att fördela. */
    public static final int MIN_RESPONSES = 3;

    /** Väljaren "Personer per dag" går från 1 till detta. */
    public static final int MAX_PER_DAY = 4;

    /** En dag på arbetsbladet: dagen själv och brickorna som ligger på den. */
    public record Day(ServiceDay day, List<Tile> tiles) {
        public boolean isEmpty() {
            return tiles.isEmpty();
        }
    }

    /** En bricka på en dag. Färgen är personens svar för just den dagen; rött förekommer aldrig. */
    public record Tile(String name, Availability availability) {}

    /** En person i brickraden: namnet, svaren per dag, och hur många dagar hen har i förslaget. */
    public record Person(String name, Map<LocalDate, Availability> answers, int count) {
        public Person {
            answers = Map.copyOf(answers);
        }
    }

    /** Finns det tillräckligt många svar för att ett förslag ska vara meningsfullt? */
    public static boolean canPropose(PollView view) {
        return view.participantCount() >= MIN_RESPONSES;
    }

    /**
     * Tolkar {@code perDag} från adressen. Saknas den, är skräp, eller ligger utanför
     * 1–{@value #MAX_PER_DAY}, blir det 1: förvalet, inte ett fel.
     */
    public static int clampPerDay(@Nullable String raw) {
        if (raw == null) {
            return 1;
        }
        try {
            var n = Integer.parseInt(raw.trim());
            return n >= 1 && n <= MAX_PER_DAY ? n : 1;
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /** Räknar fram förslaget och sätter ihop arbetsbladet. */
    public static ScheduleView of(PollView view, int perDay) {
        var answers = new HashMap<String, Map<LocalDate, Availability>>();
        for (var name : view.participantNames()) {
            answers.put(name, new HashMap<>());
        }
        for (var row : view.days()) {
            record(answers, row.can(), row.day().date(), Availability.CAN);
            record(answers, row.ifNeeded(), row.day().date(), Availability.IF_NEEDED);
            record(answers, row.cannot(), row.day().date(), Availability.CANNOT);
        }

        var participants = new ArrayList<Participant>();
        for (var name : view.participantNames()) {
            participants.add(new Participant(name, answers.get(name)));
        }
        var dates = view.days().stream().map(row -> row.day().date()).toList();
        var proposal = Scheduler.propose(dates, participants, perDay);

        var counts = new HashMap<String, Integer>();
        var days = new ArrayList<Day>(view.days().size());
        for (var row : view.days()) {
            var tiles = new ArrayList<Tile>();
            for (var name : proposal.get(row.day().date())) {
                tiles.add(new Tile(name, answers.get(name).get(row.day().date())));
                counts.merge(name, 1, Integer::sum);
            }
            days.add(new Day(row.day(), List.copyOf(tiles)));
        }

        var people = new ArrayList<Person>();
        for (var name : view.participantNames()) {
            people.add(new Person(name, answers.get(name), counts.getOrDefault(name, 0)));
        }
        return new ScheduleView(view.poll(), perDay, List.copyOf(days), List.copyOf(people));
    }

    private static void record(
            Map<String, Map<LocalDate, Availability>> answers,
            List<String> names,
            LocalDate date,
            Availability availability) {
        for (var name : names) {
            // Vakten finns för ett namn som svarat utan att stå i deltagarlistan; det ska
            // inte kunna hända, men får inte fälla sidan om det gör det.
            answers.computeIfAbsent(name, n -> new HashMap<>()).put(date, availability);
        }
    }
}
