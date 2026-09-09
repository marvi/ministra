package ministra.web;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Klockan injiceras överallt i stället för att kalla {@code now()} direkt, så att tester
 * kan styra tiden.
 *
 * <p>Svensk tid, inte UTC — nattpasset går klockan 22 lokalt (D-024).
 */
@Configuration
public class ClockConfiguration {

    public static final ZoneId ZONE = ZoneId.of("Europe/Stockholm");

    @Bean
    Clock clock() {
        return Clock.system(ZONE);
    }
}
