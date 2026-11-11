package ar.com.fiskit.spring;

import ar.com.fiskit.Fiskit;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code fiskit.*} in application.yml or application.properties.
 *
 * @param apiKey sk_test_... or sk_live_...; if empty, the FISKIT_API_KEY environment variable
 * @param webhookSecret whsec_... of your webhook endpoint, for {@link ar.com.fiskit.Webhook}
 */
@ConfigurationProperties("fiskit")
public record FiskitProperties(String apiKey, String webhookSecret, String baseUrl, Integer maxRetries,
        Duration timeout) {

    public FiskitProperties {
        baseUrl = baseUrl == null ? Fiskit.DEFAULT_BASE_URL : baseUrl;
        maxRetries = maxRetries == null ? 2 : maxRetries;
        timeout = timeout == null ? Duration.ofSeconds(60) : timeout;
    }
}
