package ministra.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import ministra.MinistraProperties;
import ministra.poll.Poll;
import org.junit.jupiter.api.Test;

/** Texterna är fastställda i D-035. Testerna finns för att de inte ska glida i väg. */
class MailTextsTest {

    private final MailTexts texts =
            new MailTexts(
                    new MinistraProperties("https://ministra.marvi.work", 10, "ministra@example.se"));

    private Poll poll(String title, String comment) {
        return new Poll(
                "SVARSTOKEN",
                "ADMINTOKEN",
                title,
                comment,
                LocalDate.of(2026, 10, 4),
                LocalDate.of(2026, 12, 20),
                LocalDate.of(2026, 10, 9),
                "Markus",
                "markus@example.se");
    }

    @Test
    void creation_email_contains_both_links() {
        var body = texts.creationBody(poll("Sakristaner fram till påsk", "Hör av dig vid frågor."));

        assertThat(body).contains("Hej Markus!");
        assertThat(body).contains("https://ministra.marvi.work/s/SVARSTOKEN");
        assertThat(body).contains("https://ministra.marvi.work/a/ADMINTOKEN");
        assertThat(body).contains("9 oktober 2026");
    }

    @Test
    void creation_email_leaves_out_the_comment() {
        // Skapandet är en oautentiserad utgång som mejlar till en användarangiven adress.
        // Ju mindre fritext som lämnar vår avsändardomän desto bättre (D-016).
        var body = texts.creationBody(poll("Sakristaner", "SPAM SPAM köp billiga piller"));

        assertThat(body).doesNotContain("SPAM");
    }

    @Test
    void creation_email_shortens_long_titles() {
        var body = texts.creationBody(poll("x".repeat(200), null));

        assertThat(body).contains("x".repeat(80) + "…");
        assertThat(body).doesNotContain("x".repeat(81));
    }

    @Test
    void digest_subject_names_the_poll_when_there_is_only_one() {
        var item = new MailTexts.DigestItem(poll("Textläsare i höst", null), List.of("Anna"));

        assertThat(texts.digestSubject(List.of(item))).isEqualTo("Nya svar på \"Textläsare i höst\"");
    }

    @Test
    void digest_subject_is_generic_when_there_are_several() {
        var items =
                List.of(
                        new MailTexts.DigestItem(poll("En", null), List.of("Anna")),
                        new MailTexts.DigestItem(poll("Två", null), List.of("Bengt")));

        assertThat(texts.digestSubject(items)).isEqualTo("Nya svar på dina förfrågningar");
    }

    @Test
    void digest_lists_the_names_and_the_admin_link() {
        var items =
                List.of(
                        new MailTexts.DigestItem(
                                poll("Sakristaner", null), List.of("Anna", "Bengt")));

        var body = texts.digestBody("Markus", items);

        assertThat(body).contains("Hej Markus!");
        assertThat(body).contains("Sedan igår har det kommit nya svar.");
        assertThat(body).contains("Anna, Bengt");
        assertThat(body).contains("https://ministra.marvi.work/a/ADMINTOKEN");
    }

    @Test
    void invitation_speaks_of_days_not_sundays() {
        // Listan innehåller även jul, påsk och andra helgdagar (D-040).
        var body = texts.invitationBody(poll("Sakristaner", null));

        assertThat(body).doesNotContain("söndag");
        assertThat(body).contains("vilka dagar du kan");
    }

    @Test
    void mailto_has_the_creator_as_recipient_and_no_one_else() {
        var mailto = texts.invitationMailto(poll("Sakristaner", null));

        assertThat(mailto).startsWith("mailto:markus%40example.se?subject=");
        assertThat(mailto).contains("&body=");
    }

    @Test
    void mailto_percent_encodes_swedish_characters_as_utf8() {
        var mailto = texts.invitationMailto(poll("Sakristaner", null));

        // ö:et i "behövs" och "förnamn" ska bli %C3%B6, inte tappa prickarna.
        assertThat(mailto).contains("%C3%B6");
        var decoded = URLDecoder.decode(mailto, StandardCharsets.UTF_8);
        assertThat(decoded).contains("Kan du dessa dagar?");
        assertThat(decoded).contains("Om det behövs");
        assertThat(decoded).contains("Hälsningar");
    }

    @Test
    void mailto_encodes_spaces_as_percent20_not_plus() {
        var mailto = texts.invitationMailto(poll("Två ord", null));

        assertThat(mailto).doesNotContain("+");
    }

    @Test
    void invitation_has_the_link_early() {
        var body = texts.invitationBody(poll("Sakristaner", null));

        var linkPosition = body.indexOf("https://ministra.marvi.work/s/SVARSTOKEN");
        assertThat(linkPosition).isPositive();
        assertThat(linkPosition).isLessThan(120);
    }

    @Test
    void invitation_includes_the_comment_when_there_is_one() {
        // Det här mejlet lämnar aldrig vår server, så kommentaren är ofarlig här (D-035).
        var body = texts.invitationBody(poll("Sakristaner", "Vi ses i sakristian kl 9."));

        assertThat(body).contains("Vi ses i sakristian kl 9.");
    }

    @Test
    void invitation_leaves_no_gap_when_the_comment_is_missing() {
        var body = texts.invitationBody(poll("Sakristaner", null));

        assertThat(body).doesNotContain("\n\n\n");
    }
}
