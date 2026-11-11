package ar.com.fiskit;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Client of the FisKit API. Thread-safe: create one and reuse it.
 *
 * <pre>{@code
 * Fiskit fiskit = Fiskit.builder().apiKey(System.getenv("FISKIT_API_KEY")).build();
 * Invoice invoice = fiskit.invoices().create(
 *         Map.of("voucher_type", "invoice_c", "amounts", Map.of("total", 1500)), "sale-8812");
 * }</pre>
 */
public final class Fiskit {

    public static final String VERSION = "0.1.0";
    public static final String DEFAULT_BASE_URL = "https://api.fiskit.com.ar";
    private static final Set<Integer> RETRYABLE = Set.of(408, 409, 429, 500, 502, 503, 504);

    static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final String apiKey;
    private final String baseUrl;
    private final int maxRetries;
    private final Duration timeout;
    private final HttpClient http;
    private final Invoices invoices = new Invoices(this);
    private final Taxpayers taxpayers = new Taxpayers(this);

    private Fiskit(Builder builder) {
        String key = builder.apiKey != null ? builder.apiKey : System.getenv("FISKIT_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Missing API key: pass it or set FISKIT_API_KEY");
        }
        this.apiKey = key;
        this.baseUrl = builder.baseUrl.replaceAll("/+$", "");
        this.maxRetries = builder.maxRetries;
        this.timeout = builder.timeout;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Invoices invoices() {
        return invoices;
    }

    public Taxpayers taxpayers() {
        return taxpayers;
    }

    JsonNode requestJson(String method, String path, Object body, String idempotencyKey) {
        byte[] bytes = request(method, path, body, idempotencyKey, false);
        try {
            return bytes.length == 0 ? JSON.nullNode() : JSON.readTree(bytes);
        } catch (IOException e) {
            throw new FiskitException(0, "api_error", "invalid_response", "The response is not JSON", null, null);
        }
    }

    byte[] request(String method, String path, Object body, String idempotencyKey, boolean binary) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", binary ? "application/pdf" : "application/json")
                .header("User-Agent", "fiskit-java/" + VERSION);
        if (body != null) {
            try {
                request.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body)));
            } catch (IOException e) {
                throw new IllegalArgumentException("The body cannot be written as JSON", e);
            }
        } else {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        }
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        // Without an idempotency key a POST is never repeated: it might have been processed
        boolean retryAllowed = "GET".equals(method) || idempotencyKey != null;

        for (int attempt = 0; ; attempt++) {
            HttpResponse<byte[]> response;
            try {
                response = http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            } catch (IOException e) {
                if (retryAllowed && attempt < maxRetries) {
                    sleep(attempt, null);
                    continue;
                }
                throw new FiskitException(0, "connection_error", "network", String.valueOf(e.getMessage()), null, null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FiskitException(0, "connection_error", "interrupted", "Interrupted", null, null);
            }
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return response.body();
            }
            if (RETRYABLE.contains(status) && retryAllowed && attempt < maxRetries) {
                sleep(attempt, response.headers().firstValue("retry-after").orElse(null));
                continue;
            }
            throw FiskitException.from(status, response.body(), response.headers().firstValue("request-id").orElse(null));
        }
    }

    private static void sleep(int attempt, String retryAfter) {
        double seconds = 0;
        try {
            seconds = retryAfter == null ? 0 : Double.parseDouble(retryAfter);
        } catch (NumberFormatException ignored) {
            // Not a number of seconds: use the exponential wait
        }
        long millis = seconds > 0 ? (long) (Math.min(seconds, 30) * 1000) : (long) (500 * Math.pow(2, attempt));
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static final class Builder {

        private String apiKey;
        private String baseUrl = DEFAULT_BASE_URL;
        private int maxRetries = 2;
        private Duration timeout = Duration.ofSeconds(60);

        private Builder() {
        }

        /** sk_test_... or sk_live_...; by default the FISKIT_API_KEY environment variable. */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout, "timeout");
            return this;
        }

        public Fiskit build() {
            return new Fiskit(this);
        }
    }

    /** Converts a JSON node to a plain map, for callers that prefer maps. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> toMap(JsonNode node) {
        return JSON.convertValue(node, Map.class);
    }
}
