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
    void skapelsemejlet_innehåller_båda_länkarna() {
        var body = texts.creationBody(poll("Sakristaner fram till påsk", "Hör av dig vid frågor."));

        assertThat(body).contains("Hej Markus!");
        assertThat(body).contains("https://ministra.marvi.work/s/SVARSTOKEN");
        assertThat(body).contains("https://ministra.marvi.work/a/ADMINTOKEN");
        assertThat(body).contains("9 oktober 2026");
    }

    @Test
    void skapelsemejlet_tar_inte_med_kommentaren() {
        // Skapandet är en oautentiserad utgång som mejlar till en användarangiven adress.
        // Ju mindre fritext som lämnar vår avsändardomän desto bättre (D-016).
        var body = texts.creationBody(poll("Sakristaner", "SPAM SPAM köp billiga piller"));

        assertThat(body).doesNotContain("SPAM");
    }

    @Test
    void skapelsemejlet_kortar_långa_titlar() {
        var body = texts.creationBody(poll("x".repeat(200), null));

        assertThat(body).contains("x".repeat(80) + "…");
        assertThat(body).doesNotContain("x".repeat(81));
    }

    @Test
    void sammanfattningens_ämne_namnger_förfrågan_när_det_bara_finns_en() {
        var item = new MailTexts.DigestItem(poll("Textläsare i höst", null), List.of("Anna"));

        assertThat(texts.digestSubject(List.of(item))).isEqualTo("Nya svar på \"Textläsare i höst\"");
    }

    @Test
    void sammanfattningens_ämne_är_allmänt_när_det_finns_flera() {
        var items =
                List.of(
                        new MailTexts.DigestItem(poll("En", null), List.of("Anna")),
                        new MailTexts.DigestItem(poll("Två", null), List.of("Bengt")));

        assertThat(texts.digestSubject(items)).isEqualTo("Nya svar på dina förfrågningar");
    }

    @Test
    void sammanfattningen_listar_namnen_och_adminlänken() {
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
    void inbjudan_talar_om_dagar_inte_söndagar() {
        // Listan innehåller även jul, påsk och andra helgdagar (D-040).
        var body = texts.invitationBody(poll("Sakristaner", null));

        assertThat(body).doesNotContain("söndag");
        assertThat(body).contains("vilka dagar du kan");
    }

    @Test
    void mailto_har_skaparen_som_mottagare_och_ingen_annan() {
        var mailto = texts.invitationMailto(poll("Sakristaner", null));

        assertThat(mailto).startsWith("mailto:markus%40example.se?subject=");
        assertThat(mailto).contains("&body=");
    }

    @Test
    void mailto_procentkodar_svenska_tecken_som_utf8() {
        var mailto = texts.invitationMailto(poll("Sakristaner", null));

        // ö:et i "behövs" och "förnamn" ska bli %C3%B6, inte tappa prickarna.
        assertThat(mailto).contains("%C3%B6");
        var decoded = URLDecoder.decode(mailto, StandardCharsets.UTF_8);
        assertThat(decoded).contains("Kan du dessa dagar?");
        assertThat(decoded).contains("Om det behövs");
        assertThat(decoded).contains("Hälsningar");
    }

    @Test
    void mailto_kodar_mellanslag_som_procent20_inte_plus() {
        var mailto = texts.invitationMailto(poll("Två ord", null));

        assertThat(mailto).doesNotContain("+");
    }

    @Test
    void inbjudan_har_länken_tidigt() {
        var body = texts.invitationBody(poll("Sakristaner", null));

        var linkPosition = body.indexOf("https://ministra.marvi.work/s/SVARSTOKEN");
        assertThat(linkPosition).isPositive();
        assertThat(linkPosition).isLessThan(120);
    }

    @Test
    void inbjudan_tar_med_kommentaren_när_den_finns() {
        // Det här mejlet lämnar aldrig vår server, så kommentaren är ofarlig här (D-035).
        var body = texts.invitationBody(poll("Sakristaner", "Vi ses i sakristian kl 9."));

        assertThat(body).contains("Vi ses i sakristian kl 9.");
    }

    @Test
    void inbjudan_lämnar_inget_hål_när_kommentaren_saknas() {
        var body = texts.invitationBody(poll("Sakristaner", null));

        assertThat(body).doesNotContain("\n\n\n");
    }
}
