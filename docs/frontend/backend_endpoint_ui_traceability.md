# UT INVOICE — BACKEND ENDPOINT TO FRONTEND UI TRACEABILITY

**Audit Date**: 2026-10-05  
**Audit Git SHA**: `056473b9c01f8318eb48042a00db378bd40e8eca`  
**Controllers Cataloged**: 49 Spring Boot `@RestController`s  
**Human Workflow Parity**: 100% of human-facing endpoints are mapped to an active Flutter screen, typed API service, registered route, and verified by E2E test.

---

## 1. Master Admin Capabilities (`UtMasterAdminApp`)

| Controller | HTTP Method | Endpoint | Permission | Human Workflow? | Owning App | Flutter API Service | Screen | Route | Navigation Entry | E2E Test | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `AuthorityAuditController` | GET | `/api/v1/authority/system-checksum` | `PLATFORM_SUPER_ADMIN` | YES | Master Admin | `ApiClient` | `SystemIntegrityScreen` | `/admin/system-integrity` | System Integrity | `e2e_three_app_business_workflows_test.dart` (Test 1) | COMPLETE |
| `AuthorityAuditController` | POST | `/api/v1/authority/investigation-warrant` | `PLATFORM_SUPER_ADMIN` | YES | Master Admin | `ApiClient` | `AuthorityInvestigationPortalScreen` | `/admin/authority-investigation` | Authority Investigation | `e2e_three_app_business_workflows_test.dart` (Test 2) | COMPLETE |
| `ProviderExitGovernanceController` | GET / POST | `/api/v1/authority/provider-exit-plan` | `PLATFORM_SUPER_ADMIN` | YES | Master Admin | `ApiClient` | `ProviderExitGovernanceScreen` | `/admin/provider-exit` | Provider Exit | `e2e_three_app_business_workflows_test.dart` (Test 3) | COMPLETE |
| `MasterComplianceEvidenceController` | GET | `/api/v1/authority/compliance-summary` | `PLATFORM_SUPER_ADMIN` | YES | Master Admin | `ApiClient` | `DirectiveComplianceGovernanceScreen` | `/admin/directive-compliance` | Directive Compliance | `e2e_three_app_business_workflows_test.dart` (Test 4) | COMPLETE |
| `PlatformReadinessController` | GET | `/api/v1/master/readiness` | `PLATFORM_SUPER_ADMIN` | YES | Master Admin | `MasterAdminApiClient` | `DeploymentReadinessScreen` | `/admin/system-health` | System Health | `master_admin_navigation_and_roles_test.dart` | COMPLETE |
| `MasterApiClientController` | GET / POST | `/api/v1/master/api-clients` | `PLATFORM_SUPER_ADMIN` | YES | Master Admin | `MasterAdminApiClient` | `MasterApiManagementScreen` | `/admin/api-management` | API Management | `master_admin_navigation_and_roles_test.dart` | COMPLETE |
| `MasterEnvironmentController` | GET / POST | `/api/v1/master/environment/summary` | `PLATFORM_SUPER_ADMIN` | YES | Master Admin | `MasterAdminApiClient` | `MasterEnvironmentSecretsScreen` | `/admin/environment` | Environment & Secrets | `master_admin_navigation_and_roles_test.dart` | COMPLETE |
| `MasterWebhookOversightController` | GET | `/api/v1/master/webhooks` | `PLATFORM_SUPER_ADMIN` | YES | Master Admin | `MasterAdminApiClient` | `MasterApiManagementScreen` | `/admin/notification-providers` | Notification Providers | `master_admin_navigation_and_roles_test.dart` | COMPLETE |
| `AuditVerificationController` | GET / POST | `/api/v1/master/audit/records` | `PLATFORM_SUPER_ADMIN` | YES | Master Admin | `MasterAdminApiClient` | `ImmutableAuditInspectionScreen` | `/admin/audit` | Audit Trail | `master_admin_navigation_and_roles_test.dart` | COMPLETE |
| `PlatformAuthController` | POST | `/api/v1/master/auth/login` | Public | YES | Public/Auth | `MasterAdminApiClient` | `MasterAdminLoginScreen` | `/admin/login` | Login Form | `workspace_and_desktop_entry_test.dart` | COMPLETE |

---

## 2. Commercial SaaS Capabilities (`UtSaasManagementApp`)

| Controller | HTTP Method | Endpoint | Permission | Human Workflow? | Owning App | Flutter API Service | Screen | Route | Navigation Entry | E2E Test | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `SaasTenantManagementController` | GET / POST | `/api/v1/saas/tenants` | `SAAS_ADMIN` | YES | SaaS Management | `SaasManagementApiClient` | `SaasTenantDirectoryScreen` | `/saas/tenants` | Tenant Directory | `saas_management_navigation_test.dart` | COMPLETE |
| `SaasTenantManagementController` | POST | `/api/v1/saas/tenants/onboard` | `SAAS_ADMIN` | YES | SaaS Management | `SaasManagementApiClient` | `SaasTenantOnboardingScreen` | `/saas/onboarding` | Tenant Onboarding | `saas_management_navigation_test.dart` | COMPLETE |
| `SaasSubscriptionController` | GET / PUT | `/api/v1/saas/plans` | `SAAS_ADMIN` | YES | SaaS Management | `SaasManagementApiClient` | `SubscriptionsScreen` | `/saas/subscriptions` | Subscriptions & Plans | `subscriptions_screen_test.dart` | COMPLETE |
| `TenantLifecycleController` | GET / POST | `/api/v1/taxpayer-lifecycle/notifications` | `SAAS_ADMIN` | YES | SaaS Management | `ApiClient` | `TenantLifecycleNotificationsScreen` | `/saas/lifecycle` | Lifecycle Notifications | `e2e_three_app_business_workflows_test.dart` (Test 5) | COMPLETE |
| `MarketplaceController` | GET / POST | `/api/v1/marketplace/merchants` | `SAAS_ADMIN` | YES | SaaS Management | `ApiClient` | `MarketplaceManagementScreen` | `/saas/marketplace` | Marketplace Operations | `e2e_three_app_business_workflows_test.dart` (Test 6) | COMPLETE |
| `TenantExitController` | GET / POST | `/api/v1/saas/tenants/exit-requests` | `SAAS_ADMIN` | YES | SaaS Management | `SaasManagementApiClient` | `SaasTenantExitOversightScreen` | `/saas/exit-oversight` | Portability & Exit | `e2e_three_app_business_workflows_test.dart` (Test 7) | COMPLETE |
| `ProviderTierComplianceController` | GET / PUT | `/api/v1/provider-tier/current` | `SAAS_ADMIN` | YES | SaaS Management | `ApiClient` | `ProviderTierDashboardScreen` | `/saas/provider-tiering` | Provider Tiering | `e2e_three_app_business_workflows_test.dart` (Test 8) | COMPLETE |
| `BusinessSectorController` | GET | `/api/v1/saas/sectors` | `SAAS_ADMIN` | YES | SaaS Management | `SaasManagementApiClient` | `SaasTenantOnboardingScreen` | `/saas/onboarding` | Form Field Selector | `saas_management_navigation_test.dart` | COMPLETE |
| `SaasReportManagementController` | GET | `/api/v1/saas/reports` | `SAAS_ADMIN` | YES | SaaS Management | `SaasManagementApiClient` | `SaasManagementDashboardScreen` | `/saas/dashboard` | Dashboard Charts | `saas_management_navigation_test.dart` | COMPLETE |

---

## 3. Tenant Client Capabilities (`UtTenantClientApp`)

| Controller | HTTP Method | Endpoint | Permission | Human Workflow? | Owning App | Flutter API Service | Screen | Route | Navigation Entry | E2E Test | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `InvoiceController` | GET / POST | `/api/v1/invoices` | `INVOICE_CREATE` / `INVOICE_READ` | YES | Tenant Client | `ApiClient` | `InvoiceListScreen` / `CreateInvoiceScreen` | `/invoices`, `/invoices/create` | Invoices | `flutter_offline_sync_adversarial_test.dart` | COMPLETE |
| `CashReceiptController` | GET / POST | `/api/v1/cash-receipts` | `INVOICE_CREATE` | YES | Tenant Client | `ApiClient` | `CashReceiptScreen` | `/cash-receipts` | Cash Receipts | `e2e_three_app_business_workflows_test.dart` (Test 9) | COMPLETE |
| `PurchaseVoucherController` | GET / POST | `/api/v1/purchase-vouchers` | `INVOICE_CREATE` | YES | Tenant Client | `ApiClient` | `PurchaseVoucherScreen` | `/purchase-vouchers` | Purchase Vouchers | `e2e_three_app_business_workflows_test.dart` (Test 10) | COMPLETE |
| `WithholdingReceiptController` | GET / POST | `/api/v1/withholding-receipts` | `INVOICE_CREATE` | YES | Tenant Client | `ApiClient` | `WithholdingReceiptScreen` | `/withholding` | Withholding | `e2e_three_app_business_workflows_test.dart` (Test 11) | COMPLETE |
| `CreditSettlementController` | GET / POST | `/api/v1/credit-settlements` | `INVOICE_CREATE` | YES | Tenant Client | `ApiClient` | `CreditSettlementScreen` | `/credit-settlement` | Credit Settlement | `e2e_three_app_business_workflows_test.dart` (Test 12) | COMPLETE |
| `CancellationController` | GET / POST | `/api/v1/cancellations` | `INVOICE_CANCEL` | YES | Tenant Client | `ApiClient` | `CancellationManagementScreen` | `/cancellations` | Cancellations | `e2e_three_app_business_workflows_test.dart` (Test 13) | COMPLETE |
| `DeviceOfflineAllocationController` | GET / POST | `/api/v1/offline/allocations` | `OFFLINE_OPERATE` | YES | Tenant Client | `ApiClient` | `OfflineOperationsScreen` | `/offline` | Offline Operations | `e2e_three_app_business_workflows_test.dart` (Test 14) | COMPLETE |
| `ManualInvoiceController` | GET / POST | `/api/v1/manual-reconciliation` | `INVOICE_CREATE` | YES | Tenant Client | `ApiClient` | `ManualInvoiceReconciliationScreen` | `/offline/manual-reconciliation` | Manual Fallback | `e2e_three_app_business_workflows_test.dart` (Test 15) | COMPLETE |
| `DeviceManagementController` | GET / POST | `/api/v1/devices` | `DEVICE_MANAGE` | YES | Tenant Client | `ApiClient` | `DeviceComplianceScreen` | `/devices` | mPOS & Devices | `e2e_three_app_business_workflows_test.dart` (Test 16) | COMPLETE |
| `TenantGovernmentCredentialController` | GET / POST | `/api/v1/government/credentials` | `TENANT_ADMIN` | YES | Tenant Client | `ApiClient` | `GovernmentCredentialsScreen` | `/government/credentials` | Government Integration | `e2e_three_app_business_workflows_test.dart` (Test 17) | COMPLETE |
| `TenantGovernmentCredentialController` | GET | `/api/v1/government/signature-health` | `TENANT_ADMIN` | YES | Tenant Client | `ApiClient` | `SignatureHealthScreen` | `/government/signature-health` | Signature Health | `e2e_three_app_business_workflows_test.dart` (Test 18) | COMPLETE |
| `PortabilityApiController` | GET / POST | `/api/v1/tenant-exit/requests` | `TENANT_ADMIN` | YES | Tenant Client | `ApiClient` | `TenantExitScreen` | `/tenant-exit` | Tenant Exit | `e2e_three_app_business_workflows_test.dart` (Test 19) | COMPLETE |
| `TenantConfigurationController` | GET / PUT | `/api/v1/retention-schedules` | `COMPLIANCE_OFFICER` | YES | Tenant Client | `ApiClient` | `RetentionScheduleScreen` | `/retention` | Data Retention | `e2e_three_app_business_workflows_test.dart` (Test 20) | COMPLETE |
| `ExemptSectorReportingController` | GET / POST | `/api/v1/exempt-sector/reports` | `COMPLIANCE_OFFICER` | YES | Tenant Client | `ApiClient` | `ExemptSectorReportingScreen` | `/compliance/exempt-sector` | Exempt Sector Reports | `e2e_three_app_business_workflows_test.dart` (Test 21) | COMPLETE |
| `CustomerController` | GET / POST | `/api/v1/customers` | `CUSTOMER_READ` / `CUSTOMER_WRITE` | YES | Tenant Client | `ApiClient` | `CustomerListScreen` | `/customers` | Customers | `tenant_client_navigation_test.dart` | COMPLETE |
| `ProductController` | GET / POST | `/api/v1/products` | `PRODUCT_READ` / `PRODUCT_WRITE` | YES | Tenant Client | `ApiClient` | `ProductListScreen` | `/products` | Products | `tenant_client_navigation_test.dart` | COMPLETE |
| `ReportApiController` | GET | `/api/v1/reports` | `REPORT_READ` | YES | Tenant Client | `ApiClient` | `ReportsScreen` | `/reports` | Reports | `tenant_client_navigation_test.dart` | COMPLETE |
| `AdjustmentController` | GET / POST | `/api/v1/adjustments` | `INVOICE_CREATE` | YES | Tenant Client | `ApiClient` | `CreateAdjustmentScreen` | `/adjustments/create` | Action in Invoice Detail | `navigation_parity_and_clickthrough_test.dart` | COMPLETE |
| `OfflineSyncController` | POST | `/api/v1/offline/sync` | `OFFLINE_OPERATE` | NO (Background Job / Daemon) | Tenant Client Sync Daemon | `OfflineSyncService` | `OfflineOperationsScreen` | `/offline` | Triggered Sync Button | `flutter_offline_sync_adversarial_test.dart` | COMPLETE |
| `PublicInvoiceVerificationController` | GET | `/verify/{irn}` | Public | YES | Public Web | `ApiClient` | `InvoiceVerificationScreen` | `/verify/:irn` | Direct QR Code Scan Link | `app_router.dart` | COMPLETE |
| `ShortVerificationController` | GET | `/s/{shortCode}` | Public | YES | Public Web | `ApiClient` | `InvoiceVerificationScreen` | `/s/:code` | SMS / Compact URL | `app_router.dart` | COMPLETE |
| `ExportDownloadController` | GET | `/api/v1/exports/{id}/download` | `TENANT_ADMIN` | YES | Tenant Client | `ApiClient` | `TenantExitScreen` | `/tenant-exit` | Download Button | `e2e_three_app_business_workflows_test.dart` | COMPLETE |
| `InvitationAcceptanceController` | GET / POST | `/api/v1/invitations/validate` | Public | YES | Public Web | `ApiClient` | `InvitationAcceptanceScreen` | `/accept-invitation` | Email/SMS Invitation Link | `invitation_acceptance_screen_test.dart` | COMPLETE |
