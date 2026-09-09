package ministra.mail;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import ministra.MinistraProperties;
import ministra.poll.Poll;
import ministra.calendar.Dates;
import org.springframework.stereotype.Component;

/**
 * Formuleringarna i all utgående post.
 *
 * <p>Texterna är fastställda i D-035 och ska inte skrivas om på egen hand. Allt är ren
 * text — ingen HTML, ingen multipart. Appens enda mottagare är planerarna, en handfull
 * personer, och mejlen är några rader långa.
 */
@Component
public class MailTexts {

    /** Titeln kortas i utgående post, så att minsta möjliga fritext lämnar domänen. */
    private static final int TITLE_LIMIT = 80;

    private final MinistraProperties properties;

    public MailTexts(MinistraProperties properties) {
        this.properties = properties;
    }

    // ---------- 1. Vid skapandet ----------

    public String creationSubject(Poll poll) {
        return "Din förfrågan \"" + shortTitle(poll) + "\" är skapad";
    }

    public String creationBody(Poll poll) {
        return """
                Hej %s!

                Din förfrågan "%s" är skapad. Du har två länkar.

                Skicka den här till dem som ska svara:
                %s

                Den här är din egen. Spara den — med den ser du svaren och kan radera förfrågan:
                %s

                Förfrågan och alla svar raderas automatiskt efter %s.

                Ministra
                """
                .formatted(
                        poll.getCreatorName(),
                        shortTitle(poll),
                        properties.responseUrl(poll.getResponseToken()),
                        properties.adminUrl(poll.getAdminToken()),
                        Dates.format(poll.getValidUntil()));
    }

    // ---------- 2. Daglig sammanfattning ----------

    /** Nya svar på en förfrågan: förfrågan och namnen på dem som svarat sedan sist. */
    public record DigestItem(Poll poll, List<String> names) {}

    public String digestSubject(List<DigestItem> items) {
        if (items.size() == 1) {
            return "Nya svar på \"" + shortTitle(items.getFirst().poll()) + "\"";
        }
        return "Nya svar på dina förfrågningar";
    }

    public String digestBody(String creatorName, List<DigestItem> items) {
        var body = new StringBuilder();
        body.append("Hej ").append(creatorName).append("!\n\n");
        body.append("Sedan igår har det kommit nya svar.\n\n");
        for (var item : items) {
            body.append(shortTitle(item.poll())).append('\n');
            body.append("  ").append(String.join(", ", item.names())).append('\n');
            body.append("  ").append(properties.adminUrl(item.poll().getAdminToken())).append('\n');
            body.append("\n");
            body.append("Förfrågan och alla svar raderas automatiskt efter ")
                    .append(Dates.format(item.poll().getValidUntil()))
                    .append(".\n\n");
        }
        body.append("Ministra\n");
        return body.toString();
    }

    // ---------- 3. mailto:-texten som skaparen skickar själv ----------

    /**
     * Färdig {@code mailto:}-URL med skaparen som mottagare.
     *
     * <p>Mejlet lämnar aldrig vår server utan komponeras i skaparens egen klient, så
     * kommentaren får vara med här — den kan varken påverka vår leveransbarhet eller
     * missbrukas av utomstående (D-012, D-035).
     */
    public String invitationMailto(Poll poll) {
        return "mailto:"
                + encode(poll.getCreatorEmail())
                + "?subject="
                + encode(invitationSubject(poll))
                + "&body="
                + encode(invitationBody(poll));
    }

    public String invitationSubject(Poll poll) {
        return "Kan du dessa dagar? - " + poll.getTitle();
    }

    public String invitationBody(Poll poll) {
        // Länken ligger tidigt med flit: vissa klienter kapar långa mailto:-URL:er.
        var body = new StringBuilder();
        body.append("Hej!\n\n");
        body.append("Jag planerar \"")
                .append(poll.getTitle())
                .append("\" och behöver veta vilka dagar du kan. Fyll i här:\n\n");
        body.append(properties.responseUrl(poll.getResponseToken())).append("\n\n");
        // Är kommentaren tom ska den tomma raden också bort, inte lämna ett hål.
        if (poll.hasComment()) {
            body.append(poll.getComment().strip()).append("\n\n");
        }
        body.append(
                """
                Du markerar Kan, Om det behövs eller Kan inte för varje dag, och skriver ditt
                förnamn överst. Det tar ett par minuter. Svara gärna före %s.

                Hälsningar
                %s
                """
                        .formatted(Dates.format(poll.getValidUntil()), poll.getCreatorName()));
        return body.toString();
    }

    /**
     * Procentkodar som UTF-8. {@link URLEncoder} är byggd för formulärdata och kodar
     * mellanslag som {@code +}, vilket inte gäller i en mailto-URL, så det rättas här.
     */
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String shortTitle(Poll poll) {
        var title = poll.getTitle();
        return title.length() <= TITLE_LIMIT ? title : title.substring(0, TITLE_LIMIT) + "…";
    }
}
