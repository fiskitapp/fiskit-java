package ar.com.fiskit;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.Map;

/**
 * An invoice as the API returns it.
 *
 * @param status pending, processing, authorized, rejected, reconciling or failed
 * @param number null until authorized
 * @param raw every field of the response, including the ARCA observations and errors
 */
public record Invoice(
        String id,
        String status,
        String voucherType,
        int pointOfSale,
        Long number,
        String cae,
        LocalDate caeDueDate,
        Map<String, Object> raw) {

    public boolean authorized() {
        return "authorized".equals(status);
    }

    static Invoice from(JsonNode node) {
        return new Invoice(
                text(node, "id"),
                text(node, "status"),
                text(node, "voucher_type"),
                node.path("point_of_sale").asInt(),
                node.hasNonNull("number") ? node.get("number").asLong() : null,
                text(node, "cae"),
                node.hasNonNull("cae_due_date") ? LocalDate.parse(node.get("cae_due_date").asText()) : null,
                Fiskit.toMap(node));
    }

    static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
