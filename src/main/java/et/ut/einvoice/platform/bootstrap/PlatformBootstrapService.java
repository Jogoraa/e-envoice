package et.ut.einvoice.platform.bootstrap;

import et.ut.einvoice.catalog.domain.Category;
import et.ut.einvoice.catalog.domain.Product;
import et.ut.einvoice.catalog.domain.ServiceItem;
import et.ut.einvoice.catalog.repository.CategoryRepository;
import et.ut.einvoice.catalog.repository.ProductRepository;
import et.ut.einvoice.catalog.repository.ServiceItemRepository;
import et.ut.einvoice.platform.security.domain.PlatformUser;
import et.ut.einvoice.platform.security.repository.PlatformUserRepository;
import et.ut.einvoice.taxpayer.domain.TaxpayerProfile;
import et.ut.einvoice.taxpayer.repository.TaxpayerProfileRepository;
import et.ut.einvoice.tenancy.domain.ApiClient;
import et.ut.einvoice.tenancy.domain.Subscription;
import et.ut.einvoice.tenancy.domain.Tenant;
import et.ut.einvoice.tenancy.domain.TenantStatus;
import et.ut.einvoice.tenancy.domain.TenantUser;
import et.ut.einvoice.tenancy.repository.ApiClientRepository;
import et.ut.einvoice.tenancy.repository.SubscriptionRepository;
import et.ut.einvoice.tenancy.repository.TenantRepository;
import et.ut.einvoice.tenancy.repository.TenantUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Repeatable, secure, idempotent platform bootstrap service.
 * Automatically provisions initial Master Operator and Test Tenant accounts if not present,
 * generates cryptographically strong random passwords, hashes them with BCrypt, and prints
 * credentials ONCE to standard output.
 */
@Component
@Order(1)
public class PlatformBootstrapService implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformBootstrapService.class);

    private static final String CHAR_LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String CHAR_UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String DIGIT = "0123456789";
    private static final String SPECIAL = "!@#$%^&*()-_=+";
    private static final String PASSWORD_ALLOW = CHAR_LOWER + CHAR_UPPER + DIGIT + SPECIAL;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PlatformUserRepository platformUserRepository;
    private final TenantRepository tenantRepository;
    private final TenantUserRepository tenantUserRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TaxpayerProfileRepository taxpayerProfileRepository;
    private final ApiClientRepository apiClientRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ServiceItemRepository serviceItemRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public PlatformBootstrapService(
            PlatformUserRepository platformUserRepository,
            TenantRepository tenantRepository,
            TenantUserRepository tenantUserRepository,
            SubscriptionRepository subscriptionRepository,
            TaxpayerProfileRepository taxpayerProfileRepository,
            ApiClientRepository apiClientRepository,
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            ServiceItemRepository serviceItemRepository,
            PasswordEncoder passwordEncoder,
            JdbcTemplate jdbcTemplate
    ) {
        this.platformUserRepository = platformUserRepository;
        this.tenantRepository = tenantRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.taxpayerProfileRepository = taxpayerProfileRepository;
        this.apiClientRepository = apiClientRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.serviceItemRepository = serviceItemRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Starting UT Invoice platform security bootstrap check...");

        boolean masterCreated = false;
        String generatedMasterUsername = "platform.admin";
        String generatedMasterEmail = "admin@ut-invoice.internal";
        String generatedMasterPassword = null;

        boolean testTenantCreated = false;
        String testTenantTin = "0011223344";
        String testTenantName = "Abyssinia Trading & Distribution PLC";
        String testTenantUser = "admin";
        String testTenantPassword = null;
        String testApiClientId = "CLIENT_ABYSSINIA_ERP";
        String testApiClientSecret = null;

        String envMasterPass = System.getenv("PLATFORM_ADMIN_PASSWORD");
        if (envMasterPass == null || envMasterPass.isBlank()) {
            envMasterPass = System.getenv("MASTER_ADMIN_PASSWORD");
        }
        if (envMasterPass != null && !envMasterPass.isBlank()) {
            generatedMasterPassword = envMasterPass.trim();
        } else {
            generatedMasterPassword = generateSecurePassword(20);
        }

        String envTenantPass = System.getenv("TEST_TENANT_PASSWORD");
        if (envTenantPass != null && !envTenantPass.isBlank()) {
            testTenantPassword = envTenantPass.trim();
        } else {
            testTenantPassword = generateSecurePassword(18);
        }

        // 1. Idempotent Platform Operator Bootstrap
        if (!platformUserRepository.existsByRole("ROLE_PLATFORM_ADMIN")) {
            String masterHash = passwordEncoder.encode(generatedMasterPassword);

            PlatformUser masterUser = new PlatformUser(
                    UUID.randomUUID(),
                    generatedMasterUsername,
                    generatedMasterEmail,
                    masterHash,
                    "UT Invoice Master Operator",
                    "ROLE_PLATFORM_ADMIN",
                    "ACTIVE",
                    Instant.now()
            );
            platformUserRepository.save(masterUser);
            masterCreated = true;
            log.info("Provisioned initial Master Operator account: {}", generatedMasterUsername);
        } else if (envMasterPass != null && !envMasterPass.isBlank()) {
            var masterOpt = platformUserRepository.findByUsername(generatedMasterUsername)
                    .or(() -> platformUserRepository.findByEmail(generatedMasterEmail));
            if (masterOpt.isPresent()) {
                PlatformUser masterUser = masterOpt.get();
                masterUser.setPasswordHash(passwordEncoder.encode(generatedMasterPassword));
                platformUserRepository.save(masterUser);
                masterCreated = true;
                log.info("Synchronized Master Operator password from PLATFORM_ADMIN_PASSWORD environment setting.");
            }
        }

        // 2. Idempotent Test Tenant Bootstrap
        if (tenantRepository.findByTin(testTenantTin).isEmpty()) {
            UUID tenantId = UUID.randomUUID();
            UUID branchId = UUID.randomUUID();

            Tenant tenant = new Tenant(
                    tenantId,
                    "ORG_DEMO_SME_01",
                    testTenantName,
                    "Abyssinia Retail",
                    testTenantTin,
                    "SME"
            );
            tenant.activate();
            tenantRepository.save(tenant);

            // Seed Taxpayer Profile
            TaxpayerProfile profile = new TaxpayerProfile(
                    tenantId,
                    testTenantTin,
                    "0011223344VAT",
                    testTenantName,
                    "Abyssinia Retail",
                    "Addis Ababa",
                    "Bole",
                    "+251911002233",
                    "contact@abyssiniatrading.et",
                    "SYS-ETH-2026-001",
                    "POS"
            );
            taxpayerProfileRepository.saveAndFlush(profile);

            // Seed Branch via JDBC for SQL compliance
            try {
                jdbcTemplate.update(
                        "INSERT INTO branches (id, tenant_id, branch_code, branch_name, region, woreda, is_active, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                        branchId, tenantId, "MAIN_HQ", "Bole Main Branch", "Addis Ababa", "Bole", true, Timestamp.from(Instant.now())
                );
            } catch (Exception e) {
                log.warn("Notice: branch seeding through JDBC: {}", e.getMessage());
            }

            // Seed Subscription
            Subscription subscription = new Subscription(
                    UUID.randomUUID(),
                    tenantId,
                    "ENTERPRISE_UNLIMITED",
                    "ANNUAL",
                    50000,
                    100,
                    true,
                    false,
                    "ACTIVE"
            );
            subscriptionRepository.save(subscription);

            // Seed Tenant User
            String tenantUserHash = passwordEncoder.encode(testTenantPassword);
            TenantUser tenantUser = new TenantUser(
                    UUID.randomUUID(),
                    tenantId,
                    testTenantUser,
                    tenantUserHash,
                    "admin@abyssiniatrading.et",
                    "+251911002233",
                    "Abyssinia Store Manager",
                    "ROLE_TENANT_ADMIN",
                    "ACTIVE",
                    Instant.now()
            );
            tenantUserRepository.save(tenantUser);

            // Seed API Client for ERP / M2M integrations
            testApiClientSecret = generateSecurePassword(24);
            String hashedSecret = hashSha256(testApiClientSecret);
            ApiClient apiClient = new ApiClient(
                    UUID.randomUUID(),
                    tenantId,
                    testApiClientId,
                    hashedSecret,
                    "Abyssinia ERP System",
                    "invoice:read invoice:create tenant:admin"
            );
            apiClientRepository.save(apiClient);

            // Seed initial catalog master data (Categories, Products, Services)
            seedCatalogIfEmpty(tenantId);

            testTenantCreated = true;
            log.info("Provisioned initial Test Tenant account (TIN: {})", testTenantTin);
        } else {
            var tenantOpt = tenantRepository.findByTin(testTenantTin);
            if (tenantOpt.isPresent()) {
                UUID existingTenantId = tenantOpt.get().getId();
                seedCatalogIfEmpty(existingTenantId);

                if (envTenantPass != null && !envTenantPass.isBlank()) {
                    var userOpt = tenantUserRepository.findByTenantIdAndUsername(existingTenantId, testTenantUser);
                    if (userOpt.isPresent()) {
                        TenantUser tu = userOpt.get();
                        tu.setPasswordHash(passwordEncoder.encode(testTenantPassword));
                        tenantUserRepository.save(tu);
                        testTenantCreated = true;
                        log.info("Synchronized Test Tenant password from TEST_TENANT_PASSWORD environment setting.");
                    }
                }
            }
        }

        // 3. Display Bootstrap Credentials Banner if created or updated
        if (masterCreated || testTenantCreated) {
            printBootstrapBanner(
                    masterCreated, generatedMasterUsername, generatedMasterEmail, generatedMasterPassword,
                    testTenantCreated, testTenantTin, testTenantName, testTenantUser, testTenantPassword,
                    testApiClientId, testApiClientSecret
            );
        } else {
            log.info("UT Invoice platform database is already fully provisioned. Bootstrap complete.");
        }
    }

    private void printBootstrapBanner(
            boolean masterCreated, String masterUser, String masterEmail, String masterPass,
            boolean tenantCreated, String tenantTin, String tenantName, String tenantUser, String tenantPass,
            String clientId, String clientSecret
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("================================================================================\n");
        sb.append("                    UT INVOICE INITIAL BOOTSTRAP CREDENTIALS\n");
        sb.append("================================================================================\n");
        sb.append("  ATTENTION: The initial platform credentials below were securely generated\n");
        sb.append("  and persisted to the database. These credentials are displayed ONLY ONCE.\n");
        sb.append("  Store them securely in your password manager.\n");
        sb.append("--------------------------------------------------------------------------------\n");

        if (masterCreated) {
            sb.append("\n  [1] SAAS MASTER OPERATOR ACCOUNT (Master Portal & SaaS Admin)\n");
            sb.append("      Portal Route: /api/v1/saas, /api/v1/master\n");
            sb.append("      Username:     ").append(masterUser).append("\n");
            sb.append("      Email:        ").append(masterEmail).append("\n");
            sb.append("      Role:         ROLE_PLATFORM_ADMIN\n");
            sb.append("      Password:     ").append(masterPass).append("\n");
        }

        if (tenantCreated) {
            sb.append("\n  [2] TEST TENANT ACCOUNT (Tenant Business Portal)\n");
            sb.append("      Company:      ").append(tenantName).append("\n");
            sb.append("      TIN:          ").append(tenantTin).append("\n");
            sb.append("      Branch:       MAIN_HQ (Bole Main Branch)\n");
            sb.append("      Username:     ").append(tenantUser).append("\n");
            sb.append("      Role:         ROLE_TENANT_ADMIN\n");
            sb.append("      Password:     ").append(tenantPass).append("\n");

            sb.append("\n  [3] TEST TENANT API CLIENT (ERP / M2M Ingress)\n");
            sb.append("      Client ID:    ").append(clientId).append("\n");
            sb.append("      Secret:       ").append(clientSecret).append("\n");
        }

        sb.append("\n================================================================================\n");

        System.out.println(sb.toString());
    }

    public static String generateSecurePassword(int length) {
        if (length < 12) length = 12;
        StringBuilder sb = new StringBuilder(length);
        // Guarantee at least one lower, one upper, one digit, one special
        sb.append(CHAR_LOWER.charAt(RANDOM.nextInt(CHAR_LOWER.length())));
        sb.append(CHAR_UPPER.charAt(RANDOM.nextInt(CHAR_UPPER.length())));
        sb.append(DIGIT.charAt(RANDOM.nextInt(DIGIT.length())));
        sb.append(SPECIAL.charAt(RANDOM.nextInt(SPECIAL.length())));

        for (int i = 4; i < length; i++) {
            sb.append(PASSWORD_ALLOW.charAt(RANDOM.nextInt(PASSWORD_ALLOW.length())));
        }

        // Shuffle
        char[] chars = sb.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char temp = chars[i];
            chars[i] = chars[j];
            chars[j] = temp;
        }
        return new String(chars);
    }

    private void seedCatalogIfEmpty(UUID tenantId) {
        if (categoryRepository.findByTenantIdOrderByCodeAsc(tenantId).isEmpty()) {
            Category bev = new Category(UUID.randomUUID(), tenantId, "BEV", "Beverages & Refreshments", "PRODUCT", "Soft drinks, juices, bottled water and beverages", null);
            Category grain = new Category(UUID.randomUUID(), tenantId, "GRAIN", "Agricultural Grains & Cereals", "PRODUCT", "Teff, barley, wheat, pulses and staples", null);
            Category it = new Category(UUID.randomUUID(), tenantId, "IT_SERV", "IT & Cloud Services", "SERVICE", "Software engineering, hosting, network consulting", null);
            Category consult = new Category(UUID.randomUUID(), tenantId, "CONSULT", "Business & Advisory Services", "SERVICE", "Auditing, accounting, management consultancy", null);
            categoryRepository.save(bev);
            categoryRepository.save(grain);
            categoryRepository.save(it);
            categoryRepository.save(consult);

            if (productRepository.findByTenantIdOrderByItemCodeAsc(tenantId).isEmpty()) {
                Product water = new Product(
                        UUID.randomUUID(), tenantId, null, "SKU-BEV-001", "SKU-BEV-001", "600123456789",
                        "Highland Spring Bottled Water 1L", bev.getId(), "BEV", "EA",
                        new BigDecimal("45.00"), "VAT15", true, new BigDecimal("1200.00")
                );
                Product teff = new Product(
                        UUID.randomUUID(), tenantId, null, "SKU-TEF-100", "SKU-TEF-100", "600987654321",
                        "Magna Teff Grain (100kg bag)", grain.getId(), "GRAIN", "BAG",
                        new BigDecimal("9800.00"), "EXEMPT", true, new BigDecimal("250.00")
                );
                productRepository.save(water);
                productRepository.save(teff);
            }

            if (serviceItemRepository.findByTenantIdOrderByServiceCodeAsc(tenantId).isEmpty()) {
                ServiceItem srv1 = new ServiceItem(
                        UUID.randomUUID(), tenantId, null, "SRV-IT-001", "Enterprise Cloud ERP Deployment",
                        "Full lifecycle setup, migration, and integration of ERP systems", it.getId(), "IT_SERV",
                        "HR", new BigDecimal("2500.00"), "VAT15"
                );
                ServiceItem srv2 = new ServiceItem(
                        UUID.randomUUID(), tenantId, null, "SRV-AUD-002", "Statutory Tax Compliance Audit",
                        "Full statutory e-invoicing and corporate tax compliance review", consult.getId(), "CONSULT",
                        "CONTRACT", new BigDecimal("45000.00"), "VAT15"
                );
                serviceItemRepository.save(srv1);
                serviceItemRepository.save(srv2);
            }
            log.info("Successfully seeded default catalog categories, products, and services for tenant {}", tenantId);
        }
    }

    private String hashSha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
