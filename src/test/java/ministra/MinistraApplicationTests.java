package ministra;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import ministra.mail.OutboxSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Enda {@code @SpringBootTest} i projektet. Den finns för att fånga att allt går att
 * koppla ihop — schemat validerar mot Flyway-migreringarna, jte hittar de förkompilerade
 * mallarna och de schemalagda jobben går att skapa. Övrig testning sker i snabbare
 * skivor.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MinistraApplicationTests.ErrorTestConfiguration.class)
@TestPropertySource(
        properties = {
            "spring.mail.host=localhost",
            "spring.mail.port=1025",
            "ministra.base-url=https://test.example",
            "ministra.mail-from=ministra@example.se"
        })
class MinistraApplicationTests extends PostgresTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Autowired OutboxSender outboxSender;
    @LocalServerPort int port;

    @TestConfiguration(proxyBeanMethods = false)
    static class ErrorTestConfiguration {
        @Bean
        FailingController failingController() {
            return new FailingController();
        }
    }

    @Controller
    static class FailingController {
        @GetMapping("/__test/server-error")
        String fail() {
            throw new IllegalStateException("Testfel som inte får visas för användaren");
        }
    }

    @Test
    void contextLoads() {
        assertThat(outboxSender).isNotNull();
    }

    @Test
    void unknown_path_uses_branded_404_page() throws Exception {
        var response = get("/sidan-finns-inte");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body())
                .contains("Sidan finns inte", "Till startsidan", "href=\"/\"");
    }

    @Test
    void unexpected_exception_uses_branded_500_page_without_details() throws Exception {
        var response = get("/__test/server-error");

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body())
                .contains("Något gick fel", "Till startsidan", "href=\"/\"")
                .doesNotContain("Testfel som inte får visas för användaren");
    }

    private HttpResponse<String> get(String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "text/html")
                .GET()
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
