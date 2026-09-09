package ministra.schedule;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import ministra.poll.Availability;
import org.jspecify.annotations.Nullable;

/**
 * Fördelar dem som svarat på dagarna (D-046). Ren funktion: samma indata ger alltid samma
 * förslag, ingen slump, ingen Spring.
 *
 * <p>Reglerna i prioritetsordning:
 *
 * <ol>
 *   <li><b>Aldrig "kan inte".</b> Hellre en tom plats.
 *   <li><b>Grönt före gult, alltid.</b> En gul plats fylls bara när ingen grön finns kvar
 *       för dagen, oavsett rättvisa och spridning.
 *   <li><b>Rättvist antal</b> bland de gröna: den som har färst dagar hittills får nästa.
 *       Vid lika: den som har längst till sin närmaste dag.
 *   <li><b>Utspritt</b> bland de gröna: inte två gudstjänstdagar i rad om det går att
 *       undvika med en annan grön, och i övrigt så långt mellan en persons dagar som
 *       möjligt. Regeln viker när svaren inte räcker, men aldrig till gult.
 *   <li><b>Knappa dagar först:</b> dagarna fylls i ordning efter hur få gröna som finns,
 *       inte kronologiskt. Utdata är kronologisk ändå.
 * </ol>
 *
 * <p>Två pass: först fylls allt som går att fylla med gröna, sedan resten med gula. En dag
 * som saknar gröna avgörs alltså sist, när det syns vem de gröna dagarna redan tagit i
 * anspråk. Annars skulle den som är ensam grön en dag kunna få även den gula dagen intill,
 * bara för att den fylldes först.
 *
 * <p>Lika-fall avgörs av svarsordningen, aldrig av namn i bokstavsordning, så att inte
 * "Anna" alltid får mer än "Åke".
 */
public final class Scheduler {

    /** En person som svarat, med sitt svar per dag. En dag som saknas räknas som "kan inte". */
    public record Participant(String name, Map<LocalDate, Availability> answers) {

        public Participant {
            answers = Map.copyOf(answers);
        }

        Availability on(LocalDate date) {
            return answers.getOrDefault(date, Availability.CANNOT);
        }
    }

    private Scheduler() {}

    /**
     * Ger förslaget: varje dag i {@code days} med upp till {@code perDay} namn, i den
     * ordning de valdes. En dag ingen kan blir tom.
     *
     * @param days gudstjänstdagarna, i vilken ordning som helst
     * @param participants de som svarat, i svarsordning
     * @param perDay hur många som behövs per dag, minst 1
     */
    public static SortedMap<LocalDate, List<String>> propose(
            List<LocalDate> days, List<Participant> participants, int perDay) {
        if (perDay < 1) {
            throw new IllegalArgumentException("perDay måste vara minst 1, var " + perDay);
        }
        var sortedDays = days.stream().distinct().sorted().toList();
        var state = new State(sortedDays, participants);

        state.fill(byScarcity(sortedDays, state, Availability.CAN), perDay, Availability.CAN);
        state.fill(byScarcity(sortedDays, state, Availability.IF_NEEDED), perDay, Availability.IF_NEEDED);
        return state.result();
    }

    /** Dagarna med färst som svarat {@code availability} först; lika avgörs av datumet. */
    private static List<LocalDate> byScarcity(
            List<LocalDate> days, State state, Availability availability) {
        return days.stream()
                .sorted(
                        Comparator.comparingInt((LocalDate d) -> state.count(d, availability))
                                .thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    /** Arbetsläget under en körning: vem som ligger var, och vad det kostar att lägga till. */
    private static final class State {
        private final List<LocalDate> days;
        private final Map<LocalDate, Integer> index = new HashMap<>();
        private final List<Participant> participants;
        private final SortedMap<LocalDate, List<String>> assigned = new TreeMap<>();
        private final Map<String, List<LocalDate>> daysOf = new HashMap<>();

        State(List<LocalDate> days, List<Participant> participants) {
            this.days = days;
            this.participants = participants;
            for (var i = 0; i < days.size(); i++) {
                index.put(days.get(i), i);
                assigned.put(days.get(i), new ArrayList<>());
            }
            for (var p : participants) {
                daysOf.put(p.name(), new ArrayList<>());
            }
        }

        /** Fyller de platser som är lediga på varje dag med dem som svarat {@code availability}. */
        void fill(List<LocalDate> days, int perDay, Availability availability) {
            for (var day : days) {
                while (assigned.get(day).size() < perDay) {
                    var pick = pick(day, availability);
                    if (pick == null) {
                        break;
                    }
                    assign(day, pick);
                }
            }
        }

        int count(LocalDate day, Availability availability) {
            var n = 0;
            for (var p : participants) {
                if (p.on(day) == availability) {
                    n++;
                }
            }
            return n;
        }

        /**
         * Den bästa kandidaten bland dem som svarat {@code availability} för dagen och inte
         * redan ligger där, eller {@code null} om ingen finns. Först ratas de som skulle
         * hamna två gudstjänstdagar i rad, om det lämnar någon kvar. Sedan vinner färst
         * dagar, därefter längst avstånd till närmaste egna dag, och sist svarsordningen.
         */
        private @Nullable Participant pick(LocalDate day, Availability availability) {
            var pool = new ArrayList<Participant>();
            for (var p : participants) {
                if (p.on(day) == availability && !assigned.get(day).contains(p.name())) {
                    pool.add(p);
                }
            }
            if (pool.isEmpty()) {
                return null;
            }
            var spread = pool.stream().filter(p -> !adjacent(p, day)).toList();
            if (!spread.isEmpty()) {
                pool = new ArrayList<>(spread);
            }

            Participant best = pool.getFirst();
            for (var p : pool.subList(1, pool.size())) {
                if (better(p, best, day)) {
                    best = p;
                }
            }
            return best;
        }

        /** Strikt bättre: färre dagar, eller lika många och längre till närmaste egna dag. */
        private boolean better(Participant candidate, Participant current, LocalDate day) {
            var candidateCount = daysOf.get(candidate.name()).size();
            var currentCount = daysOf.get(current.name()).size();
            if (candidateCount != currentCount) {
                return candidateCount < currentCount;
            }
            return distance(candidate, day) > distance(current, day);
        }

        /** Ligger personen redan på gudstjänstdagen före eller efter? */
        private boolean adjacent(Participant p, LocalDate day) {
            var i = index.get(day);
            return (i > 0 && assigned.get(days.get(i - 1)).contains(p.name()))
                    || (i < days.size() - 1 && assigned.get(days.get(i + 1)).contains(p.name()));
        }

        /** Kalenderdagar till personens närmaste egna dag, eller "oändligt" om hen inte har någon. */
        private long distance(Participant p, LocalDate day) {
            var nearest = Long.MAX_VALUE;
            for (var own : daysOf.get(p.name())) {
                nearest = Math.min(nearest, Math.abs(ChronoUnit.DAYS.between(own, day)));
            }
            return nearest;
        }

        void assign(LocalDate day, Participant p) {
            assigned.get(day).add(p.name());
            daysOf.get(p.name()).add(day);
        }

        SortedMap<LocalDate, List<String>> result() {
            var out = new TreeMap<LocalDate, List<String>>();
            assigned.forEach((day, names) -> out.put(day, List.copyOf(names)));
            return out;
        }
    }
}
