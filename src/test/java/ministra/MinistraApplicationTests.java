package ministra;

import static org.assertj.core.api.Assertions.assertThat;

import ministra.mail.OutboxSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Enda {@code @SpringBootTest} i projektet. Den finns för att fånga att allt går att
 * koppla ihop — schemat validerar mot Flyway-migreringarna, jte hittar de förkompilerade
 * mallarna och de schemalagda jobben går att skapa. Övrig testning sker i snabbare
 * skivor.
 */
@SpringBootTest
@TestPropertySource(
        properties = {
            "spring.mail.host=localhost",
            "spring.mail.port=1025",
            "ministra.base-url=https://test.example",
            "ministra.mail-from=ministra@example.se"
        })
class MinistraApplicationTests extends PostgresTest {

    @Autowired OutboxSender outboxSender;

    @Test
    void contextLoads() {
        assertThat(outboxSender).isNotNull();
    }
}
