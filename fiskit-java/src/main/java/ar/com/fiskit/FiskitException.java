package ar.com.fiskit;

import com.fasterxml.jackson.databind.JsonNode;

/** An error answered by the API, or a network failure (status 0). */
public final class FiskitException extends RuntimeException {

    private final int status;
    private final String type;
    private final String code;
    private final String param;
    private final String requestId;

    FiskitException(int status, String type, String code, String message, String param, String requestId) {
        super(message);
        this.status = status;
        this.type = type;
        this.code = code;
        this.param = param;
        this.requestId = requestId;
    }

    static FiskitException from(int status, byte[] body, String headerRequestId) {
        JsonNode error;
        try {
            error = Fiskit.JSON.readTree(body).path("error");
        } catch (Exception e) {
            error = Fiskit.JSON.missingNode();
        }
        return new FiskitException(status,
                error.path("type").asText("api_error"),
                error.path("code").asText("http_" + status),
                error.path("message").asText("HTTP " + status),
                error.hasNonNull("param") ? error.get("param").asText() : null,
                error.hasNonNull("request_id") ? error.get("request_id").asText() : headerRequestId);
    }

    /** HTTP status, or 0 if the API could not be reached. */
    public int status() {
        return status;
    }

    /** For example invalid_request_error, authority_error or api_error. */
    public String type() {
        return type;
    }

    /** For example parameter_invalid or invoice_rejected. */
    public String code() {
        return code;
    }

    /** The field with the problem, if any. */
    public String param() {
        return param;
    }

    /** Give it to support to find the request. */
    public String requestId() {
        return requestId;
    }
}
