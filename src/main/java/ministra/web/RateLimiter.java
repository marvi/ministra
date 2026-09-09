package ministra.web;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import ministra.MinistraProperties;
import org.springframework.stereotype.Component;

/**
 * Tak för hur många förfrågningar en och samma IP får skapa per timme (D-017).
 *
 * <p>Skapandet är en oautentiserad utgång som mejlar till en användarangiven adress. Det
 * är den enda reella missbruksvägen i appen, och en gräns som ingen verklig användare
 * märker stänger den.
 *
 * <p>Räkneverket ligger i minnet, aldrig i databasen — vi sparar inga IP-adresser
 * (D-028). En omstart nollställer det, och det är acceptabelt.
 */
@Component
public class RateLimiter {

    private static final Duration WINDOW = Duration.ofHours(1);

    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();
    private final int limit;

    public RateLimiter(MinistraProperties properties) {
        this.limit = properties.createsPerHourPerIp();
    }

    /** Registrerar ett försök och svarar om det ryms inom taket. */
    public boolean tryAcquire(String key, Instant now) {
        var cutoff = now.minus(WINDOW);
        var timestamps = hits.computeIfAbsent(key, unused -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= limit) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }

    /**
     * Klientens adress.
     *
     * <p>Bakom reverse proxy måste den läsas ur {@code X-Forwarded-For}, annars ser alla
     * ut att komma från proxyn och gränsen slår mot alla på en gång (D-022).
     */
    public static String clientIp(HttpServletRequest request) {
        var forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            var first = forwarded.split(",", 2)[0].strip();
            if (!first.isEmpty()) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }

    /** Städar bort tomma fönster så att kartan inte växer obegränsat. */
    public void evictStale(Instant now) {
        var cutoff = now.minus(WINDOW);
        hits.entrySet()
                .removeIf(
                        entry -> {
                            var timestamps = entry.getValue();
                            synchronized (timestamps) {
                                while (!timestamps.isEmpty()
                                        && timestamps.peekFirst().isBefore(cutoff)) {
                                    timestamps.pollFirst();
                                }
                                return timestamps.isEmpty();
                            }
                        });
    }
}
