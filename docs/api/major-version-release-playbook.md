# Public API Major-Version Release Playbook

This is the end-to-end operating procedure for publishing, migrating to, supporting, and retiring API majors (`v2`, `v3`, and later) without changing an existing client contract.

## Current production contract

| Item | Current value |
| --- | --- |
| Public major | `v1` |
| URL prefix | `/api/v1/**` |
| Wire-contract release | `1.0.0-RELEASE` |
| Frozen since | 2026-09-18 |
| Lifecycle | `CURRENT` and frozen |
| Change policy | Additive, backward-compatible changes only |
| OpenAPI document | `/v3/api-docs/v1` |
| Version setting | `API_SUPPORTED_VERSIONS=v1` |

The exact live state is available to platform administrators at `GET /api/v1/master/api-versions` and in **API Integrations & Governance → API Versions**. The endpoint is read-only by design; activating a major is a deployment responsibility, not a runtime UI action.

## Non-negotiable rules

1. The URL chooses the contract. `/api/v1/...` always executes v1 behavior and is never redirected or upgraded to v2.
2. `X-API-Version` and `Accept: application/vnd.ut-einvoice.v{major}+json` are compatibility assertions. They must match the path; they cannot choose a version for an unversioned URL.
3. A breaking change always receives a new major, including changed field meaning or type, removed/renamed fields, changed error/status semantics, pagination behavior, authentication/authorization rules, or idempotency behavior.
4. A new major is not published until all application instances contain its controllers, security policy, OpenAPI document, and passing contract evidence.
5. Removing an old major requires a dated deprecation notice, client migration evidence, an approved sunset, and a release after the sunset date.

## Decide whether a new major is required

| Change | v1 allowed? | Action |
| --- | --- | --- |
| Add an optional response field | Yes | Add to v1 with contract test coverage. |
| Add an optional request field with a safe default | Yes | Add to v1, document default, and test older clients. |
| Add a new endpoint | Yes | Add under `/api/v1` if it does not alter an existing endpoint. |
| Remove or rename a field | No | Implement it under `/api/v2`. |
| Change a number to a string, nullable to required, or alter enum meaning | No | Implement it under `/api/v2`. |
| Change an existing response/error status or idempotency behavior | No | Implement it under `/api/v2`. |
| Tighten client permission required by an existing operation | No | Keep v1 unchanged; introduce the new rule in v2. |

When in doubt, treat the change as breaking. The cost of an extra major is lower than silently breaking fiscal or ERP integrations.

## Roles and approval gates

| Role | Required responsibility |
| --- | --- |
| API owner | Writes the version RFC, compatibility assessment, and migration guide. |
| Backend lead | Owns versioned routes, DTOs, OpenAPI, tests, and rollback compatibility. |
| Security lead | Approves authorization, tenant isolation, signing, rate limits, and audit coverage. |
| QA/release manager | Signs off contract regression, E2E, performance, and deployment evidence. |
| Platform/SRE | Executes blue/green publication, monitoring, rollback, and configuration rollout. |
| Integration support | Identifies clients, coordinates migration, and confirms readiness before sunset. |

No one person should approve and publish a breaking fiscal API release alone.

## Publishing v2 or v3

### 1. Create the version RFC

Record the following before coding:

- Business reason and affected v1 operations.
- Field-by-field and behavior-by-behavior compatibility matrix.
- The v2/v3 endpoint catalog, request/response/error examples, and OpenAPI changes.
- Migration instructions, client cohorts, communication dates, and a proposed support/sunset timeline.
- Rollback plan: v1 remains live; a v2 rollback removes only v2 traffic.

### 2. Build a separate wire contract

Keep the new transport layer separate from v1. Reuse domain services where appropriate, but never reuse a mutable v1 request/response model for a breaking implementation.

```text
src/main/java/et/ut/einvoice/
  invoicing/
    api/v2/
      InvoiceV2Controller.java
      CreateInvoiceV2Request.java
      InvoiceV2Response.java
```

Map the controller explicitly, for example:

```java
@RestController
@RequestMapping("/api/v2/invoices")
class InvoiceV2Controller { /* ... */ }
```

Keep `/api/v1/invoices` and its DTOs untouched. Add a new `/api/v3/...` layer when v3 is required; do not mutate v2 in place.

### 3. Add the new OpenAPI document and version metadata

Add a dedicated `GroupedOpenApi` group for the major so clients can download only that contract at `/v3/api-docs/v2` or `/v3/api-docs/v3`. Add the version to the deployment-owned catalog in `application.yml`:

```yaml
platform:
  api:
    current-version: v2
    supported-versions: ${API_SUPPORTED_VERSIONS:v1,v2}
    catalog:
      v1:
        lifecycle: STABLE
        contract-version: 1.0.0-RELEASE
        released-at: "2026-09-18"
        frozen: true
        change-policy: ADDITIVE_ONLY
      v2:
        lifecycle: CURRENT
        contract-version: 2.0.0-RELEASE
        released-at: "2026-11-15"
        frozen: false
        change-policy: BREAKING_CHANGES_ALLOWED
        notes: "Introduces the approved v2 invoice representation."
```

Do not add `v2` to `API_SUPPORTED_VERSIONS` until the artifact containing the v2 routes is ready for public traffic. A planned v3 can appear in the catalog as `DRAFT`, but it must not appear in `API_SUPPORTED_VERSIONS` before publication.

### 4. Produce release evidence

The release manager must attach evidence for every gate:

- v1 contract snapshot/diff confirms no v1 endpoint, schema, error, or security regression.
- v2/v3 request, response, validation, authorization, tenancy, idempotency, signature, and rate-limit tests pass.
- Both path and assertion negotiation are tested: matching path/header succeeds; mismatched `X-API-Version` or vendor `Accept` returns `400 API_VERSION_MISMATCH`; unversioned `/api/...` returns `400 API_VERSION_REQUIRED`.
- OpenAPI document validation and generated-client smoke tests pass for each supported major.
- Database migrations are backward-compatible with all supported API versions.
- Performance/load testing includes each major independently, with dashboards separated by route version.
- Security review approves new public routes and scopes.

### 5. Publish without mixed fleet behavior

Use blue/green deployment, an atomic all-instance rollout, or a dedicated version gateway. Do not expose `/api/v2` while any public application instance lacks v2 controllers or has `API_SUPPORTED_VERSIONS=v1`.

Recommended blue/green sequence:

1. Deploy the release candidate (v1 + v2 code) to the green environment with v2 disabled.
2. Run internal E2E, OpenAPI, security, and generated-client tests against green.
3. Set `API_SUPPORTED_VERSIONS=v1,v2` on every green instance and validate `/api/v2` there.
4. Shift external traffic to green atomically; preserve the blue environment for rollback.
5. Monitor error rates, latency, signature failures, tenant-boundary denials, and v1/v2 request counts independently.
6. Announce general availability only after the defined observation window is clean.

For v3, use the same process with `API_SUPPORTED_VERSIONS=v1,v2,v3` while v1 and v2 remain supported.

### 6. Post-publication verification

Run these checks against the public gateway, with real platform credentials where needed:

```text
GET /api/v1/...  -> X-API-Version: 1
GET /api/v2/...  -> X-API-Version: 2
GET /api/...     -> 400 API_VERSION_REQUIRED
GET /api/v1/... + X-API-Version: 2 -> 400 API_VERSION_MISMATCH
GET /api/v9/...  -> 404 API_VERSION_UNSUPPORTED
GET /v3/api-docs/v1 and /v3/api-docs/v2 -> expected isolated contracts
```

Confirm the admin version catalog shows the expected current, supported, and lifecycle values before communicating the release.

## Client migration and support

1. Publish a migration guide, OpenAPI link, changelog, sample payloads, SDK/generated-client version, and a testing sandbox date.
2. Segment clients by risk: fiscal POS and ERP integrations first, then lower-risk reporting/lookup clients.
3. Support dual-run or shadow validation where a client can compare v1 and v2 outputs without double-posting fiscal mutations.
4. Track each client as `not contacted`, `sandbox verified`, `production verified`, `exception approved`, or `migrated`.
5. Keep client communications, known incompatibilities, support tickets, and approval evidence attached to the release record.

## Deprecating and retiring v1 (or any later major)

Set the lifecycle to `DEPRECATED`, with explicit `deprecation-date` and `sunset-date` in the catalog. At the retiring version's API boundary emit:

```http
Deprecation: true
Sunset: Tue, 18 Nov 2027 00:00:00 GMT
Link: </api/v2/invoices>; rel="successor-version"
```

Do not use redirects or change the old version's response to v2 semantics. Keep the deprecated version fully functional through the announced sunset.

Before removal, verify that:

- no unapproved production client calls the retiring major;
- statutory, audit, reconciliation, and data-retention workflows remain intact;
- support has approved all exceptions;
- the release notes and migration guide were re-notified before the sunset;
- rollback remains possible by re-enabling the old major only if its artifact and data compatibility are still safe.

After the sunset, remove the old major from `API_SUPPORTED_VERSIONS` in an approved release and update the catalog to `SUNSET`. Remove code only in a later cleanup release after retention requirements have been met.

## Dashboard operating model

The master-admin **API Versions** tab is a release-control dashboard, not a mutable production switch. It shows:

- the current and supported live majors;
- route prefix, OpenAPI path, contract release, lifecycle, freeze state, and policy;
- required publication and retirement gates;
- the exact deployment setting needed to expose the next approved major.

This separation is intentional: a dashboard click cannot accidentally expose an incomplete major or cause some instances to accept a version that others reject.

## Release record template

Use one approved record per major:

```text
Major: v2
RFC / owner: API-2026-002 / <name>
Previous majors retained: v1
New contract release: 2.0.0-RELEASE
OpenAPI: /v3/api-docs/v2
Client migration guide: <link>
Security approval: <ticket/link>
Contract evidence: <build/link>
E2E and load evidence: <build/link>
Blue/green change: <deployment/link>
GA date: YYYY-MM-DD
Deprecation date: YYYY-MM-DD or not scheduled
Sunset date: YYYY-MM-DD or not scheduled
Rollback owner and procedure: <details>
```

See [API Versioning and Negotiation Policy](versioning.md) for the wire-level rules and [Backend API Freeze Specification](backend_api_freeze.md) for the v1 fiscal contract.
