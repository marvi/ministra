package ministra.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import ministra.MinistraProperties;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private static final Instant NOW = Instant.parse("2026-09-09T10:00:00Z");

    private RateLimiter limiterAllowing(int perHour) {
        return new RateLimiter(new MinistraProperties("http://localhost", perHour, "a@b.se"));
    }

    @Test
    void släpper_igenom_upp_till_taket() {
        var limiter = limiterAllowing(3);

        assertThat(limiter.tryAcquire("1.2.3.4", NOW)).isTrue();
        assertThat(limiter.tryAcquire("1.2.3.4", NOW)).isTrue();
        assertThat(limiter.tryAcquire("1.2.3.4", NOW)).isTrue();
        assertThat(limiter.tryAcquire("1.2.3.4", NOW)).isFalse();
    }

    @Test
    void räknar_per_adress() {
        var limiter = limiterAllowing(1);

        assertThat(limiter.tryAcquire("1.2.3.4", NOW)).isTrue();
        assertThat(limiter.tryAcquire("5.6.7.8", NOW)).isTrue();
        assertThat(limiter.tryAcquire("1.2.3.4", NOW)).isFalse();
    }

    @Test
    void fönstret_glider_framåt() {
        var limiter = limiterAllowing(1);

        assertThat(limiter.tryAcquire("1.2.3.4", NOW)).isTrue();
        assertThat(limiter.tryAcquire("1.2.3.4", NOW.plus(Duration.ofMinutes(59)))).isFalse();
        assertThat(limiter.tryAcquire("1.2.3.4", NOW.plus(Duration.ofMinutes(61)))).isTrue();
    }

    @Test
    void städar_bort_utgångna_fönster() {
        var limiter = limiterAllowing(1);
        limiter.tryAcquire("1.2.3.4", NOW);

        limiter.evictStale(NOW.plus(Duration.ofHours(2)));

        assertThat(limiter.tryAcquire("1.2.3.4", NOW.plus(Duration.ofHours(2)))).isTrue();
    }
}
