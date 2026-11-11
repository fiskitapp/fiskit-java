package ar.com.fiskit;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/** Issue invoices and credit or debit notes, look them up, download their PDF and email them. */
public final class Invoices {

    private final Fiskit client;

    Invoices(Fiskit client) {
        this.client = client;
    }

    /**
     * Issues an invoice with a generated idempotency key, so the automatic retries never issue a second
     * one. Prefer {@link #create(Map, String)} with your sale id to make your own retries safe too.
     *
     * @param body the request as documented, for example
     *     {@code Map.of("voucher_type", "invoice_c", "amounts", Map.of("total", 1500))}
     */
    public Invoice create(Map<String, ?> body) {
        return create(body, UUID.randomUUID().toString());
    }

    public Invoice create(Map<String, ?> body, String idempotencyKey) {
        return Invoice.from(client.requestJson("POST", "/v1/invoices", body, idempotencyKey));
    }

    public Invoice retrieve(String id) {
        return Invoice.from(client.requestJson("GET", "/v1/invoices/" + encode(id), null, null));
    }

    /** The voucher PDF, with the QR code ARCA requires. */
    public byte[] pdf(String id) {
        return client.request("GET", "/v1/invoices/" + encode(id) + "/pdf", null, null, true);
    }

    /** Emails the PDF to receiver.email of the invoice. @return the address it was sent to */
    public String email(String id) {
        return email(id, null);
    }

    /** Emails the PDF to {@code email}. @return the address it was sent to */
    public String email(String id, String email) {
        Map<String, ?> body = email == null ? Map.of() : Map.of("email", email);
        return client.requestJson("POST", "/v1/invoices/" + encode(id) + "/email", body, null).path("email").asText();
    }

    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
