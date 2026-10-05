# UT Electronic Invoicing (UT Invoice) — Route Inventory & Recovery Architecture
## Comprehensive Multi-Surface Route Register & Audit

**Document Version:** 1.0.0  
**Compliance Standard:** FDRE Ministry of Revenues Directive No. 1142/2026 & INSA System Integrity Guidelines  
**Status:** FULLY AUDITED & VERIFIED  
**Date:** October 2026  

---

### 1. Executive Summary & Root Cause Analysis

#### A. Root Cause of `/admin/system-integrity` Route Failure
In the UT Invoice platform, three isolated application surfaces operate within the unified Flutter codebase, selected at launch via `--dart-define=APP_SURFACE=tenant|saas|admin`:
1. **Tenant / Client App:** Uses `appRouterProvider` (`frontend/lib/app/router/app_router.dart`).
2. **SaaS Management App:** Uses `saasRouterProvider` (`frontend/lib/apps/saas_management/saas_router.dart`).
3. **Master Admin App:** Uses `masterAdminRouterProvider` (`frontend/lib/apps/master_admin/master_admin_router.dart`).

When the Master Admin shell was updated with navigation links for:
- `/admin/authority-investigation`
- `/admin/provider-exit`
- `/admin/system-integrity`

These routes were initially wired into `app_router.dart` but were omitted from `masterAdminRouterProvider` in `frontend/lib/apps/master_admin/master_admin_router.dart`. Consequently, running with `--dart-define=APP_SURFACE=admin` caused GoRouter to throw:
`GoException: no routes for location: /admin/system-integrity`

Furthermore, none of the three routers implemented an `errorBuilder`, causing GoRouter to render its raw default framework page with unhandled exception text and an unsafe generic link.

#### B. Redesign & Architectural Resolution
1. **Registered All Missing Routes:**
   - In `master_admin_router.dart`: Added `/admin/authority-investigation`, `/admin/provider-exit`, `/admin/system-integrity`.
   - In `saas_router.dart`: Added `/saas/lifecycle-notifications`, `/saas/marketplace`, `/saas/tenant-exit-oversight`, `/saas/provider-tiers`, `/saas/onboarding/wizard`.
2. **Central Route Recovery System:**
   - Created `RouteRecoveryService` to detect active application surfaces (`AppSurface`), sanitize attempted URLs (stripping tokens, passwords, and API keys), and classify failures (`notFound` 404 vs `accessDenied` 403 vs `sessionRequired` 401).
   - Created `RouteRecoveryScreen` with premium desktop and mobile layouts, UT Invoice branding, and app-specific safe home returns.
   - Enforced strict cross-app security boundaries so unprivileged tenant users attempting admin URLs receive generic access restriction rather than internal route disclosures.
   - Wired `errorBuilder` into all three routers (`masterAdminRouterProvider`, `saasRouterProvider`, `appRouterProvider`).

---

### 2. Multi-Surface Route Inventory Table

| Route | App Surface | Screen / Component | Permission / Claim | Navigation Source | Exists | Reachable | Fallback | Status |
| :--- | :--- | :--- | :--- | :--- | :---: | :---: | :--- | :---: |
| `/` | Root / Public | `WorkspaceSelectionScreen` / `DashboardScreen` | Public / Session | Initial App Launch | YES | YES | `/login` | `VALID` |
| `/workspace` | Root / Public | `WorkspaceSelectionScreen` | Public | Workspace Switcher | YES | YES | `/` | `VALID` |
| `/login` | Tenant Client | `LoginScreen` | Public | Auth Redirect / Logout | YES | YES | `/dashboard` | `VALID` |
| `/tenant/login` | Tenant Client | `LoginScreen` | Public | Deep Link / Workspace | YES | YES | `/dashboard` | `VALID` |
| `/saas/login` | SaaS Management | `SaasLoginScreen` | Public | SaaS Shell Logout | YES | YES | `/saas/dashboard` | `VALID` |
| `/master/login` | Master Admin | `MasterAdminLoginScreen` | Public | Alias Entrypoint | YES | YES | `/admin/dashboard` | `VALID` |
| `/admin/login` | Master Admin | `MasterAdminLoginScreen` | Public | Admin Shell Logout | YES | YES | `/admin/dashboard` | `VALID` |
| `/auth/invitations/accept` | Cross-Cutting | `InvitationAcceptanceScreen` | Public (Signed Token) | Email / SMS Invitation | YES | YES | `/login` | `VALID` |
| `/verify/:irn` | Public Portal | `PublicVerificationScreen` | Public | QR Code Scan | YES | YES | `/` | `VALID` |
| `/dashboard` | Tenant Client | `DashboardScreen` | Authenticated Tenant | Sidebar Menu Item 1 | YES | YES | `/login` | `VALID` |
| `/invoices` | Tenant Client | `InvoiceListScreen` | `invoice:read` | Sidebar Menu Item 2 | YES | YES | `/dashboard` | `VALID` |
| `/invoices/new` | Tenant Client | `InvoiceCreationWizard` | `invoice:create` | Header Button / Quick Action | YES | YES | `/invoices` | `VALID` |
| `/invoices/:id` | Tenant Client | `InvoiceDetailScreen` | `invoice:read` | Table Row Click | YES | YES | `/invoices` | `VALID` |
| `/adjustments` | Tenant Client | `TaxAdjustmentScreen` | `adjustment:create` | Sidebar Menu Item 3 | YES | YES | `/dashboard` | `VALID` |
| `/catalog/products` | Tenant Client | `ProductCatalogScreen` | `catalog:read` | Sidebar Menu Item 4 | YES | YES | `/dashboard` | `VALID` |
| `/catalog/services` | Tenant Client | `ServiceCatalogScreen` | `catalog:read` | Sidebar Menu Item 5 | YES | YES | `/dashboard` | `VALID` |
| `/catalog/categories` | Tenant Client | `CategoryManagementScreen` | `catalog:read` | Sidebar Menu Item 6 | YES | YES | `/dashboard` | `VALID` |
| `/inventory` | Tenant Client | `InventoryScreen` | `inventory:read` | Sidebar Menu Item 7 | YES | YES | `/dashboard` | `VALID` |
| `/customers` | Tenant Client | `CustomerListScreen` | `customer:read` | Sidebar Menu Item 8 | YES | YES | `/dashboard` | `VALID` |
| `/receipts/cash` | Tenant Client | `CashReceiptScreen` | `receipt:cash:create` | Sidebar Menu Item 9 | YES | YES | `/dashboard` | `VALID` |
| `/receipts/purchase-voucher` | Tenant Client | `PurchaseVoucherScreen` | `voucher:create` | Sidebar Menu Item 10 | YES | YES | `/dashboard` | `VALID` |
| `/receipts/withholding` | Tenant Client | `WithholdingReceiptScreen` | `withholding:create` | Sidebar Menu Item 11 | YES | YES | `/dashboard` | `VALID` |
| `/invoices/credit-settlement` | Tenant Client | `CreditSettlementScreen` | `credit:settle` | Sidebar Menu Item 12 | YES | YES | `/dashboard` | `VALID` |
| `/invoices/cancellations` | Tenant Client | `CancellationManagementScreen` | `invoice:cancel` | Sidebar Menu Item 13 | YES | YES | `/dashboard` | `VALID` |
| `/offline/operations` | Tenant Client | `OfflineOperationsScreen` | `offline:manage` | Sidebar Menu Item 14 | YES | YES | `/dashboard` | `VALID` |
| `/offline/manual-reconciliation` | Tenant Client | `ManualInvoiceReconciliationScreen`| `offline:manual` | Sidebar Menu Item 15 | YES | YES | `/dashboard` | `VALID` |
| `/devices/compliance` | Tenant Client | `DeviceComplianceScreen` | `device:manage` | Sidebar Menu Item 16 | YES | YES | `/dashboard` | `VALID` |
| `/government/credentials` | Tenant Client | `GovernmentCredentialsScreen` | `credentials:manage` | Sidebar Menu Item 17 | YES | YES | `/dashboard` | `VALID` |
| `/government/signature-health` | Tenant Client | `SignatureHealthScreen` | `signature:health:read`| Sidebar Menu Item 18 | YES | YES | `/dashboard` | `VALID` |
| `/compliance/exempt-reporting` | Tenant Client | `ExemptSectorReportingScreen` | `exempt:reporting` | Sidebar Menu Item 19 | YES | YES | `/dashboard` | `VALID` |
| `/compliance/retention` | Tenant Client | `RetentionScheduleScreen` | `retention:read` | Sidebar Menu Item 20 | YES | YES | `/dashboard` | `VALID` |
| `/compliance/tenant-exit` | Tenant Client | `TenantExitScreen` | `tenant:exit` | Sidebar Menu Item 21 | YES | YES | `/dashboard` | `VALID` |
| `/offline/queue` | Tenant Client | `OfflineQueueScreen` | `offline:read` | Sidebar Badge / Status Pill | YES | YES | `/dashboard` | `VALID` |
| `/government/status` | Tenant Client | `GovernmentStatusScreen` | `government:status:read`| Network Status Indicator | YES | YES | `/dashboard` | `VALID` |
| `/reports` | Tenant Client | `ReportsScreen` | `reports:read` | Sidebar Menu Item 22 | YES | YES | `/dashboard` | `VALID` |
| `/audit` | Tenant Client | `AuditScreen` | `audit:read` | Sidebar Menu Item 23 | YES | YES | `/dashboard` | `VALID` |
| `/settings` | Tenant Client | `SettingsScreen` | `tenant:settings` | Sidebar Menu Item 24 | YES | YES | `/dashboard` | `VALID` |
| `/saas/dashboard` | SaaS Management | `SaasDashboardScreen` | `ROLE_SAAS_ADMIN` | Sidebar Menu Item 1 | YES | YES | `/saas/login` | `VALID` |
| `/saas/onboarding` | SaaS Management | `TenantOnboardingWizard` | `tenant:onboard` | Sidebar Menu Item 2 | YES | YES | `/saas/dashboard` | `VALID` |
| `/saas/onboarding/wizard` | SaaS Management | `TenantOnboardingWizard` | `tenant:onboard` | Direct Action Button | YES | YES | `/saas/dashboard` | `VALID` |
| `/saas/tenants` | SaaS Management | `TenantLifecycleScreen` | `tenant:lifecycle` | Sidebar Menu Item 3 | YES | YES | `/saas/dashboard` | `VALID` |
| `/saas/subscriptions` | SaaS Management | `SubscriptionsScreen` | `subscription:manage` | Sidebar Menu Item 4 | YES | YES | `/saas/dashboard` | `VALID` |
| `/saas/usage` | SaaS Management | `UsageMeteringScreen` | `usage:read` | Sidebar Menu Item 5 | YES | YES | `/saas/dashboard` | `VALID` |
| `/saas/lifecycle-notifications` | SaaS Management | `TenantLifecycleNotificationsScreen` | `lifecycle:notify` | Sidebar Menu Item 6 | YES | YES | `/saas/dashboard` | `VALID` |
| `/saas/marketplace` | SaaS Management | `MarketplaceManagementScreen` | `marketplace:manage` | Sidebar Menu Item 7 | YES | YES | `/saas/dashboard` | `VALID` |
| `/saas/tenant-exit-oversight` | SaaS Management | `SaasTenantExitOversightScreen` | `tenant:exit:oversight` | Sidebar Menu Item 8 | YES | YES | `/saas/dashboard` | `VALID` |
| `/saas/provider-tiers` | SaaS Management | `ProviderTierDashboardScreen` | `provider:tier:read` | Sidebar Menu Item 9 | YES | YES | `/saas/dashboard` | `VALID` |
| `/saas/reports` | SaaS Management | `SaasReportDefinitionsScreen` | `reports:manage` | Sidebar Menu Item 10 | YES | YES | `/saas/dashboard` | `VALID` |
| `/saas/support` | SaaS Management | `SupportDiagnosticsScreen` | `support:diagnostics` | Sidebar Menu Item 11 | YES | YES | `/saas/dashboard` | `VALID` |
| `/admin/dashboard` | Master Admin | `MasterAdminDashboardScreen` | `ROLE_PLATFORM_ADMIN` | Sidebar Menu Item 1 | YES | YES | `/admin/login` | `VALID` |
| `/admin/users` | Master Admin | `MasterUsersDirectoryScreen` | `platform:users` | Sidebar Menu Item 2 | YES | YES | `/admin/dashboard` | `VALID` |
| `/admin/roles` | Master Admin | `MasterRolesPermissionsScreen` | `platform:roles` | Sidebar Menu Item 3 | YES | YES | `/admin/dashboard` | `VALID` |
| `/admin/roles-permissions` | Master Admin | `MasterRolesPermissionsScreen` | `platform:roles` | Alias / Direct Link | YES | YES | `/admin/dashboard` | `VALID` |
| `/admin/access-reviews` | Master Admin | `MasterAccessReviewsScreen` | `platform:access_review`| Sidebar Menu Item 4 | YES | YES | `/admin/dashboard` | `VALID` |
| `/admin/sessions` | Master Admin | `MasterSessionsDevicesScreen` | `platform:sessions` | Sidebar Menu Item 5 | YES | YES | `/admin/dashboard` | `VALID` |
| `/admin/tenants` | Master Admin | `TenantOversightScreen` | `platform:tenants` | Sidebar Menu Item 6 | YES | YES | `/admin/dashboard` | `VALID` |
| `/admin/api-management` | Master Admin | `MasterApiManagementScreen` | `platform:api_keys` | Sidebar Menu Item 7 | YES | YES | `/admin/dashboard` | `VALID` |
| `/admin/readiness` | Master Admin | `DeploymentReadinessScreen` | `platform:readiness` | Sidebar Menu Item 8 | YES | YES | `/admin/dashboard` | `VALID` |
| `/admin/gateway` | Master Admin | `GovernmentGatewayMonitorScreen`| `platform:gateway` | Sidebar Menu Item 9 | YES | YES | `/admin/dashboard` | `VALID` |
| `/admin/audit` | Master Admin | `SecurityAuditScreen` | `platform:audit` | Sidebar Menu Item 10 | YES | YES | `/admin/dashboard` | `VALID` |
| `/admin/config` | Master Admin | `PlatformConfigScreen` | `platform:config` | Sidebar Menu Item 11 | YES | YES | `/admin/dashboard` | `VALID` |
| `/admin/system/environment` | Master Admin | `MasterEnvironmentSecretsScreen`| `platform:secrets` | Sidebar Menu Item 12 | YES | YES | `/admin/dashboard` | `VALID` |
| `/admin/compliance-governance` | Master Admin | `DirectiveComplianceGovernanceScreen`| `compliance:governance` | Sidebar Menu Item 13 | YES | YES | `/admin/dashboard` | `VALID` |
| `/admin/authority-investigation`| Master Admin | `AuthorityInvestigationPortalScreen` | `authority:investigate` | Sidebar Menu Item 14 | YES | YES | `/admin/dashboard` | `VALID` |
| `/admin/provider-exit` | Master Admin | `ProviderExitGovernanceScreen` | `provider:exit` | Sidebar Menu Item 15 | YES | YES | `/admin/dashboard` | `VALID` |
| `/admin/system-integrity` | Master Admin | `SystemIntegrityScreen` | `system:integrity:read` | Sidebar Menu Item 16 | YES | YES | `/admin/dashboard` | `VALID` |
| `/admin/account/settings` | Master Admin | `MasterAccountSettingsScreen` | `platform:account` | Bottom Account Profile | YES | YES | `/admin/dashboard` | `VALID` |

---

### 3. Route Recovery Fallback Matrix

| User Context | Attempted Destination | Classified Failure | Screen Title | Rendered Action | Destination Target |
| :--- | :--- | :---: | :--- | :--- | :--- |
| **Authenticated Tenant** | Invalid Tenant Path (e.g. `/invoices/invalid-doc`) | `404 Not Found` | Page unavailable | Return to Client Dashboard | `/dashboard` |
| **Authenticated Tenant** | Admin Path (e.g. `/admin/system-integrity`) | `403 Access Restricted` | Access restricted (path hidden) | Return to Client Dashboard | `/dashboard` |
| **Authenticated SaaS** | Invalid SaaS Path (e.g. `/saas/unknown-billing`) | `404 Not Found` | Management page unavailable | Return to SaaS Dashboard | `/saas/dashboard` |
| **Authenticated SaaS** | Admin Path (e.g. `/admin/system/environment`) | `403 Access Restricted` | Access restricted (path hidden) | Return to SaaS Dashboard | `/saas/dashboard` |
| **Authenticated Master Admin** | Invalid Admin Path (e.g. `/admin/nonexistent`) | `404 Not Found` | Admin page unavailable | Return to Admin Dashboard | `/admin/dashboard` |
| **Unauthenticated** | Any Private Path (e.g. `/invoices/new`) | `401 Session Required` | Session required | Sign In to UT Invoice | `/login` |
| **Expired Session** | Any Private Path | `401 Session Required` | Session required | Sign In to UT Invoice | `/login` |
