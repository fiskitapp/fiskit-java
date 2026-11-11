# FisKit for Java and Spring Boot

[Español más abajo](#español)

Official client of the [FisKit](https://fiskit.com.ar) API: Argentine electronic invoices with ARCA (formerly AFIP) in a single request. Java 17 or later.

## Spring Boot

```xml
<dependency>
    <groupId>ar.com.fiskit</groupId>
    <artifactId>fiskit-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

```yaml
fiskit:
  api-key: ${FISKIT_API_KEY}
  webhook-secret: ${FISKIT_WEBHOOK_SECRET}
```

```java
@Service
class Billing {
    private final Fiskit fiskit;

    Billing(Fiskit fiskit) {
        this.fiskit = fiskit;
    }

    Invoice invoice(Order order) {
        // Your order id as the key: a retry never issues a second invoice
        return fiskit.invoices().create(
                Map.of("voucher_type", "invoice_c", "amounts", Map.of("total", order.total())),
                "order-" + order.id());
    }
}
```

## Plain Java

```xml
<dependency>
    <groupId>ar.com.fiskit</groupId>
    <artifactId>fiskit-java</artifactId>
    <version>0.1.0</version>
</dependency>
```

```java
Fiskit fiskit = Fiskit.builder().apiKey(System.getenv("FISKIT_API_KEY")).build();

Invoice invoice = fiskit.invoices().create(
        Map.of("voucher_type", "invoice_c", "amounts", Map.of("total", 1500)), "sale-8812");
byte[] pdf = fiskit.invoices().pdf(invoice.id());                 // PDF with ARCA's QR code
fiskit.invoices().email(invoice.id(), "customer@example.com");
var taxpayer = fiskit.taxpayers().retrieve(30712345671L);         // name and VAT condition of a CUIT
```

**Webhooks:** `Webhook.verify(rawBody, headers, secret)` checks the Standard Webhooks signature and returns the event.

`sk_test_...` keys issue against ARCA's homologation environment (no fiscal value); `sk_live_...` keys issue real invoices. Outages are retried with the same idempotency key. Errors are `FiskitException` with `status()`, `type()`, `code()`, `param()` and `requestId()`.

Full documentation: https://fiskit.com.ar/developers

Coding with an AI agent? Point it to https://fiskit.com.ar/llms.txt: the API rules, errors and examples in plain text.

---

## Español

Cliente oficial de la API de [FisKit](https://fiskit.com.ar): facturas electrónicas de ARCA (ex AFIP) con un solo pedido. Para Java 17 o superior.

**Con Spring Boot**, agregás `fiskit-spring-boot-starter`, ponés `fiskit.api-key` en `application.yml` y pedís `Fiskit` por inyección:

```java
Invoice factura = fiskit.invoices().create(
        Map.of("voucher_type", "invoice_c", "amounts", Map.of("total", 1500)),
        "venta-8812"); // con el id de tu venta, un reintento nunca duplica la factura
```

Las claves `sk_test_...` emiten en homologación, sin validez fiscal; las `sk_live_...` emiten facturas reales.

Documentación completa: https://fiskit.com.ar/developers

¿Programás con un agente de IA? Pasale https://fiskit.com.ar/llms.txt: las reglas, errores y ejemplos de la API en texto plano.
