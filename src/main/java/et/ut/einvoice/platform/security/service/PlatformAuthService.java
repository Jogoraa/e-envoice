package et.ut.einvoice.platform.security.service;

import et.ut.einvoice.platform.security.JwtTokenService;
import et.ut.einvoice.platform.security.domain.PlatformUser;
import et.ut.einvoice.platform.security.dto.PlatformAuthResponse;
import et.ut.einvoice.platform.security.dto.PlatformLoginRequest;
import et.ut.einvoice.platform.security.dto.TenantAuthResponse;
import et.ut.einvoice.platform.security.dto.TenantLoginRequest;
import et.ut.einvoice.platform.security.repository.PlatformUserRepository;
import et.ut.einvoice.tenancy.domain.Tenant;
import et.ut.einvoice.tenancy.domain.TenantStatus;
import et.ut.einvoice.tenancy.domain.TenantUser;
import et.ut.einvoice.tenancy.repository.TenantRepository;
import et.ut.einvoice.tenancy.repository.TenantUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import et.ut.einvoice.platform.config.dto.ConfigurationDtos.SendStepUpOtpResponse;
import et.ut.einvoice.platform.config.service.MasterMfaOtpService;
import et.ut.einvoice.platform.identity.repository.PlatformUserRecoveryCodeRepository;
import et.ut.einvoice.platform.identity.service.TotpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Service
public class PlatformAuthService {

    private static final Logger log = LoggerFactory.getLogger(PlatformAuthService.class);

    private final TenantRepository tenantRepository;
    private final TenantUserRepository tenantUserRepository;
    private final PlatformUserRepository platformUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final TotpService totpService;
    private final PlatformUserRecoveryCodeRepository recoveryCodeRepository;
    private final MasterMfaOtpService mfaOtpService;

    @Autowired
    public PlatformAuthService(
            TenantRepository tenantRepository,
            TenantUserRepository tenantUserRepository,
            PlatformUserRepository platformUserRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            @Autowired(required = false) TotpService totpService,
            @Autowired(required = false) PlatformUserRecoveryCodeRepository recoveryCodeRepository,
            @Autowired(required = false) MasterMfaOtpService mfaOtpService
    ) {
        this.tenantRepository = tenantRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.platformUserRepository = platformUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.totpService = totpService;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.mfaOtpService = mfaOtpService;
    }

    public PlatformAuthService(
            TenantRepository tenantRepository,
            TenantUserRepository tenantUserRepository,
            PlatformUserRepository platformUserRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            TotpService totpService,
            PlatformUserRecoveryCodeRepository recoveryCodeRepository
    ) {
        this(tenantRepository, tenantUserRepository, platformUserRepository, passwordEncoder, jwtTokenService, totpService, recoveryCodeRepository, null);
    }

    public PlatformAuthService(
            TenantRepository tenantRepository,
            TenantUserRepository tenantUserRepository,
            PlatformUserRepository platformUserRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService
    ) {
        this(tenantRepository, tenantUserRepository, platformUserRepository, passwordEncoder, jwtTokenService, null, null, null);
    }

    @Transactional
    public TenantAuthResponse authenticateTenantUser(TenantLoginRequest request) {
        String tin = request.tin() != null ? request.tin().trim() : "";
        String username = request.username() != null ? request.username().trim() : "";
        String rawPassword = request.password() != null ? request.password() : "";

        if (tin.isEmpty() || username.isEmpty() || rawPassword.isEmpty()) {
            throw new BadCredentialsException("TIN, username, and password must not be empty.");
        }

        Optional<Tenant> tenantOpt = tenantRepository.findByTin(tin);
        if (tenantOpt.isEmpty()) {
            log.warn("Failed tenant authentication attempt: TIN '{}' not registered", tin);
            throw new BadCredentialsException("Invalid TIN, username, or password.");
        }

        Tenant tenant = tenantOpt.get();
        if (tenant.getStatus() == TenantStatus.SUSPENDED ||
            tenant.getStatus() == TenantStatus.DEACTIVATED ||
            tenant.getStatus() == TenantStatus.ARCHIVED) {
            log.warn("Authentication rejected for suspended/inactive tenant TIN '{}'", tin);
            throw new DisabledException("Tenant account is suspended or deactivated.");
        }

        Optional<TenantUser> userOpt = tenantUserRepository.findByTenantIdAndUsername(tenant.getId(), username);
        if (userOpt.isEmpty()) {
            userOpt = tenantUserRepository.findByTenantIdAndEmail(tenant.getId(), username);
        }
        if (userOpt.isEmpty()) {
            log.warn("Failed tenant authentication attempt: user/email '{}' not found in tenant '{}'", username, tenant.getId());
            throw new BadCredentialsException("Invalid TIN, username, or password.");
        }

        TenantUser user = userOpt.get();
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            log.warn("Failed tenant authentication attempt: invalid password for user '{}' in tenant '{}'", username, tenant.getId());
            throw new BadCredentialsException("Invalid TIN, username, or password.");
        }

        if (!user.isActive()) {
            log.warn("Authentication rejected: user '{}' in tenant '{}' is not ACTIVE (status: {})", username, tenant.getId(), user.getStatus());
            throw new DisabledException("User account is inactive or suspended.");
        }

        // Update last login
        user.setLastLoginAt(Instant.now());
        tenantUserRepository.save(user);

        // Build scopes based on user role
        Set<String> roles = new HashSet<>();
        roles.add(user.getRole().startsWith("ROLE_") ? user.getRole() : "ROLE_" + user.getRole());

        Set<String> scopes = new HashSet<>();
        scopes.add("invoice:read");
        scopes.add("invoice:create");
        scopes.add("customer:read");
        scopes.add("customer:write");
        scopes.add("report:read");
        scopes.add("offline:sync");

        if (user.getRole().toUpperCase().contains("ADMIN") || user.getRole().equalsIgnoreCase("ROLE_TENANT_ADMIN")) {
            scopes.add("tenant:admin");
            scopes.add("user:manage");
            scopes.add("branch:manage");
            scopes.add("device:manage");
        }

        long ttlSeconds = 86400; // 24 hours
        String token = jwtTokenService.generateToken(tenant.getId(), user.getUsername(), roles, scopes, ttlSeconds);

        log.info("Tenant user '{}' successfully authenticated for tenant '{}' ({})", user.getUsername(), tenant.getLegalName(), tenant.getTin());

        return new TenantAuthResponse(
                token,
                "Bearer",
                ttlSeconds,
                tenant.getId(),
                tenant.getTin(),
                tenant.getLegalName(),
                tenant.getTradeName(),
                user.getUsername(),
                user.getFullName(),
                user.getRole(),
                scopes,
                Instant.now()
        );
    }

    @Transactional
    public PlatformAuthResponse authenticatePlatformOperator(PlatformLoginRequest request) {
        String identifier = request.username() != null ? request.username().trim() : "";
        String rawPassword = request.password() != null ? request.password() : "";

        if (identifier.isEmpty() || rawPassword.isEmpty()) {
            throw new BadCredentialsException("Username/email and password must not be empty.");
        }

        Optional<PlatformUser> userOpt = platformUserRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(identifier, identifier);
        if (userOpt.isEmpty() && (identifier.equalsIgnoreCase("saasadmin") || identifier.equalsIgnoreCase("saas.admin") || identifier.equalsIgnoreCase("saasadmin@utsolutionsplc.com"))) {
            userOpt = platformUserRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("saas.admin", "saas.admin@utsolutionsplc.com");
        }
        if (userOpt.isEmpty() && (identifier.equalsIgnoreCase("admin") || identifier.equalsIgnoreCase("masteradmin") || identifier.equalsIgnoreCase("master.admin"))) {
            userOpt = platformUserRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("platform.admin", "admin@ut-invoice.internal");
        }

        if (userOpt.isEmpty()) {
            log.warn("Failed platform operator authentication attempt for '{}'", identifier);
            throw new BadCredentialsException("Invalid platform credentials.");
        }

        PlatformUser user = userOpt.get();
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            log.warn("Failed platform operator authentication: invalid password for '{}'", identifier);
            throw new BadCredentialsException("Invalid platform credentials.");
        }

        if (!user.isActive()) {
            log.warn("Platform operator authentication rejected: user '{}' is not ACTIVE (status: {})", identifier, user.getStatus());
            throw new DisabledException("Platform operator account is inactive or suspended.");
        }

        // Validate Hardware / TOTP MFA token only if account has enrolled and set up a registered secret OR has a pending OTP
        boolean hasRegisteredMfa = user.isMfaEnabled() && user.getMfaSecret() != null && !user.getMfaSecret().isBlank();
        boolean hasPendingOtp = mfaOtpService != null && mfaOtpService.hasPendingOtp(user.getUsername());

        if (hasRegisteredMfa || hasPendingOtp) {
            String mfaCode = request.mfaCode() != null ? request.mfaCode().trim() : "";
            if (mfaCode.isEmpty()) {
                log.warn("Platform operator authentication rejected: MFA code missing for user '{}'", identifier);
                throw new BadCredentialsException("MFA is enabled on this account. 6-digit TOTP / SMS token is required.");
            }

            boolean valid = false;
            // 1. Dynamic SMS/Email OTP (if pending)
            if (mfaOtpService != null && mfaOtpService.hasPendingOtp(user.getUsername())) {
                if (mfaOtpService.verifyOtp(user.getUsername(), mfaCode)) {
                    valid = true;
                }
            }

            // 2. Authentic TOTP authenticator app code
            if (!valid && hasRegisteredMfa) {
                valid = totpService != null && totpService.verifyCode(user.getMfaSecret(), mfaCode);
            }

            // 3. Emergency backup recovery code
            if (!valid && recoveryCodeRepository != null) {
                String hash = sha256Hex(mfaCode);
                var matchingCode = recoveryCodeRepository.findByUserId(user.getId()).stream()
                        .filter(rc -> !rc.isUsed() && rc.getCodeHash().equalsIgnoreCase(hash))
                        .findFirst();
                if (matchingCode.isPresent()) {
                    valid = true;
                    var rc = matchingCode.get();
                    rc.setUsed(true);
                    rc.setUsedAt(Instant.now());
                    recoveryCodeRepository.save(rc);
                    log.info("Platform operator '{}' authenticated using emergency recovery backup code.", identifier);
                }
            }

            if (!valid) {
                log.warn("Platform operator authentication failed: invalid MFA token for '{}'", identifier);
                throw new BadCredentialsException("Invalid 6-digit MFA / TOTP token. Please check your SMS or authenticator app.");
            }
        }

        user.setLastLoginAt(Instant.now());
        platformUserRepository.save(user);

        Set<String> roles = new HashSet<>();
        roles.add(user.getRole().startsWith("ROLE_") ? user.getRole() : "ROLE_" + user.getRole());

        Set<String> scopes = new HashSet<>();
        scopes.add("saas:read");
        scopes.add("saas:write");
        scopes.add("tenant:manage");
        scopes.add("subscription:manage");
        scopes.add("audit:read");

        if (user.getRole().equalsIgnoreCase("ROLE_PLATFORM_ADMIN") || user.getRole().equalsIgnoreCase("PLATFORM_ADMIN")) {
            scopes.add("platform:superadmin");
            scopes.add("security:manage");
        }

        long ttlSeconds = 28800; // 8 hours
        String masterToken = jwtTokenService.generateMasterToken(user.getUsername(), roles, scopes, ttlSeconds);

        log.info("Platform operator '{}' ({}) successfully authenticated with role '{}'", user.getUsername(), user.getEmail(), user.getRole());

        return new PlatformAuthResponse(
                masterToken,
                "Bearer",
                ttlSeconds,
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                scopes,
                Instant.now()
        );
    }

    public SendStepUpOtpResponse sendLoginOtp(String usernameOrEmail, String ipAddress, String correlationId) {
        if (usernameOrEmail == null || usernameOrEmail.isBlank()) {
            throw new BadCredentialsException("Username or email is required to dispatch verification code.");
        }
        String clean = usernameOrEmail.trim();
        Optional<PlatformUser> userOpt = platformUserRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(clean, clean);
        if (userOpt.isEmpty() && (clean.equalsIgnoreCase("saasadmin") || clean.equalsIgnoreCase("saas.admin") || clean.equalsIgnoreCase("saasadmin@utsolutionsplc.com"))) {
            userOpt = platformUserRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("saas.admin", "saas.admin@utsolutionsplc.com");
        }
        if (userOpt.isEmpty() && (clean.equalsIgnoreCase("admin") || clean.equalsIgnoreCase("masteradmin") || clean.equalsIgnoreCase("master.admin"))) {
            userOpt = platformUserRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("platform.admin", "admin@ut-invoice.internal");
        }
        if (userOpt.isEmpty()) {
            log.warn("Failed to dispatch login OTP: user not found for '{}'", clean);
            throw new BadCredentialsException("Invalid platform credentials.");
        }
        if (mfaOtpService == null) {
            throw new IllegalStateException("MFA OTP dispatch service is unconfigured.");
        }
        return mfaOtpService.dispatchStepUpOtp(userOpt.get(), null, ipAddress, correlationId);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 cryptographic digest unavailable.", e);
        }
    }
}

