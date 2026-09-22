package et.ut.einvoice.compliance;

import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.adjustments.dto.CreateAdjustmentRequest;
import et.ut.einvoice.cancellation.dto.CreateCancellationRequestDto;
import et.ut.einvoice.government.domain.GovernmentRegistrationProvider;
import et.ut.einvoice.invoicing.domain.TransactionType;
import et.ut.einvoice.invoicing.dto.CreateInvoiceRequest;
import et.ut.einvoice.taxpayer.domain.TaxpayerProfile;
import et.ut.einvoice.taxpayer.repository.TaxpayerProfileRepository;
import et.ut.einvoice.tenancy.domain.ApiClient;
import et.ut.einvoice.tenancy.domain.Tenant;
import et.ut.einvoice.tenancy.repository.ApiClientRepository;
import et.ut.einvoice.tenancy.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ReferenceErpIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TaxpayerProfileRepository taxpayerProfileRepository;

    @Autowired
    private ApiClientRepository apiClientRepository;

    @MockBean
    private GovernmentRegistrationProvider governmentRegistrationProvider;

    private final String apiKey = "UT_ERP_KEY_001";
    private final String clientSecret = "sec_test_12345";
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.fromString("00000000-0000-0000-0000-000000000003");

        Tenant tenant = new Tenant(
                tenantId, "ORG-UT-001", "Future UT ERP Retailer PLC", "UT Retail",
                "0099887766", "SME"
        );
        tenant.activate();
        tenantRepository.save(tenant);

        taxpayerProfileRepository.save(new TaxpayerProfile(
                tenantId, "0099887766", "VAT-77665544", "Future UT ERP Retailer PLC", "UT Retail",
                "14", "01", "+251911998877", "erp@utsystems.et", "8EFBBDD7FA", "ERP"
        ));

        apiClientRepository.save(new ApiClient(
                UUID.randomUUID(), tenantId, apiKey, clientSecret, "Future UT ERP System",
                "invoice:read invoice:create invoice:adjust invoice:cancel receipt:create tenant:admin webhook:manage"
        ));

        Mockito.when(governmentRegistrationProvider.getProviderVersion()).thenReturn("v1.0");

        Mockito.when(governmentRegistrationProvider.registerInvoice(any(), any(), anyString()))
                .thenAnswer(inv -> {
                    String irn = "ERP-IRN-" + UUID.randomUUID();
                    String rrn = "ERP-RRN-" + UUID.randomUUID();
                    String qr = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
                    return GovernmentRegistrationProvider.GovernmentRegistrationResult.success(irn, rrn, "2026-09-18T12:00:00Z", qr, "signed-invoice-mock");
                });

        Mockito.when(governmentRegistrationProvider.cancelInvoice(anyString(), anyString(), anyString()))
                .thenReturn(new GovernmentRegistrationProvider.CancellationResult(true, "CANCEL-ERP-999", "Successfully cancelled"));
    }

    @Test
    @DisplayName("UT ERP Integration: Issue Invoice, Query, Adjust, Cancel strictly via /api/v1 REST endpoints")
    void test_FutureUtErp_PublicApiContract() throws Exception {
        // 1. UT ERP issues B2C sales registration invoice via POST /api/v1/invoices
        var items = List.of(new CreateInvoiceRequest.LineItemRequest(
                "SKU-100", "Laptop Stand", "goods", "PCS",
                new BigDecimal("2.00"), new BigDecimal("1500.00"), BigDecimal.ZERO, "VAT15", null
        ));
        var invoiceReq = new CreateInvoiceRequest(
                TransactionType.B2C, "CASH", "IMMEDIATE", null, items, null, null, null
        );

        String invoiceResponseStr = mockMvc.perform(post("/api/v1/invoices")
                        .header("X-API-Key", apiKey)
                        .header("X-Client-Secret", clientSecret)
                        .header("Idempotency-Key", "erp-idemp-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invoiceReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REGISTERED"))
                .andExpect(jsonPath("$.irn").exists())
                .andExpect(jsonPath("$.grandTotal").value(3450.00))
                .andReturn().getResponse().getContentAsString();

        String registeredIrn = objectMapper.readTree(invoiceResponseStr).get("irn").asText();

        // 2. UT ERP retrieves registered invoices via GET /api/v1/invoices
        mockMvc.perform(get("/api/v1/invoices")
                        .header("X-API-Key", apiKey)
                        .header("X-Client-Secret", clientSecret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].irn").value(registeredIrn));

        // 3. UT ERP registers a Credit Note via POST /api/v1/adjustments/credit-notes
        var creditReq = new CreateAdjustmentRequest(
                registeredIrn, "Goods returned by customer",
                new BigDecimal("1000.00"), new BigDecimal("150.00")
        );

        mockMvc.perform(post("/api/v1/adjustments/credit-notes")
                        .header("X-API-Key", apiKey)
                        .header("X-Client-Secret", clientSecret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(creditReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.noteType").value("CREDIT_NOTE"))
                .andExpect(jsonPath("$.adjustedTotal").value(1150.00));

        // 4. UT ERP requests invoice cancellation via POST /api/v1/cancellations
        var cancelReq = new CreateCancellationRequestDto(
                registeredIrn, "CUSTOMER_CANCELLATION", "Customer requested cancellation"
        );

        mockMvc.perform(post("/api/v1/cancellations")
                        .header("X-API-Key", apiKey)
                        .header("X-Client-Secret", clientSecret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("APPROVED"))
                .andExpect(jsonPath("$.cancellationRef").value("CANCEL-ERP-999"));

        // 5. UT ERP initiates a data export via POST /api/v1/portability/exports
        mockMvc.perform(post("/api/v1/portability/exports")
                        .header("X-API-Key", apiKey)
                        .header("X-Client-Secret", clientSecret))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value(org.hamcrest.Matchers.isOneOf("REQUESTED", "COMPLETED")));
    }
}
