package ministra.poll;

import java.util.List;
import ministra.calendar.ServiceDay;

/**
 * En gudstjänstdag som den visas i listan: dagen själv, och vilka som svarat vad.
 *
 * <p>Namnen ligger i tre listor i stället för en karta, eftersom vyn visar dem grupperade
 * per svar. Alla deltagare ser allas svar — det är avsiktligt (D-010).
 */
public record ServiceDayView(
        ServiceDay day, List<String> can, List<String> ifNeeded, List<String> cannot) {

    public boolean hasAnswers() {
        return !can.isEmpty() || !ifNeeded.isEmpty() || !cannot.isEmpty();
    }
}
