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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public PlatformAuthService(
            TenantRepository tenantRepository,
            TenantUserRepository tenantUserRepository,
            PlatformUserRepository platformUserRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService
    ) {
        this.tenantRepository = tenantRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.platformUserRepository = platformUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
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

        Optional<PlatformUser> userOpt = platformUserRepository.findByUsernameOrEmail(identifier, identifier);
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
}
