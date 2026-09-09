package ministra.poll;

/** Ett svar som inte går att ta emot, med ett meddelande avsett för användaren. */
public class SubmissionException extends RuntimeException {
    public SubmissionException(String message) {
        super(message);
    }
}
