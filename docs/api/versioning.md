# API Versioning and Negotiation Policy

## Contract

Every public REST endpoint must use a major version in its path:

```text
/api/v1/invoices
/api/v2/invoices
```

The path is the authoritative version selector. An unversioned path such as `/api/invoices` is rejected with `400 API_VERSION_REQUIRED`; an unsupported version is rejected with `404 API_VERSION_UNSUPPORTED`. The service never redirects, defaults, or silently upgrades an API version.

## Optional compatibility assertions

Clients may send either or both of the following values to verify that the response is from the version they expect:

```http
X-API-Version: 1
Accept: application/vnd.ut-einvoice.v1+json
```

Those assertions must agree with the path. A request for `/api/v1/...` with `X-API-Version: 2` or `Accept: application/vnd.ut-einvoice.v2+json` is rejected with `400 API_VERSION_MISMATCH`. They cannot be used to select a version for an unversioned URL.

All versioned responses include `X-API-Version` and `X-Supported-API-Versions`. API responses also vary on the two negotiation headers so shared caches do not reuse an assertion-bearing response incorrectly.

## Stability and lifecycle

- `v1` is the frozen public contract. Additive, backward-compatible changes may be made in `v1`; removing or renaming fields, changing field meaning/type, changing status/error semantics, or changing authentication/authorization behavior requires a new major version.
- A breaking release is implemented in new controllers under `/api/v2/**`, with separate v2 request/response DTOs and a dedicated v2 OpenAPI group. Do not change v1 controllers or DTOs to implement v2 behavior.
- Only versions listed in `platform.api.supported-versions` are routable. The default is `v1`. Add `v2` to that property only in the same deployment that includes the secured v2 controller mappings and API documentation.
- Keep a supported version active until its announced sunset date. When a version is deprecated, add `Deprecation: true`, `Sunset`, and a successor `Link` header at that version's boundary; do not repoint its URLs to a newer version.

## Adding v2

1. Add v2-specific controllers under `/api/v2/**` and DTOs in a `v2` package. Reuse domain services where possible, not the v1 wire models.
2. Add the v2 authorization rules and a `/v3/api-docs/v2` OpenAPI group.
3. Deploy those mappings together with `API_SUPPORTED_VERSIONS=v1,v2`.
4. Add contract tests for both versions. Verify v1 responses have not changed before enabling clients to migrate.

This makes the coexistence period explicit: `/api/v1/...` remains stable while `/api/v2/...` can carry an intentionally incompatible contract.

For the release approval, deployment, client migration, rollback, and retirement process, follow the [Public API Major-Version Release Playbook](major-version-release-playbook.md).
