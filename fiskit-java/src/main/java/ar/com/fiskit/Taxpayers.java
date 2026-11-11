package ar.com.fiskit;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Look up a CUIT in the ARCA registry. */
public final class Taxpayers {

    private final Fiskit client;

    Taxpayers(Fiskit client) {
        this.client = client;
    }

    /** Name, fiscal address and VAT condition of a CUIT. */
    public Taxpayer retrieve(long cuit) {
        JsonNode node = client.requestJson("GET", "/v1/taxpayers/" + cuit, null, null);
        return new Taxpayer(
                node.path("cuit").asLong(),
                Invoice.text(node, "name"),
                Invoice.text(node, "type"),
                node.path("active").asBoolean(),
                Invoice.text(node, "vat_condition"),
                node.path("receiver_vat_condition_id").asInt(),
                Fiskit.toMap(node));
    }

    /**
     * @param type person or company
     * @param vatCondition registered, monotributo, exempt or not_registered
     * @param receiverVatConditionId the value to send as receiver.vat_condition_id when invoicing them
     */
    public record Taxpayer(long cuit, String name, String type, boolean active, String vatCondition,
            int receiverVatConditionId, Map<String, Object> raw) {
    }
}
