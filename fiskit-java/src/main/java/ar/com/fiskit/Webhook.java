package ar.com.fiskit;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Checks the webhooks FisKit sends (Standard Webhooks signatures). */
public final class Webhook {

    private Webhook() {
    }

    /** Same as {@link #verify(String, Map, String, long, Instant)} with 5 minutes of tolerance. */
    public static JsonNode verify(String rawBody, Map<String, String> headers, String secret) {
        return verify(rawBody, headers, secret, 300, Instant.now());
    }

    /**
     * Checks the signature and returns the parsed event. Pass the raw body as received.
     *
     * @throws IllegalArgumentException if the signature is wrong or the message is too old
     */
    public static JsonNode verify(String rawBody, Map<String, String> headers, String secret, long toleranceSeconds,
            Instant now) {
        Map<String, String> lowered = new TreeMap<>();
        headers.forEach((name, value) -> lowered.put(name.toLowerCase(Locale.ROOT), value));
        String id = lowered.get("webhook-id");
        String timestamp = lowered.get("webhook-timestamp");
        String signatures = lowered.get("webhook-signature");
        if (id == null || timestamp == null || signatures == null) {
            throw new IllegalArgumentException("Missing webhook headers");
        }
        if (Math.abs(now.getEpochSecond() - Long.parseLong(timestamp)) > toleranceSeconds) {
            throw new IllegalArgumentException("Webhook timestamp out of tolerance");
        }
        byte[] expected;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(secret.replaceFirst("^whsec_", "")), "HmacSHA256"));
            expected = Base64.getEncoder().encode(
                    mac.doFinal((id + "." + timestamp + "." + rawBody).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
        for (String candidate : signatures.split(" ")) {
            byte[] received = candidate.replaceFirst("^v1,", "").getBytes(StandardCharsets.US_ASCII);
            if (MessageDigest.isEqual(expected, received)) {
                try {
                    return Fiskit.JSON.readTree(rawBody);
                } catch (Exception e) {
                    throw new IllegalArgumentException("The webhook body is not JSON", e);
                }
            }
        }
        throw new IllegalArgumentException("Invalid webhook signature");
    }
}
