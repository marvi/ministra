package ministra.poll;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import ministra.calendar.ChurchCalendar;
import ministra.calendar.Dates;
import ministra.calendar.ServiceDay;
import ministra.mail.MailTexts;
import ministra.mail.OutboxEmail;
import ministra.mail.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Skapar förfrågningar, tar emot svar och bygger listvyn. */
@Service
public class PollService {

    // Aldrig titel, namn, adresser eller tokens i loggen (AGENTS.md, punkt 8). Bara
    // databas-id, datum och antal.
    private static final Logger log = LoggerFactory.getLogger(PollService.class);

    /** Perioden får vara högst ett halvår (D-020). */
    public static final int MAX_PERIOD_MONTHS = 6;

    /** Hur långt fram skapa-formuläret erbjuder dagar att välja mellan. */
    private static final int OFFERED_MONTHS = 12;

    private final PollRepository polls;
    private final ParticipantRepository participants;
    private final ChurchCalendar calendar;
    private final OutboxRepository outbox;
    private final MailTexts texts;

    public PollService(
            PollRepository polls,
            ParticipantRepository participants,
            ChurchCalendar calendar,
            OutboxRepository outbox,
            MailTexts texts) {
        this.polls = polls;
        this.participants = participants;
        this.calendar = calendar;
        this.outbox = outbox;
        this.texts = texts;
    }

    // ---------- Skapa ----------

    /**
     * Dagarna som skapa-formuläret erbjuder som start och slut.
     *
     * <p>Man ska varken kunna välja ett datum utan gudstjänst eller en dag som redan
     * varit (D-036). Listan börjar därför dagen efter idag.
     */
    public List<ServiceDay> selectableDays(LocalDate today) {
        return calendar.serviceDaysBetween(today.plusDays(1), today.plusMonths(OFFERED_MONTHS));
    }

    /**
     * Kontrollerar perioden. Formuläret erbjuder bara framtida dagar, men det går att posta
     * förbi det.
     *
     * <p>Kravet på framtid gör kontrollen mot kyrkoårets första år överflödig: en dag i
     * framtiden ligger alltid efter 2004 (D-036).
     *
     * @throws SubmissionException med ett meddelande avsett för användaren
     */
    public void validate(CreatePollForm form, LocalDate today) {
        var start = form.startDate();
        var end = form.endDate();
        if (start == null || end == null) {
            throw new SubmissionException("Välj både en start- och en slutdag.");
        }
        if (!start.isAfter(today)) {
            throw new SubmissionException("Startdagen måste ligga i framtiden.");
        }
        if (end.isBefore(start)) {
            throw new SubmissionException("Slutdagen kan inte ligga före startdagen.");
        }
        if (start.plusMonths(MAX_PERIOD_MONTHS).isBefore(end)) {
            throw new SubmissionException(
                    "Perioden får vara högst " + MAX_PERIOD_MONTHS + " månader.");
        }
    }

    /** Alla gudstjänstdagar i formulärets period, innan skaparen valt bort något. */
    public List<ServiceDay> daysBetween(CreatePollForm form) {
        return calendar.serviceDaysBetween(form.startDate(), form.endDate());
    }

    /**
     * Dagarna som dagvalssidan visar: kyrkoårets dagar plus dem skaparen lagt till.
     *
     * <p>Kryssrutorna avgör sedan vilka som blir kvar. Det är därför {@link #create}
     * klarar sig med en enda mängd — bortvalda och tillagda faller ut ur jämförelsen mot
     * kyrkoåret.
     */
    public List<ServiceDay> candidateDays(CreatePollForm form, Set<LocalDate> added) {
        return withExtras(daysBetween(form), added);
    }

    /**
     * Kontrollerar ett datum som skaparen vill lägga till för hand (D-043).
     *
     * @throws SubmissionException med ett meddelande avsett för användaren
     */
    public void validateExtraDay(CreatePollForm form, Set<LocalDate> added, LocalDate date) {
        if (date == null) {
            throw new SubmissionException("Välj ett datum att lägga till.");
        }
        if (date.isBefore(form.startDate()) || date.isAfter(form.endDate())) {
            throw new SubmissionException(
                    "Datumet måste ligga i perioden "
                            + Dates.format(form.startDate())
                            + " – "
                            + Dates.format(form.endDate())
                            + ".");
        }
        if (candidateDays(form, added).stream().anyMatch(day -> day.date().equals(date))) {
            throw new SubmissionException("Den dagen finns redan i listan.");
        }
    }

    /**
     * Skapar förfrågan med de dagar skaparen behållit.
     *
     * <p>Urvalet görs innan förfrågan finns (D-042). Det som saknas i {@code kept} av
     * kyrkoårets dagar lagras som bortvalt, och det som finns i {@code kept} utan att vara
     * en kyrkoårsdag lagras som tillagt (D-043). Dagarna själva räknas fortfarande fram ur
     * kyrkoåret (D-018).
     */
    @Transactional
    public Poll create(CreatePollForm form, LocalDate today, Set<LocalDate> kept) {
        validate(form, today);

        // Datum utanför perioden räknas inte, oavsett hur de hamnat i formuläret.
        var inPeriod =
                kept.stream()
                        .filter(date -> !date.isBefore(form.startDate()))
                        .filter(date -> !date.isAfter(form.endDate()))
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        if (inPeriod.isEmpty()) {
            throw new SubmissionException("Minst en dag måste vara med i förfrågan.");
        }

        var churchDays =
                daysBetween(form).stream()
                        .map(ServiceDay::date)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        var excluded =
                churchDays.stream()
                        .filter(date -> !inPeriod.contains(date))
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        var extra =
                inPeriod.stream()
                        .filter(date -> !churchDays.contains(date))
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        var poll =
                new Poll(
                        Tokens.generate(),
                        Tokens.generate(),
                        form.title().strip(),
                        form.comment() == null || form.comment().isBlank()
                                ? null
                                : form.comment().strip(),
                        form.startDate(),
                        form.endDate(),
                        today.plusMonths(form.validMonths()),
                        form.creatorName().strip(),
                        form.creatorEmail().strip());
        poll.chooseDays(excluded, extra);
        polls.save(poll);

        // Samma transaktion som förfrågan. Går den tillbaka finns inget mejl att skicka.
        outbox.save(
                new OutboxEmail(
                        poll.getCreatorEmail(), texts.creationSubject(poll), texts.creationBody(poll)));
        log.info(
                "Skapade förfrågan {}: {} dagar {}–{}, giltig till {}",
                poll.getId(),
                inPeriod.size(),
                form.startDate(),
                form.endDate(),
                poll.getValidUntil());
        return poll;
    }

    // ---------- Slå upp ----------

    public Poll byResponseToken(String token) {
        return polls.findByResponseToken(token).orElseThrow(PollNotFoundException::new);
    }

    public Poll byAdminToken(String token) {
        return polls.findByAdminToken(token).orElseThrow(PollNotFoundException::new);
    }

    /**
     * Dagarna i förfrågan: kyrkoårets gudstjänstdagar minus de bortvalda, plus de
     * handpåsatta (D-018, D-043).
     */
    public List<ServiceDay> daysOf(Poll poll) {
        var churchDays =
                calendar.serviceDaysBetween(poll.getStartDate(), poll.getEndDate()).stream()
                        .filter(day -> !poll.isExcluded(day.date()))
                        .toList();
        return withExtras(churchDays, poll.getExtraDays());
    }

    /** Lägger till de datum som inte redan finns, slår upp deras namn och sorterar. */
    private List<ServiceDay> withExtras(List<ServiceDay> churchDays, Set<LocalDate> extras) {
        var known = churchDays.stream().map(ServiceDay::date).collect(Collectors.toSet());
        var days = new ArrayList<>(churchDays);
        extras.stream()
                .filter(date -> !known.contains(date))
                .map(calendar::dayAt)
                .forEach(days::add);
        days.sort(Comparator.comparing(ServiceDay::date));
        return List.copyOf(days);
    }

    // ---------- Svara ----------

    /**
     * Tar emot ett svar.
     *
     * <p>Alla dagar måste besvaras (D-019), varje datum måste vara en dag som ingår i
     * förfrågan (D-018, D-042, D-043), och namnet måste vara ledigt (D-009).
     */
    @Transactional
    public Participant submit(Poll detached, String rawName, Map<LocalDate, Availability> answers) {
        // Förfrågan kommer från en tidigare, avslutad transaktion och är frånkopplad.
        // Utan den här omläsningen går det inte att röra dess deltagarsamling.
        var poll = managed(detached);

        var name = rawName == null ? "" : rawName.strip();
        if (name.isEmpty()) {
            throw new SubmissionException("Skriv ditt namn.");
        }
        if (name.length() > 80) {
            throw new SubmissionException("Namnet får vara högst 80 tecken.");
        }
        if (participants.existsByPollAndNameKey(poll, Participant.normalize(name))) {
            throw new SubmissionException(
                    "Det finns redan ett svar från \""
                            + name
                            + "\". Lägg till en bokstav så att det går att skilja er åt, till"
                            + " exempel \""
                            + name
                            + " J\".");
        }

        // Bara förfrågans egna dagar plockas ur svaret. Datum därutöver ignoreras tyst.
        var participant = new Participant(poll, name);
        for (var day : daysOf(poll)) {
            var availability = answers.get(day.date());
            if (availability == null) {
                throw new SubmissionException("Alla dagar måste besvaras.");
            }
            participant.addResponse(new Response(participant, day.date(), availability));
        }
        try {
            poll.addParticipant(participant);
            var saved = participants.save(participant);
            log.info(
                    "Nytt svar på förfrågan {}: {} dagar besvarade, {} svar totalt",
                    poll.getId(),
                    saved.getResponses().size(),
                    participants.countByPoll(poll));
            return saved;
        } catch (DataIntegrityViolationException e) {
            // Två svar med samma namn samtidigt. Databasen är den som avgör.
            throw new SubmissionException(
                    "Det finns redan ett svar från \"" + name + "\". Välj ett annat namn.");
        }
    }

    /** Bygger listan: en rad per gudstjänstdag, med namnen grupperade per svar. */
    @Transactional(readOnly = true)
    public PollView view(Poll poll) {
        var days = daysOf(poll);
        var all = participants.findWithResponses(poll);

        Map<LocalDate, List<String>> can = new LinkedHashMap<>();
        Map<LocalDate, List<String>> ifNeeded = new LinkedHashMap<>();
        Map<LocalDate, List<String>> cannot = new LinkedHashMap<>();
        for (var day : days) {
            can.put(day.date(), new ArrayList<>());
            ifNeeded.put(day.date(), new ArrayList<>());
            cannot.put(day.date(), new ArrayList<>());
        }

        var names = new ArrayList<String>();
        for (var participant : all) {
            names.add(participant.getName());
            for (var response : participant.getResponses()) {
                var bucket =
                        switch (response.getAvailability()) {
                            case CAN -> can;
                            case IF_NEEDED -> ifNeeded;
                            case CANNOT -> cannot;
                        };
                // Dagarna är låsta vid skapandet (D-042), så varje svar borde ha en hink.
                // Vakten finns för data som skrivits före en framtida ändring av reglerna.
                var list = bucket.get(response.getServiceDate());
                if (list != null) {
                    list.add(participant.getName());
                }
            }
        }

        var rows = new ArrayList<ServiceDayView>(days.size());
        for (var day : days) {
            rows.add(
                    new ServiceDayView(
                            day,
                            List.copyOf(can.get(day.date())),
                            List.copyOf(ifNeeded.get(day.date())),
                            List.copyOf(cannot.get(day.date()))));
        }
        return new PollView(poll, List.copyOf(rows), List.copyOf(names));
    }

    // ---------- Radera ----------

    @Transactional
    public void delete(Poll detached) {
        // Samma skäl som i submit: kaskaden behöver en förfrågan som sessionen känner till.
        polls.findById(detached.getId())
                .ifPresent(
                        poll -> {
                            var count = participants.countByPoll(poll);
                            polls.delete(poll);
                            log.info(
                                    "Skaparen raderade förfrågan {} med {} svar", poll.getId(), count);
                        });
    }

    private Poll managed(Poll detached) {
        return polls.findById(detached.getId()).orElseThrow(PollNotFoundException::new);
    }
}
