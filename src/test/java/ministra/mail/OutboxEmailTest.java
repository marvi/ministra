package ministra.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Backoffen i {@link OutboxEmail#recordFailure}. Ren JUnit — entiteten har ingen Spring
 * att koppla upp.
 */
class OutboxEmailTest {

    private static final Instant NOW = Instant.parse("2026-09-09T20:00:00Z");

    private OutboxEmail email() {
        return new OutboxEmail("markus@example.se", "Ämne", "Text");
    }

    @Test
    void is_due_immediately_when_created() {
        var email = email();

        assertThat(email.getAttempts()).isZero();
        assertThat(email.getNextAttemptAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void doubles_the_wait_for_every_failure() {
        var email = email();

        email.recordFailure("fel", NOW);
        assertThat(email.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(1)));

        email.recordFailure("fel", NOW);
        assertThat(email.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(2)));

        email.recordFailure("fel", NOW);
        assertThat(email.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(4)));

        assertThat(email.getAttempts()).isEqualTo(3);
    }

    @Test
    void never_waits_longer_than_an_hour() {
        var email = email();

        for (int i = 0; i < 9; i++) {
            email.recordFailure("fel", NOW);
        }

        assertThat(email.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
    }

    @Test
    void gives_up_after_ten_attempts() {
        var email = email();

        for (int i = 0; i < OutboxEmail.MAX_ATTEMPTS - 1; i++) {
            email.recordFailure("fel", NOW);
        }
        assertThat(email.isExhausted()).isFalse();

        email.recordFailure("fel", NOW);
        assertThat(email.isExhausted()).isTrue();
    }

    @Test
    void truncates_long_error_messages_to_the_column_width() {
        var email = email();

        email.recordFailure("x".repeat(2_000), NOW);

        assertThat(email.getLastError()).hasSize(500);
    }

    @Test
    void tolerates_a_missing_error_message() {
        // MailException.getMessage() kan vara null.
        var email = email();

        email.recordFailure(null, NOW);

        assertThat(email.getLastError()).isNull();
        assertThat(email.getAttempts()).isEqualTo(1);
    }
}
