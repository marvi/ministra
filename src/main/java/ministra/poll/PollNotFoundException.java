package ministra.poll;

/**
 * Okänt token.
 *
 * <p>Okänt och utgånget token ska ge samma svar som ett felaktigt — ingen uppräkning,
 * ingen ledtråd om huruvida en förfrågan finns (se AGENTS.md, integritet).
 */
public class PollNotFoundException extends RuntimeException {
    public PollNotFoundException() {
        super("Förfrågan finns inte");
    }
}
