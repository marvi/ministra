package ministra;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Appens egen konfiguration. Sätts som miljövariabler ur env-filen (D-014).
 *
 * @param baseUrl publik adress utan avslutande snedstreck, till exempel
 *     {@code https://ministra.marvi.work}. Alla absoluta länkar byggs från den och
 *     aldrig från {@code X-Forwarded-Host} (D-022).
 * @param createsPerHourPerIp tak för antal skapade förfrågningar per IP och timme (D-017)
 * @param mailFrom avsändaradress, på den verifierade domänen med SPF och DKIM (D-029)
 */
@ConfigurationProperties(prefix = "ministra")
public record MinistraProperties(String baseUrl, int createsPerHourPerIp, String mailFrom) {

    public MinistraProperties {
        if (baseUrl != null && baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (createsPerHourPerIp <= 0) {
            createsPerHourPerIp = 10;
        }
    }

    public String responseUrl(String responseToken) {
        return baseUrl + "/s/" + responseToken;
    }

    public String adminUrl(String adminToken) {
        return baseUrl + "/a/" + adminToken;
    }
}
