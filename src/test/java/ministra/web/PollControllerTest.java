package ministra.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import ministra.MinistraProperties;
import ministra.calendar.ServiceDay;
import ministra.mail.MailTexts;
import ministra.poll.Poll;
import ministra.poll.PollNotFoundException;
import ministra.poll.PollService;
import ministra.poll.PollView;
import ministra.poll.SubmissionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import gg.jte.springframework.boot.autoconfigure.JteAutoConfiguration;
import gg.jte.springframework.boot.autoconfigure.ServletJteAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PollController.class)
// Vyskiktet ingår inte i @WebMvcTest-skivan av sig självt; utan detta renderas ingenting
// och testerna får tomma svar utan felmeddelande.
@ImportAutoConfiguration({JteAutoConfiguration.class, ServletJteAutoConfiguration.class})
class PollControllerTest {

    private static final Clock FIXED =
            Clock.fixed(Instant.parse("2026-09-09T10:00:00Z"), ZoneId.of("Europe/Stockholm"));

    @TestConfiguration
    static class Beans {
        @Bean
        Clock clock() {
            return FIXED;
        }

        @Bean
        MinistraProperties properties() {
            return new MinistraProperties("https://test.example", 2, "ministra@example.se");
        }

        @Bean
        MailTexts texts(MinistraProperties properties) {
            return new MailTexts(properties);
        }

        @Bean
        RateLimiter rateLimiter(MinistraProperties properties) {
            return new RateLimiter(properties);
        }
    }

    @Autowired MockMvc mvc;
    @MockitoBean PollService polls;

    private Poll poll() {
        return new Poll(
                "SVARSTOKEN",
                "ADMINTOKEN",
                "Sakristaner fram till påsk",
                null,
                LocalDate.of(2026, 10, 4),
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 10, 9),
                "Markus",
                "markus@example.se");
    }

    @BeforeEach
    void stubSundays() {
        given(polls.selectableDays(any()))
                .willReturn(List.of(new ServiceDay(LocalDate.of(2026, 10, 4), "Den helige Mikaels dag")));
    }

    @Test
    void skapaformuläret_visas() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ny förfrågan")));
    }

    @Test
    void alla_svar_bär_noindex() throws Exception {
        // Församlingens listor ska inte gå att googla.
        mvc.perform(get("/"))
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow, noarchive"));
    }

    @Test
    void okänt_token_ger_samma_svar_som_ett_raderat() throws Exception {
        // Ingen uppräkning: inget avslöjar om en förfrågan finns eller har funnits.
        given(polls.byResponseToken(anyString())).willThrow(new PollNotFoundException());

        mvc.perform(get("/s/finns-inte"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("finns inte")));
    }

    @Test
    void svarsvyn_visar_den_beslutade_instruktionstexten() throws Exception {
        var subject = poll();
        given(polls.byResponseToken("SVARSTOKEN")).willReturn(subject);
        given(polls.view(subject)).willReturn(new PollView(subject, List.of(), List.of()));

        mvc.perform(get("/s/SVARSTOKEN"))
                .andExpect(status().isOk())
                // Ordalydelsen är beslutad i D-013 och står i sidan, inte i en hover.
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Mittenvalet betyder att du helst avstår")));
    }

    @Test
    void adminvyn_visar_båda_länkarna_och_mailtoknappen() throws Exception {
        var subject = poll();
        given(polls.byAdminToken("ADMINTOKEN")).willReturn(subject);
        given(polls.view(subject)).willReturn(new PollView(subject, List.of(), List.of()));

        mvc.perform(get("/a/ADMINTOKEN"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "https://test.example/s/SVARSTOKEN")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "https://test.example/a/ADMINTOKEN")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("mailto:")));
    }

    @Test
    void svarsvyn_avslöjar_inte_adminlänken() throws Exception {
        var subject = poll();
        given(polls.byResponseToken("SVARSTOKEN")).willReturn(subject);
        given(polls.view(subject)).willReturn(new PollView(subject, List.of(), List.of()));

        mvc.perform(get("/s/SVARSTOKEN"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("ADMINTOKEN"))));
    }

    // ---------- Skapa i två steg (D-042) ----------

    @Test
    void steg_ett_leder_till_dagvalet_utan_att_spara() throws Exception {
        given(polls.candidateDays(any(), any()))
                .willReturn(List.of(
                        new ServiceDay(LocalDate.of(2026, 12, 25), "Juldagen"),
                        new ServiceDay(LocalDate.of(2026, 12, 26), "Annandag jul")));

        mvc.perform(step1("10.0.0.10"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Juldagen")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Annandag jul")));

        // Ingenting får sparas i det här steget.
        org.mockito.Mockito.verify(polls, org.mockito.Mockito.never()).create(any(), any(), any());
    }

    @Test
    void ogiltig_epost_visar_formuläret_igen_med_felet() throws Exception {
        mvc.perform(step1("10.0.0.11").param("creatorEmail", "inte-en-adress"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Kontrollera e-postadressen")));
    }

    @Test
    void steg_två_skapar_och_leder_till_adminvyn() throws Exception {
        given(polls.create(any(), any(), any())).willReturn(poll());

        mvc.perform(step2("10.0.0.12").param("day", "2026-12-25").param("day", "2026-12-26"))
                .andExpect(redirectedUrl("/a/ADMINTOKEN"));
    }

    @Test
    void avvisat_dagval_visas_som_fel_på_dagsidan() throws Exception {
        given(polls.candidateDays(any(), any()))
                .willReturn(List.of(new ServiceDay(LocalDate.of(2026, 12, 25), "Juldagen")));
        willThrow(new SubmissionException("Minst en dag måste vara med i förfrågan."))
                .given(polls)
                .create(any(), any(), any());

        mvc.perform(step2("10.0.0.13"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Minst en dag måste vara med")));
    }

    @Test
    void hastighetsgränsen_stoppar_upprepade_skapanden() throws Exception {
        // Taket är två i testkonfigurationen, och räknas där förfrågan faktiskt skapas.
        given(polls.create(any(), any(), any())).willReturn(poll());
        given(polls.candidateDays(any(), any()))
                .willReturn(List.of(new ServiceDay(LocalDate.of(2026, 12, 25), "Juldagen")));

        mvc.perform(step2("10.0.0.14").param("day", "2026-12-25"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(step2("10.0.0.14").param("day", "2026-12-25"))
                .andExpect(status().is3xxRedirection());

        mvc.perform(step2("10.0.0.14").param("day", "2026-12-25"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("För många förfrågningar")));
    }

    @Test
    void lägg_till_datum_visar_dagen_i_listan_utan_att_spara() throws Exception {
        given(polls.candidateDays(any(), any()))
                .willReturn(List.of(
                        new ServiceDay(LocalDate.of(2026, 10, 4), "Den helige Mikaels dag"),
                        new ServiceDay(LocalDate.of(2026, 10, 14))));

        mvc.perform(step("/skapa/dagar", "10.0.0.17").param("extraDate", "2026-10-14"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("14 okt")));

        org.mockito.Mockito.verify(polls, org.mockito.Mockito.never()).create(any(), any(), any());
    }

    @Test
    void htmx_får_bara_fragmentet_inte_hela_sidan() throws Exception {
        given(polls.candidateDays(any(), any()))
                .willReturn(List.of(new ServiceDay(LocalDate.of(2026, 10, 14))));

        mvc.perform(step("/skapa/dagar", "10.0.0.19")
                        .header("HX-Request", "true")
                        .param("extraDate", "2026-10-14"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<div id=\"day-choice\">")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("<html"))));
    }

    @Test
    void htmx_faller_tillbaka_till_hela_formuläret_när_fälten_är_ogiltiga() throws Exception {
        // Kräver manipulerade dolda fält. Utan den här grenen skulle fragmentmallen
        // renderas med en modell byggd för skapa-formuläret och gå sönder.
        mvc.perform(step("/skapa/dagar", "10.0.0.20")
                        .header("HX-Request", "true")
                        .param("creatorEmail", "inte-en-adress"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Kontrollera e-postadressen")));
    }

    @Test
    void avvisat_datum_visas_som_fel_på_dagsidan() throws Exception {
        given(polls.candidateDays(any(), any()))
                .willReturn(List.of(new ServiceDay(LocalDate.of(2026, 10, 4), "Den helige Mikaels dag")));
        willThrow(new SubmissionException("Den dagen finns redan i listan."))
                .given(polls)
                .validateExtraDay(any(), any(), any());

        mvc.perform(step("/skapa/dagar", "10.0.0.18").param("extraDate", "2026-10-04"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("finns redan i listan")));
    }

    @Test
    void hastighetsgränsen_räknar_per_klient_inte_per_proxy() throws Exception {
        // Utan X-Forwarded-For ser alla bakom proxyn ut som samma adress (D-022).
        given(polls.create(any(), any(), any())).willReturn(poll());

        mvc.perform(step2("10.0.0.15").param("day", "2026-12-25"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(step2("10.0.0.15").param("day", "2026-12-25"))
                .andExpect(status().is3xxRedirection());

        mvc.perform(step2("10.0.0.16").param("day", "2026-12-25"))
                .andExpect(status().is3xxRedirection());
    }

    /**
     * Varje test får en egen klientadress. Hastighetsbegränsaren är en singleton i
     * kontexten, så tester som delar adress påverkar varandra.
     */
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder step1(
            String clientIp) {
        return withForm(post("/skapa"), clientIp);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder step2(
            String clientIp) {
        return withForm(post("/skapa/spara"), clientIp);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder step(
            String path, String clientIp) {
        return withForm(post(path), clientIp);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withForm(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String clientIp) {
        return request
                .header("X-Forwarded-For", clientIp)
                .param("title", "Sakristaner")
                .param("startDate", "2026-10-04")
                .param("endDate", "2026-11-01")
                .param("validMonths", "1")
                .param("creatorName", "Markus")
                .param("creatorEmail", "markus@example.se");
    }
}
