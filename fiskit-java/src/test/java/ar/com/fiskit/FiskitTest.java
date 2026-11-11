package ar.com.fiskit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FiskitTest {

    private static final String INVOICE = """
            {"id": "inv_1", "object": "invoice", "status": "authorized", "voucher_type": "invoice_c",
             "point_of_sale": 1, "number": 42, "cae": "76451234567890", "cae_due_date": "2026-11-20",
             "authority_observations": [], "authority_errors": []}""";

    /** What the fake API answers, in order: status, body, extra header. A null status closes the connection. */
    private record Answer(Integer status, String body, String header, String headerValue) {
    }

    private record Received(String method, String path, String authorization, String idempotencyKey,
            String accept, String body) {
    }

    private final Deque<Answer> answers = new ArrayDeque<>();
    private final List<Received> received = new ArrayList<>();
    private HttpServer server;
    private Fiskit fiskit;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        fiskit = Fiskit.builder().apiKey("sk_test_x")
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).build();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        received.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("Idempotency-Key"),
                exchange.getRequestHeaders().getFirst("Accept"),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
        Answer answer = answers.removeFirst();
        if (answer.status() == null) {
            exchange.close();
            return;
        }
        if (answer.header() != null) {
            exchange.getResponseHeaders().add(answer.header(), answer.headerValue());
        }
        byte[] body = answer.body().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(answer.status(), body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    @Test
    void createsAnInvoiceWithTheKeyTheBodyAndAnIdempotencyKey() {
        answers.add(new Answer(200, INVOICE, null, null));

        Invoice invoice = fiskit.invoices().create(
                Map.of("voucher_type", "invoice_c", "amounts", Map.of("total", 1500)), "sale-1");

        assertTrue(invoice.authorized());
        assertEquals(42L, invoice.number());
        assertEquals("76451234567890", invoice.cae());
        assertEquals("2026-11-20", invoice.caeDueDate().toString());
        Received request = received.get(0);
        assertEquals("POST", request.method());
        assertEquals("/v1/invoices", request.path());
        assertEquals("Bearer sk_test_x", request.authorization());
        assertEquals("sale-1", request.idempotencyKey());
        assertTrue(request.body().contains("\"total\":1500"));
    }

    @Test
    void retriesAnOutageWithTheSameGeneratedIdempotencyKey() {
        answers.add(new Answer(503, "{\"error\":{\"code\":\"authority_unavailable\"}}", "Retry-After", "0"));
        answers.add(new Answer(502, "{}", null, null));
        answers.add(new Answer(200, INVOICE, null, null));

        fiskit.invoices().create(Map.of("voucher_type", "invoice_c", "amounts", Map.of("total", 1)));

        assertEquals(3, received.size());
        Set<String> keys = new HashSet<>();
        received.forEach(r -> keys.add(r.idempotencyKey()));
        assertEquals(1, keys.size());
        assertTrue(keys.iterator().next().matches("[0-9a-f-]{36}"));
    }

    @Test
    void apiErrorsBecomeAFiskitException() {
        answers.add(new Answer(400, """
                {"error": {"type": "invalid_request_error", "code": "parameter_invalid", "message": "Bad total",
                 "param": "amounts.total", "request_id": "req_1"}}""", null, null));

        FiskitException error = assertThrows(FiskitException.class, () -> fiskit.invoices().retrieve("inv_1"));

        assertEquals(400, error.status());
        assertEquals("parameter_invalid", error.code());
        assertEquals("amounts.total", error.param());
        assertEquals("req_1", error.requestId());
        assertEquals("Bad total", error.getMessage());
    }

    @Test
    void thePdfComesBackAsBytesAndTheTaxpayerByCuit() {
        answers.add(new Answer(200, "%PDF-1.7", null, null));
        answers.add(new Answer(200, """
                {"object": "taxpayer", "cuit": 30712345671, "name": "ACME SA", "type": "company", "active": true,
                 "vat_condition": "registered", "receiver_vat_condition_id": 1}""", null, null));

        assertArrayEquals("%PDF-1.7".getBytes(StandardCharsets.UTF_8), fiskit.invoices().pdf("inv_1"));
        Taxpayers.Taxpayer taxpayer = fiskit.taxpayers().retrieve(30712345671L);

        assertEquals("registered", taxpayer.vatCondition());
        assertEquals(1, taxpayer.receiverVatConditionId());
        assertEquals("application/pdf", received.get(0).accept());
        assertEquals("/v1/taxpayers/30712345671", received.get(1).path());
    }

    @Test
    void aPostWithoutIdempotencyKeyIsNeverRetried() {
        answers.add(new Answer(503, "{}", null, null));

        FiskitException error = assertThrows(FiskitException.class, () -> fiskit.invoices().email("inv_1", "a@b.com"));

        assertEquals(503, error.status());
        assertEquals(1, received.size());
    }

    @Test
    void verifiesWebhooksWithTheStandardWebhooksExample() {
        // Example from the Standard Webhooks specification
        String secret = "whsec_MfKQ9r8GKYqrTwjUPD8ILPZIo2LaLaSw";
        Map<String, String> headers = Map.of(
                "Webhook-Id", "msg_p5jXN8AQM9LWM0D4loKWxJek",
                "Webhook-Timestamp", "1614265330",
                "Webhook-Signature", "v1,g0hM9SsE+OTPJTGt/tmIKtSyZlE3uFJELVlNIOLJ1OE=");
        Instant sent = Instant.ofEpochSecond(1614265330);

        assertEquals(2432232314L, Webhook.verify("{\"test\": 2432232314}", headers, secret, 300, sent).get("test").asLong());
        assertThrows(IllegalArgumentException.class,
                () -> Webhook.verify("{\"test\": 1}", headers, secret, 300, sent));
        assertThrows(IllegalArgumentException.class,
                () -> Webhook.verify("{\"test\": 2432232314}", headers, secret, 300, sent.plusSeconds(3600)));
    }
}
