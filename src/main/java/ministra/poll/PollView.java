package ministra.poll;

import java.util.List;

/**
 * Allt en listvy behöver. Admin-vyn är samma vy med länkar och knappar ovanpå (D-027),
 * så det finns bara en av de här.
 *
 * <p>Vilka dagar som ingår avgjordes när förfrågan skapades (D-042) och går inte att
 * ändra efteråt, så vyn behöver inte veta något om bortvalda dagar.
 */
public record PollView(Poll poll, List<ServiceDayView> days, List<String> participantNames) {

    public int participantCount() {
        return participantNames.size();
    }
}
