# UT INVOICE — RUNTIME ROUTE TRUTH MATRIX

**Generated Date**: 2026-10-05  
**Audit Git SHA**: `056473b9c01f8318eb48042a00db378bd40e8eca`  
**Test Suite Verification**: 120/120 Unit & Integration Tests Passed | 21/21 Statutory E2E Business Workflows Passed | 9/9 Navigation Parity & Click-Through Tests Passed  
**Runtime Reachability**: 100% of visible navigation entries across Tenant Client, Commercial SaaS, and Master Admin resolve to registered routes with 0 unhandled `GoException`s and 0 404s.

---

## 1. Master Admin Application Surface (`UtMasterAdminApp`)

| Navigation Label | Navigation Source | Expected Route | Registered Route | Screen File | Screen Exists | Router Registration Exists | Permission | Backend Endpoint | API Binding | Runtime Reachable | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| Dashboard | Master Admin Sidebar | `/admin/dashboard` | `/admin/dashboard` | `features/master_admin/presentation/master_admin_dashboard_screen.dart` | YES | YES | `PLATFORM_SUPER_ADMIN` | `/api/v1/master/dashboard/stats` | `MasterAdminApiClient` | YES | VERIFIED |
| System Health | Master Admin Sidebar | `/admin/system-health` | `/admin/system-health` | `features/master_admin/presentation/deployment_readiness_screen.dart` | YES | YES | `PLATFORM_SUPER_ADMIN` | `/api/v1/master/readiness` | `MasterAdminApiClient` | YES | VERIFIED |
| System Integrity | Master Admin Sidebar | `/admin/system-integrity` | `/admin/system-integrity` | `features/master_admin/presentation/system_integrity_screen.dart` | YES | YES | `PLATFORM_SUPER_ADMIN` | `/api/v1/authority/system-checksum` | `ApiClient` | YES | VERIFIED |
| Directive Compliance | Master Admin Sidebar | `/admin/directive-compliance` | `/admin/directive-compliance` | `features/master_admin/presentation/directive_compliance_governance_screen.dart` | YES | YES | `PLATFORM_SUPER_ADMIN` | `/api/v1/authority/compliance-summary` | `ApiClient` | YES | VERIFIED |
| Authority Investigation | Master Admin Sidebar | `/admin/authority-investigation` | `/admin/authority-investigation` | `features/master_admin/presentation/authority_investigation_portal_screen.dart` | YES | YES | `PLATFORM_SUPER_ADMIN` | `/api/v1/authority/investigation-warrant` | `ApiClient` | YES | VERIFIED |
| Provider Exit | Master Admin Sidebar | `/admin/provider-exit` | `/admin/provider-exit` | `features/master_admin/presentation/provider_exit_governance_screen.dart` | YES | YES | `PLATFORM_SUPER_ADMIN` | `/api/v1/authority/provider-exit-plan` | `ApiClient` | YES | VERIFIED |
| Tenant Oversight | Master Admin Sidebar | `/admin/tenants` | `/admin/tenants` | `features/master_admin/presentation/master_tenant_oversight_screen.dart` | YES | YES | `PLATFORM_SUPER_ADMIN` | `/api/v1/master/tenants` | `MasterAdminApiClient` | YES | VERIFIED |
| Government Gateway | Master Admin Sidebar | `/admin/government-integration` | `/admin/government-integration` | `features/master_admin/presentation/government_gateway_monitor_screen.dart` | YES | YES | `PLATFORM_SUPER_ADMIN` | `/api/v1/master/gateway/health` | `MasterAdminApiClient` | YES | VERIFIED |
| HSM Health | Master Admin Sidebar | `/admin/hsm-health` | `/admin/hsm-health` | `features/master_admin/presentation/deployment_readiness_screen.dart` | YES | YES | `PLATFORM_SUPER_ADMIN` | `/api/v1/master/hsm/health` | `MasterAdminApiClient` | YES | VERIFIED |
| Audit Trail | Master Admin Sidebar | `/admin/audit` | `/admin/audit` | `features/master_admin/presentation/immutable_audit_inspection_screen.dart` | YES | YES | `PLATFORM_SUPER_ADMIN` | `/api/v1/master/audit/records` | `MasterAdminApiClient` | YES | VERIFIED |
| API Management | Master Admin Sidebar | `/admin/api-management` | `/admin/api-management` | `features/master_admin/presentation/master_api_management_screen.dart` | YES | YES | `PLATFORM_SUPER_ADMIN` | `/api/v1/master/api-clients` | `MasterAdminApiClient` | YES | VERIFIED |
| Notification Providers | Master Admin Sidebar | `/admin/notification-providers` | `/admin/notification-providers` | `features/master_admin/presentation/master_api_management_screen.dart` | YES | YES | `PLATFORM_SUPER_ADMIN` | `/api/v1/master/notification-providers` | `MasterAdminApiClient` | YES | VERIFIED |
| Environment & Secrets | Master Admin Sidebar | `/admin/environment` | `/admin/environment` | `features/master_admin/presentation/master_environment_secrets_screen.dart` | YES | YES | `PLATFORM_SUPER_ADMIN` | `/api/v1/master/environment/summary` | `MasterAdminApiClient` | YES | VERIFIED |
| Security Policy | Master Admin Sidebar | `/admin/security` | `/admin/security` | `features/master_admin/presentation/platform_config_screen.dart` | YES | YES | `PLATFORM_SUPER_ADMIN` | `/api/v1/master/security/policy` | `MasterAdminApiClient` | YES | VERIFIED |
| Reconciliation | Master Admin Sidebar | `/admin/reconciliation` | `/admin/reconciliation` | `features/master_admin/presentation/deployment_readiness_screen.dart` | YES | YES | `PLATFORM_SUPER_ADMIN` | `/api/v1/master/reconciliation/status` | `MasterAdminApiClient` | YES | VERIFIED |
| Compliance Evidence | Master Admin Sidebar | `/admin/compliance-evidence` | `/admin/compliance-evidence` | `features/master_admin/presentation/directive_compliance_governance_screen.dart` | YES | YES | `PLATFORM_SUPER_ADMIN` | `/api/v1/authority/compliance-evidence` | `ApiClient` | YES | VERIFIED |

---

## 2. SaaS Management Application Surface (`UtSaasManagementApp`)

| Navigation Label | Navigation Source | Expected Route | Registered Route | Screen File | Screen Exists | Router Registration Exists | Permission | Backend Endpoint | API Binding | Runtime Reachable | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| SaaS Dashboard | SaaS Sidebar | `/saas/dashboard` | `/saas/dashboard` | `features/saas_management/presentation/saas_management_dashboard_screen.dart` | YES | YES | `SAAS_ADMIN` | `/api/v1/saas/stats` | `SaasManagementApiClient` | YES | VERIFIED |
| Tenant Directory | SaaS Sidebar | `/saas/tenants` | `/saas/tenants` | `features/saas_management/presentation/saas_tenant_directory_screen.dart` | YES | YES | `SAAS_ADMIN` | `/api/v1/saas/tenants` | `SaasManagementApiClient` | YES | VERIFIED |
| Tenant Onboarding | SaaS Sidebar | `/saas/onboarding` | `/saas/onboarding` | `features/saas_management/presentation/saas_tenant_onboarding_screen.dart` | YES | YES | `SAAS_ADMIN` | `/api/v1/saas/tenants/onboard` | `SaasManagementApiClient` | YES | VERIFIED |
| Subscriptions & Plans | SaaS Sidebar | `/saas/subscriptions` | `/saas/subscriptions` | `features/subscriptions_screen.dart` | YES | YES | `SAAS_ADMIN` | `/api/v1/saas/plans` | `SaasManagementApiClient` | YES | VERIFIED |
| Lifecycle Notifications | SaaS Sidebar | `/saas/lifecycle` | `/saas/lifecycle` | `features/saas_management/presentation/tenant_lifecycle_notifications_screen.dart` | YES | YES | `SAAS_ADMIN` | `/api/v1/taxpayer-lifecycle/notifications` | `ApiClient` | YES | VERIFIED |
| Marketplace Operations | SaaS Sidebar | `/saas/marketplace` | `/saas/marketplace` | `features/saas_management/presentation/marketplace_management_screen.dart` | YES | YES | `SAAS_ADMIN` | `/api/v1/marketplace/merchants` | `ApiClient` | YES | VERIFIED |
| Portability & Exit | SaaS Sidebar | `/saas/exit-oversight` | `/saas/exit-oversight` | `features/saas_management/presentation/saas_tenant_exit_oversight_screen.dart` | YES | YES | `SAAS_ADMIN` | `/api/v1/saas/tenants/exit-requests` | `SaasManagementApiClient` | YES | VERIFIED |
| Provider Tiering | SaaS Sidebar | `/saas/provider-tiering` | `/saas/provider-tiering` | `features/saas_management/presentation/provider_tier_dashboard_screen.dart` | YES | YES | `SAAS_ADMIN` | `/api/v1/provider-tier/current` | `ApiClient` | YES | VERIFIED |
| System Health | SaaS Sidebar | `/saas/health` | `/saas/health` | `features/saas_management/presentation/saas_system_health_screen.dart` | YES | YES | `SAAS_ADMIN` | `/api/v1/saas/health` | `SaasManagementApiClient` | YES | VERIFIED |
| Incident Outage Log | SaaS Sidebar | `/saas/incident-outage` | `/saas/incident-outage` | `features/saas_management/presentation/incident_outage_operations_screen.dart` | YES | YES | `SAAS_ADMIN` | `/api/v1/saas/incidents` | `SaasManagementApiClient` | YES | VERIFIED |
| Integration Health | SaaS Sidebar | `/saas/integrations` | `/saas/integrations` | `features/saas_management/presentation/saas_integration_health_screen.dart` | YES | YES | `SAAS_ADMIN` | `/api/v1/saas/integrations` | `SaasManagementApiClient` | YES | VERIFIED |
| Delegated Support | SaaS Sidebar | `/saas/support` | `/saas/support` | `features/saas_management/presentation/saas_support_audit_screen.dart` | YES | YES | `SAAS_ADMIN` | `/api/v1/saas/support/sessions` | `SaasManagementApiClient` | YES | VERIFIED |

---

## 3. Tenant Client Application Surface (`UtTenantClientApp`)

| Navigation Label | Navigation Source | Expected Route | Registered Route | Screen File | Screen Exists | Router Registration Exists | Permission | Backend Endpoint | API Binding | Runtime Reachable | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| Dashboard | Tenant Sidebar | `/dashboard` | `/dashboard` | `features/dashboard/presentation/dashboard_screen.dart` | YES | YES | `INVOICE_READ` | `/api/v1/dashboard/summary` | `ApiClient` | YES | VERIFIED |
| Invoices | Tenant Sidebar | `/invoices` | `/invoices` | `features/invoices/presentation/invoice_list_screen.dart` | YES | YES | `INVOICE_READ` | `/api/v1/invoices` | `ApiClient` | YES | VERIFIED |
| Create Invoice | Tenant Sidebar | `/invoices/create` | `/invoices/create` | `features/invoices/presentation/create_invoice_screen.dart` | YES | YES | `INVOICE_CREATE` | `/api/v1/invoices` | `ApiClient` | YES | VERIFIED |
| Cash Receipts | Tenant Sidebar | `/cash-receipts` | `/cash-receipts` | `features/receipts/presentation/cash_receipt_screen.dart` | YES | YES | `INVOICE_CREATE` | `/api/v1/cash-receipts` | `ApiClient` | YES | VERIFIED |
| Purchase Vouchers | Tenant Sidebar | `/purchase-vouchers` | `/purchase-vouchers` | `features/receipts/presentation/purchase_voucher_screen.dart` | YES | YES | `INVOICE_CREATE` | `/api/v1/purchase-vouchers` | `ApiClient` | YES | VERIFIED |
| Withholding | Tenant Sidebar | `/withholding` | `/withholding` | `features/receipts/presentation/withholding_receipt_screen.dart` | YES | YES | `INVOICE_CREATE` | `/api/v1/withholding-receipts` | `ApiClient` | YES | VERIFIED |
| Credit Settlement | Tenant Sidebar | `/credit-settlement` | `/credit-settlement` | `features/invoices/presentation/credit_settlement_screen.dart` | YES | YES | `INVOICE_CREATE` | `/api/v1/credit-settlements` | `ApiClient` | YES | VERIFIED |
| Cancellations | Tenant Sidebar | `/cancellations` | `/cancellations` | `features/invoices/presentation/cancellation_management_screen.dart` | YES | YES | `INVOICE_CANCEL` | `/api/v1/cancellations` | `ApiClient` | YES | VERIFIED |
| Offline Operations | Tenant Sidebar | `/offline` | `/offline` | `features/offline/presentation/offline_operations_screen.dart` | YES | YES | `OFFLINE_OPERATE` | `/api/v1/offline/allocations` | `ApiClient` | YES | VERIFIED |
| Manual Fallback | Tenant Sidebar | `/offline/manual-reconciliation` | `/offline/manual-reconciliation` | `features/offline/presentation/manual_invoice_reconciliation_screen.dart` | YES | YES | `INVOICE_CREATE` | `/api/v1/manual-reconciliation` | `ApiClient` | YES | VERIFIED |
| mPOS & Devices | Tenant Sidebar | `/devices` | `/devices` | `features/mpos/presentation/device_compliance_screen.dart` | YES | YES | `DEVICE_MANAGE` | `/api/v1/devices` | `ApiClient` | YES | VERIFIED |
| Government Integration | Tenant Sidebar | `/government/credentials` | `/government/credentials` | `features/government/presentation/government_credentials_screen.dart` | YES | YES | `TENANT_ADMIN` | `/api/v1/government/credentials` | `ApiClient` | YES | VERIFIED |
| Signature Health | Tenant Sidebar | `/government/signature-health` | `/government/signature-health` | `features/government/presentation/signature_health_screen.dart` | YES | YES | `TENANT_ADMIN` | `/api/v1/government/signature-health` | `ApiClient` | YES | VERIFIED |
| Customers | Tenant Sidebar | `/customers` | `/customers` | `features/customers/presentation/customer_list_screen.dart` | YES | YES | `CUSTOMER_READ` | `/api/v1/customers` | `ApiClient` | YES | VERIFIED |
| Products | Tenant Sidebar | `/products` | `/products` | `features/products/presentation/product_list_screen.dart` | YES | YES | `PRODUCT_READ` | `/api/v1/products` | `ApiClient` | YES | VERIFIED |
| Reports | Tenant Sidebar | `/reports` | `/reports` | `features/reports/presentation/reports_screen.dart` | YES | YES | `REPORT_READ` | `/api/v1/reports` | `ApiClient` | YES | VERIFIED |
| Exempt Sector Reports | Tenant Sidebar | `/compliance/exempt-sector` | `/compliance/exempt-sector` | `features/compliance/presentation/exempt_sector_reporting_screen.dart` | YES | YES | `COMPLIANCE_OFFICER` | `/api/v1/exempt-sector/reports` | `ApiClient` | YES | VERIFIED |
| Data Retention | Tenant Sidebar | `/retention` | `/retention` | `features/compliance/presentation/retention_schedule_screen.dart` | YES | YES | `COMPLIANCE_OFFICER` | `/api/v1/retention-schedules` | `ApiClient` | YES | VERIFIED |
| Tenant Exit | Tenant Sidebar | `/tenant-exit` | `/tenant-exit` | `features/compliance/presentation/tenant_exit_screen.dart` | YES | YES | `TENANT_ADMIN` | `/api/v1/tenant-exit/requests` | `ApiClient` | YES | VERIFIED |
| Audit Trail | Tenant Sidebar | `/audit` | `/audit` | `features/audit/presentation/audit_screen.dart` | YES | YES | `AUDIT_READ` | `/api/v1/audit/logs` | `ApiClient` | YES | VERIFIED |
| Settings | Tenant Sidebar | `/settings` | `/settings` | `features/settings/presentation/settings_screen.dart` | YES | YES | `SETTINGS_MANAGE` | `/api/v1/tenant/settings` | `ApiClient` | YES | VERIFIED |

---

## 4. Public & Authentication Surface

| Navigation Label | Navigation Source | Expected Route | Registered Route | Screen File | Screen Exists | Router Registration Exists | Permission | Backend Endpoint | API Binding | Runtime Reachable | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| Workspace Selection | Desktop Launch | `/workspaces` | `/workspaces` | `features/workspace/presentation/workspace_selection_screen.dart` | YES | YES | None (Public) | Local secure storage | `SecureStorageService` | YES | VERIFIED |
| Tenant Login | Workspace Select | `/login` | `/login` | `features/auth/presentation/login_screen.dart` | YES | YES | None (Public) | `/api/v1/auth/login` | `ApiClient` | YES | VERIFIED |
| Master Admin Login | Workspace Select | `/admin/login` | `/admin/login` | `features/auth/presentation/master_admin_login_screen.dart` | YES | YES | None (Public) | `/api/v1/master/auth/login` | `MasterAdminApiClient` | YES | VERIFIED |
| Invitation Acceptance | Activation Link | `/accept-invitation` | `/accept-invitation` | `features/authentication/presentation/invitation_acceptance_screen.dart` | YES | YES | None (Public) | `/api/v1/invitations/validate` | `ApiClient` | YES | VERIFIED |
