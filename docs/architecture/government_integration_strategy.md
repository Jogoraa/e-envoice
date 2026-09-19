# Government Integration Gateway Strategy
## Ministry of Revenues (MoR) / EIRS Adapter Specification
### Compliant Integration with Ethiopian Electronic Invoicing Management System

---

## 1. Architectural SPI Isolation

To prevent external changes in Ministry endpoints, protocol specifications, or payload schemas from destabilizing core invoice business logic, all government communications are strictly encapsulated behind a domain SPI:

```java
package et.ut.einvoice.government.domain;

public interface GovernmentRegistrationProvider {
    GovernmentAuthResult authenticate(GovernmentCredentials credentials);
    RegistrationResult registerInvoice(InvoiceRegistrationCommand command);
    BulkRegistrationResult registerBulkInvoices(BulkRegistrationCommand command);
    VerificationResult verifyInvoice(String irn);
    CancellationResult cancelInvoice(CancellationCommand command);
    ReceiptRegistrationResult registerReceipt(ReceiptRegistrationCommand command);
}
```

The concrete implementation is:
`et.ut.einvoice.government.infrastructure.mor.MorEirsRegistrationProvider`.

If the Ministry migrates from REST to gRPC or updates to an API v2 in the future, only the adapter is modified.

---

## 2. Authentication & Token Management

```text
  [UT Government Gateway]                                [MoR IAM Gateway]
             │                                                   │
             │ 1. POST /auth/login                               │
             │    { clientId, clientSecret, apiKey, tin }        │
             ├──────────────────────────────────────────────────►│
             │                                                   │
             │ 2. Returns: accessToken, refreshToken, expiresIn  │
             │◄──────────────────────────────────────────────────┤
             │                                                   │
             │ 3. Cache Token in Redis (TTL = expiresIn - 60s)   │
             │    Subsequent calls reuse cached token            │
```

- When the token reaches its refresh threshold (e.g., 5 minutes before expiry), the gateway dispatches a non-blocking refresh call via `POST /auth/refresh-token`.
- Credentials are retrieved on demand from HashiCorp Vault using the tenant's secure secret path.

---

## 3. Digital Signature & Payload Canonicalization (Bouncy Castle)

Under Article 4(6) of Directive No. 1142/2026, payloads submitted to the Ministry must be digitally signed using an INSA-certified private key:
1. **Canonical JSON Serialization**: Whitespace normalized, object keys sorted lexicographically, UTF-8 encoded.
2. **Cryptographic Hashing**: SHA-256 hash computed across the canonical payload:
   `PayloadHash = Hex(SHA-256(canonical_json))`.
3. **Digital Signature**: Bouncy Castle `SHA256withRSA` or `SHA256withECDSA` generates a Base64-encoded signature using the tenant's private key (`private_key.pem`).
4. The signature and X.509 certificate string are attached to the submission envelope.

---

## 4. Sequence Auto-Synchronization (Auto-Healing)

A critical discovery from the existing MoR integration prototype (`generate_invoice.js` lines 228–252) is that the Ministry gateway enforces strict server-side sequence counters and document numbers. If an external client gets out of sync, MoR returns:
```json
{
  "statusCode": 400,
  "body": [
    { "portion": "DocumentDetails", "errorMessage": ["Invalid document number, expected : 14"] },
    { "portion": "SourceSystem", "errorMessage": ["Invalid invoice counter, expected : 14"] }
  ]
}
```

### Auto-Sync Protocol in `MorEirsRegistrationProvider`:
1. The gateway parses the error portion and regex matches `expected\s*:\s*(\d+)`.
2. If both `DocumentNumber` and `InvoiceCounter` sequence mismatches are detected:
   - The provider updates the tenant's sequence tracker in PostgreSQL.
   - Automatically rebuilds the payload with the server-demanded sequence.
   - Re-signs and re-submits the invoice seamlessly within the same transaction (up to 3 attempts).
3. This eliminates human operator intervention during sequence desynchronization.

---

## 5. Resilience, Circuit Breaking & Safe Retries

| Scenario | Behavior & Protocol |
|---|---|
| **HTTP 429 Too Many Requests** | Exponential backoff with jitter (initial wait 3s, multiplier 2.0, max 3 retries). |
| **HTTP 5xx Server Error / Timeout** | Circuit breaker opens after 5 consecutive failures within 30s. Automatically degrades to `OFFLINE_BUFFERED` mode to prevent blocking customer checkout. |
| **Idempotency on Gateway Retries** | Every outbound request generates a deterministic `submission_id` and `idempotency_key` stored in `government_submissions`. A retry will never create a duplicate tax transaction at the Ministry. |
