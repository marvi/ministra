package ministra.poll;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Genererar länktoken.
 *
 * <p>Länkarna är capability-URL:er: den som har länken kommer in. Därför 128 bitar från
 * {@link SecureRandom}, inte löpnummer och inte UUIDv7 — den senare är tidsordnad och
 * därmed delvis gissbar (se avsnittet om integritet i AGENTS.md).
 */
public final class Tokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final int BYTES = 16;

    private Tokens() {}

    /** 128 slumpbitar som 22 tecken base64url. */
    public static String generate() {
        var bytes = new byte[BYTES];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }
}
