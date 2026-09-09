package ministra.poll;

/**
 * Deltagarens svar för en enskild gudstjänstdag. Se D-013 och D-019.
 *
 * <p>Exakt tre tillstånd. Det finns inget "obesvarat" — formuläret går inte att skicka
 * med luckor, så en dag har alltid ett av dessa.
 */
public enum Availability {
    /** Grön. Jag kan ta den här dagen. */
    CAN("Kan"),

    /** Gul. Jag avstår helst, men ställer upp om ingen annan kan. */
    IF_NEEDED("Om det behövs"),

    /** Röd. Jag är förhindrad. */
    CANNOT("Kan inte");

    private final String label;

    Availability(String label) {
        this.label = label;
    }

    /** Etiketten som visas för användaren. Översättningen hör hemma här, inte i vyn. */
    public String label() {
        return label;
    }
}
