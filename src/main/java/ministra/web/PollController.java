package ministra.web;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HxRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ministra.MinistraProperties;
import ministra.mail.MailTexts;
import ministra.poll.Availability;
import ministra.poll.CreatePollForm;
import ministra.poll.Poll;
import ministra.poll.PollNotFoundException;
import ministra.poll.PollService;
import ministra.poll.SubmissionException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Fyra vyer: skapa-formuläret, dagvalet, svarsvyn och admin-vyn. Admin-vyn är svarsvyn
 * med länkar och knappar ovanpå, inte en egen lista (D-027).
 *
 * <p>Skapandet sker i två steg och sparar först i det andra (D-042).
 */
@Controller
public class PollController {

    private final PollService polls;
    private final MailTexts texts;
    private final MinistraProperties properties;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    public PollController(
            PollService polls,
            MailTexts texts,
            MinistraProperties properties,
            RateLimiter rateLimiter,
            Clock clock) {
        this.polls = polls;
        this.texts = texts;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    // ---------- Skapa ----------

    @GetMapping("/")
    public String createForm(Model model) {
        return backToCreateForm(model, emptyForm(), Map.of());
    }

    /**
     * Steg två: visa dagarna och låt skaparen välja bort dem som inte ska med.
     *
     * <p>Ingenting sparas här. Förfrågan skapas först i {@link #save}, så fälten följer
     * med som dolda inputs (D-042).
     */
    @PostMapping("/skapa")
    public String chooseDays(@Valid CreatePollForm form, BindingResult binding, Model model) {
        var errors = fieldErrors(binding);
        if (errors.isEmpty()) {
            try {
                polls.validate(form, today());
                return dayChoice(model, form, Set.of(), null, null);
            } catch (SubmissionException e) {
                errors.put("startDate", e.getMessage());
            }
        }
        return backToCreateForm(model, form, errors);
    }

    /**
     * Lägger till ett datum som kyrkoåret inte har. En församling kan fira ett lokalt
     * helgon eller en egen högtid (D-043).
     *
     * <p>Med htmx byts bara dagvalet ut; utan renderas hela sidan om. Allt som redan är
     * ikryssat följer med tillbaka i båda fallen.
     */
    @HxRequest
    @PostMapping("/skapa/dagar")
    public String addDayFragment(
            @Valid CreatePollForm form,
            BindingResult binding,
            @RequestParam(name = "day", required = false) List<String> keptDays,
            @RequestParam(name = "added", required = false) List<String> addedDays,
            @RequestParam(name = "extraDate", required = false) String extraDate,
            Model model) {
        var view = addDay(form, binding, keptDays, addedDays, extraDate, model);
        // Bara dagvalet finns som fragment. Hamnar vi på skapa-formuläret — vilket kräver
        // att de dolda fälten manipulerats — renderas det hela, precis som utan htmx.
        return "days".equals(view) ? "dayschoice" : view;
    }

    @PostMapping("/skapa/dagar")
    public String addDay(
            @Valid CreatePollForm form,
            BindingResult binding,
            @RequestParam(name = "day", required = false) List<String> keptDays,
            @RequestParam(name = "added", required = false) List<String> addedDays,
            @RequestParam(name = "extraDate", required = false) String extraDate,
            Model model) {

        var errors = fieldErrors(binding);
        if (!errors.isEmpty()) {
            return backToCreateForm(model, form, errors);
        }

        var kept = parseDates(keptDays);
        var added = parseDates(addedDays);
        var date = extraDate == null || extraDate.isBlank() ? null : parseDate(extraDate);
        try {
            polls.validateExtraDay(form, added, date);
        } catch (SubmissionException e) {
            return dayChoice(model, form, added, kept, e.getMessage());
        }

        // Den nya dagen är ikryssad från början — man lade ju till den med flit.
        added.add(date);
        kept.add(date);
        return dayChoice(model, form, added, kept, null);
    }

    /** Steg tre: skapa förfrågan med de dagar som är kvar. */
    @PostMapping("/skapa/spara")
    public String save(
            @Valid CreatePollForm form,
            BindingResult binding,
            @RequestParam(name = "day", required = false) List<String> keptDays,
            @RequestParam(name = "added", required = false) List<String> addedDays,
            HttpServletRequest request,
            Model model) {

        var errors = fieldErrors(binding);
        if (!errors.isEmpty()) {
            return backToCreateForm(model, form, errors);
        }

        var kept = parseDates(keptDays);
        var added = parseDates(addedDays);
        if (!rateLimiter.tryAcquire(RateLimiter.clientIp(request), clock.instant())) {
            return dayChoice(
                    model,
                    form,
                    added,
                    kept,
                    "För många förfrågningar har skapats härifrån den senaste timmen."
                            + " Försök igen om en stund.");
        }
        try {
            return "redirect:/a/" + polls.create(form, today(), kept).getAdminToken();
        } catch (SubmissionException e) {
            return dayChoice(model, form, added, kept, e.getMessage());
        }
    }

    private String backToCreateForm(
            Model model, CreatePollForm form, Map<String, String> errors) {
        model.addAttribute("days", polls.selectableDays(today()));
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        return "create";
    }

    /**
     * Renderar dagvalet. {@code kept} är {@code null} första gången, och då är allt
     * ikryssat.
     */
    private String dayChoice(
            Model model,
            CreatePollForm form,
            Set<LocalDate> added,
            Set<LocalDate> kept,
            String error) {
        model.addAttribute("form", form);
        model.addAttribute("days", polls.candidateDays(form, added));
        model.addAttribute("added", added);
        model.addAttribute("kept", kept);
        model.addAttribute("error", error);
        return "days";
    }

    private static Map<String, String> fieldErrors(BindingResult binding) {
        var errors = new LinkedHashMap<String, String>();
        binding.getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return errors;
    }

    private static LocalDate parseDate(String raw) {
        try {
            return LocalDate.parse(raw);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Skräp i formuläret räknas som en dag som inte är med. */
    private static Set<LocalDate> parseDates(List<String> raw) {
        var dates = new LinkedHashSet<LocalDate>();
        for (var value : raw == null ? List.<String>of() : raw) {
            var date = parseDate(value);
            if (date != null) {
                dates.add(date);
            }
        }
        return dates;
    }

    // ---------- Svara ----------

    @GetMapping("/s/{token}")
    public String respond(@PathVariable String token, Model model) {
        var poll = polls.byResponseToken(token);
        model.addAttribute("view", polls.view(poll));
        model.addAttribute("admin", false);
        model.addAttribute("prefilledName", "");
        model.addAttribute("error", null);
        model.addAttribute("submitPath", "/s/" + token);
        return "poll";
    }

    @PostMapping("/s/{token}")
    public String submit(
            @PathVariable String token,
            @RequestParam String name,
            @RequestParam Map<String, String> allParams,
            Model model) {
        var poll = polls.byResponseToken(token);
        return submitTo(poll, name, allParams, model, false, "/s/" + token);
    }

    // ---------- Administrera ----------

    @GetMapping("/a/{token}")
    public String admin(@PathVariable String token, Model model) {
        var poll = polls.byAdminToken(token);
        addAdminAttributes(model, poll);
        model.addAttribute("view", polls.view(poll));
        model.addAttribute("prefilledName", poll.getCreatorName());
        model.addAttribute("error", null);
        return "poll";
    }

    @PostMapping("/a/{token}/svar")
    public String submitAsAdmin(
            @PathVariable String token,
            @RequestParam String name,
            @RequestParam Map<String, String> allParams,
            Model model) {
        var poll = polls.byAdminToken(token);
        return submitTo(poll, name, allParams, model, true, "/a/" + token);
    }

    @PostMapping("/a/{token}/radera")
    public String delete(@PathVariable String token) {
        polls.delete(polls.byAdminToken(token));
        return "redirect:/raderad";
    }

    @GetMapping("/raderad")
    public String deleted() {
        return "deleted";
    }

    // ---------- Gemensamt ----------

    /**
     * Tar emot ett svar och skickar vidare till {@code path} om det gick bra. Annars
     * renderas samma vy igen med felet. För admin-vyn sätter {@link #addAdminAttributes}
     * både länkarna och svarsadressen; för svarsvyn är svarsadressen densamma som
     * {@code path}.
     */
    private String submitTo(
            Poll poll,
            String name,
            Map<String, String> allParams,
            Model model,
            boolean admin,
            String path) {
        try {
            polls.submit(poll, name, parseAnswers(allParams));
            return "redirect:" + path;
        } catch (SubmissionException e) {
            model.addAttribute("view", polls.view(poll));
            model.addAttribute("prefilledName", name);
            model.addAttribute("error", e.getMessage());
            if (admin) {
                addAdminAttributes(model, poll);
            } else {
                model.addAttribute("admin", false);
                model.addAttribute("submitPath", path);
            }
            return "poll";
        }
    }

    private void addAdminAttributes(Model model, Poll poll) {
        var base = "/a/" + poll.getAdminToken();
        model.addAttribute("admin", true);
        model.addAttribute("responseUrl", properties.responseUrl(poll.getResponseToken()));
        model.addAttribute("adminUrl", properties.adminUrl(poll.getAdminToken()));
        model.addAttribute("mailto", texts.invitationMailto(poll));
        model.addAttribute("adminBase", base);
        model.addAttribute("submitPath", base + "/svar");
    }

    /**
     * Plockar ut svaren ur formuläret. Fälten heter {@code d-2026-10-04}, så att datumet
     * står i namnet och svarsraden bär sitt eget datum (D-018). Skräp behandlas som
     * obesvarat och fångas av D-019-kontrollen i tjänsten.
     */
    private static Map<LocalDate, Availability> parseAnswers(Map<String, String> params) {
        var answers = new HashMap<LocalDate, Availability>();
        for (var entry : params.entrySet()) {
            if (!entry.getKey().startsWith("d-")) {
                continue;
            }
            try {
                var date = LocalDate.parse(entry.getKey().substring(2));
                answers.put(date, Availability.valueOf(entry.getValue()));
            } catch (RuntimeException ignored) {
                // se javadoc
            }
        }
        return answers;
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static CreatePollForm emptyForm() {
        return new CreatePollForm(null, null, null, null, 1, null, null);
    }

    /**
     * Okänt token ger samma svar som ett utgånget eller felaktigt. Ingen uppräkning, ingen
     * ledtråd om huruvida en förfrågan finns.
     */
    @ExceptionHandler(PollNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound() {
        return "notfound";
    }
}
