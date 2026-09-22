package et.ut.einvoice.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.platform.context.TenantContext;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.platform.exception.ErrorEnvelope;
import et.ut.einvoice.platform.ratelimit.RateLimitingService;
import et.ut.einvoice.tenancy.domain.ApiClient;
import et.ut.einvoice.tenancy.domain.Tenant;
import et.ut.einvoice.tenancy.domain.TenantStatus;
import et.ut.einvoice.tenancy.repository.ApiClientRepository;
import et.ut.einvoice.tenancy.repository.TenantRepository;
import et.ut.einvoice.tenancy.service.DelegatedTenantSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Component
public class TenantAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantAuthenticationFilter.class);

    private final ApiClientRepository apiClientRepository;
    private final TenantRepository tenantRepository;
    private final RateLimitingService rateLimitingService;
    private final ObjectMapper objectMapper;
    private final JwtTokenService jwtTokenService;
    private final DelegatedTenantSessionService delegatedTenantSessionService;

    public TenantAuthenticationFilter(
            ApiClientRepository apiClientRepository,
            TenantRepository tenantRepository,
            RateLimitingService rateLimitingService,
            ObjectMapper objectMapper,
            JwtTokenService jwtTokenService,
            DelegatedTenantSessionService delegatedTenantSessionService
    ) {
        this.apiClientRepository = apiClientRepository;
        this.tenantRepository = tenantRepository;
        this.rateLimitingService = rateLimitingService;
        this.objectMapper = objectMapper;
        this.jwtTokenService = jwtTokenService;
        this.delegatedTenantSessionService = delegatedTenantSessionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String correlationId = request.getHeader("X-Correlation-ID");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put("correlation_id", correlationId);
        response.setHeader("X-Correlation-ID", correlationId);

        // Allow public/open endpoints including login and authentication routes
        if (path.startsWith("/actuator") || path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs") ||
            path.startsWith("/api/v1/public/") || path.startsWith("/api/v1/auth/") ||
            path.startsWith("/api/v1/saas/auth/") || path.startsWith("/api/v1/master/auth/") ||
            path.contains("/saas/auth/") || path.contains("/master/auth/") || path.contains("/auth/login")) {
            filterChain.doFilter(request, response);
            MDC.clear();
            return;
        }

        try {
            String apiKey = request.getHeader("X-API-Key");
            String clientSecret = request.getHeader("X-Client-Secret");
            String authHeader = request.getHeader("Authorization");
            String tenantHeader = request.getHeader("X-Tenant-ID");

            boolean isSaasOrMasterPath = path.startsWith("/api/v1/saas") || path.startsWith("/api/v1/master");

            // =========================================================================
            // A. SAAS / MASTER ADMIN GATEWAY ENDPOINT PROTECTION
            // =========================================================================
            if (isSaasOrMasterPath) {
                if (authHeader == null || !authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
                    writeError(response, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                            "Master operator Bearer token is required for platform administration endpoints.", correlationId);
                    return;
                }

                String token = authHeader.substring(7).trim();
                Optional<JwtTokenService.ValidatedJwtClaims> claimsOpt = jwtTokenService.validateAndExtract(token);
                if (claimsOpt.isEmpty()) {
                    writeError(response, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED",
                            "Invalid, expired, or untrusted JWT authentication token.", correlationId);
                    return;
                }

                JwtTokenService.ValidatedJwtClaims claims = claimsOpt.get();

                // Reject ordinary tenant tokens attempting to access Master/SaaS APIs
                boolean hasMasterRole = claims.roles().stream().anyMatch(r ->
                        r.equalsIgnoreCase("ROLE_PLATFORM_ADMIN") ||
                        r.equalsIgnoreCase("ROLE_SAAS_ADMIN") ||
                        r.equalsIgnoreCase("ROLE_SAAS_OPERATOR") ||
                        r.equalsIgnoreCase("ROLE_AUTHORITY_AUDITOR") ||
                        r.equalsIgnoreCase("ROLE_DELEGATED_OPERATOR")
                );

                if (!claims.isMasterToken() && !hasMasterRole) {
                    writeError(response, HttpStatus.FORBIDDEN, "TENANT_CANNOT_ACCESS_MASTER_API",
                            "Tenant tokens cannot access SaaS Master platform administration endpoints.", correlationId);
                    return;
                }

                // Establish platform operator context
                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                claims.roles().forEach(r -> authorities.add(new SimpleGrantedAuthority(r)));
                claims.scopes().forEach(s -> authorities.add(new SimpleGrantedAuthority("SCOPE_" + s)));
                if (authorities.isEmpty()) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_SAAS_ADMIN"));
                }

                var auth = new UsernamePasswordAuthenticationToken(claims.subject(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);

                TenantContext platformContext = new TenantContext(
                        UUID.fromString("00000000-0000-0000-0000-000000000000"),
                        "PLATFORM_OPERATOR",
                        null,
                        claims.subject(),
                        "SAAS_MASTER_PORTAL",
                        claims.roles(),
                        claims.scopes(),
                        null,
                        correlationId
                );
                TenantContextHolder.setContext(platformContext);

                filterChain.doFilter(request, response);
                return;
            }

            // =========================================================================
            // B. TENANT BUSINESS ENDPOINT PROTECTION
            // =========================================================================
            UUID tenantId = null;
            String clientId = "DEFAULT_CLIENT";
            String userId = null;
            Set<String> scopes = new HashSet<>();
            Set<String> roles = new HashSet<>();

            // 1. API Client Key & Secret Verification (M2M Authenticated Ingress)
            if (apiKey != null && !apiKey.isBlank()) {
                if (clientSecret == null || clientSecret.isBlank()) {
                    writeError(response, HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "X-Client-Secret header is required when X-API-Key is provided.", correlationId);
                    return;
                }

                Optional<ApiClient> clientOpt = apiClientRepository.findByClientId(apiKey.trim());
                if (clientOpt.isEmpty()) {
                    writeError(response, HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid API Key or Client credentials.", correlationId);
                    return;
                }

                ApiClient client = clientOpt.get();
                byte[] expected = client.getClientSecretHash().getBytes(StandardCharsets.UTF_8);
                byte[] provided = clientSecret.trim().getBytes(StandardCharsets.UTF_8);
                boolean secretMatches = MessageDigest.isEqual(expected, provided);
                if (!secretMatches) {
                    try {
                        byte[] hash = MessageDigest.getInstance("SHA-256").digest(provided);
                        StringBuilder sb = new StringBuilder();
                        for (byte b : hash) sb.append(String.format("%02x", b));
                        byte[] providedHash = sb.toString().getBytes(StandardCharsets.UTF_8);
                        secretMatches = MessageDigest.isEqual(expected, providedHash);
                    } catch (Exception ignored) {}
                }
                if (!secretMatches) {
                    writeError(response, HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid API Key or Client credentials.", correlationId);
                    return;
                }

                if (!"ACTIVE".equalsIgnoreCase(client.getStatus())) {
                    writeError(response, HttpStatus.UNAUTHORIZED, "CLIENT_REVOKED", "The provided API client has been suspended or revoked.", correlationId);
                    return;
                }

                // Verify tenant existence and active status
                Optional<Tenant> tenantOpt = tenantRepository.findById(client.getTenantId());
                if (tenantOpt.isEmpty()) {
                    writeError(response, HttpStatus.FORBIDDEN, "TENANT_NOT_FOUND", "Associated tenant account does not exist.", correlationId);
                    return;
                }
                var tStatus = tenantOpt.get().getStatus();
                if (tStatus == TenantStatus.SUSPENDED ||
                    tStatus == TenantStatus.DEACTIVATED ||
                    tStatus == TenantStatus.ARCHIVED) {
                    writeError(response, HttpStatus.FORBIDDEN, "TENANT_SUSPENDED", "Tenant account is suspended or deactivated.", correlationId);
                    return;
                }

                // Check for forged or cross-tenant X-Tenant-ID
                if (tenantHeader != null && !tenantHeader.isBlank()) {
                    try {
                        UUID requestedTenantId = UUID.fromString(tenantHeader.trim());
                        if (!requestedTenantId.equals(client.getTenantId())) {
                            writeError(response, HttpStatus.FORBIDDEN, "CROSS_TENANT_ACCESS_DENIED",
                                    "Authenticated client is not authorized for the requested tenant.", correlationId);
                            return;
                        }
                    } catch (IllegalArgumentException ignored) {}
                }

                tenantId = client.getTenantId();
                clientId = client.getClientId();
                userId = "api-client-" + client.getClientId();
                scopes = client.getScopeSet();
                roles.add("ROLE_API_CLIENT");
                roles.add("ROLE_TENANT_USER");
                if (scopes.contains("tenant:admin")) {
                    roles.add("ROLE_TENANT_ADMIN");
                }
                client.updateLastUsed();
                apiClientRepository.save(client);
            }
            // 2. JWT Bearer Token Authentication
            else if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
                String token = authHeader.substring(7).trim();
                Optional<JwtTokenService.ValidatedJwtClaims> claimsOpt = jwtTokenService.validateAndExtract(token);
                if (claimsOpt.isEmpty()) {
                    writeError(response, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "Invalid, expired, or untrusted JWT authentication token.", correlationId);
                    return;
                }

                JwtTokenService.ValidatedJwtClaims claims = claimsOpt.get();

                // Prohibit direct Master JWT from calling tenant APIs without delegation
                if (claims.isMasterToken() && !claims.isDelegated()) {
                    log.warn("[SECURITY] Master token attempted direct access to tenant endpoint. path={} subject={} correlationId={}",
                            path, claims.subject(), correlationId);
                    writeError(response, HttpStatus.FORBIDDEN, "MASTER_DIRECT_TENANT_ACCESS_FORBIDDEN",
                            "Direct master token access to tenant business APIs is prohibited. Use authorized delegated access. [path=" + path + "]", correlationId);
                    return;
                }

                // Handle Delegated Tenant Support Session
                if (claims.isDelegated()) {
                    if (claims.sessionId() != null && !claims.sessionId().isBlank()) {
                        try {
                            UUID sessUuid = UUID.fromString(claims.sessionId());
                            if (!delegatedTenantSessionService.isSessionActive(sessUuid)) {
                                writeError(response, HttpStatus.UNAUTHORIZED, "DELEGATED_SESSION_INVALID",
                                        "Delegated support session has expired or been revoked by platform operator.", correlationId);
                                return;
                            }
                        } catch (IllegalArgumentException ignored) {}
                    }

                    // Enforce Read-Only Support restrictions
                    if ("READ_ONLY_SUPPORT".equalsIgnoreCase(claims.accessType())) {
                        String method = request.getMethod();
                        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) ||
                            "PATCH".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
                            writeError(response, HttpStatus.FORBIDDEN, "READ_ONLY_DELEGATED_ACCESS",
                                    "Read-only support session cannot mutate tenant financial or business records.", correlationId);
                            return;
                        }
                    }
                }

                if (claims.tenantId() == null) {
                    writeError(response, HttpStatus.FORBIDDEN, "TENANT_NOT_BOUND",
                            "Authenticated token is not bound to an authorized tenant context.", correlationId);
                    return;
                }

                Optional<Tenant> tenantOpt = tenantRepository.findById(claims.tenantId());
                if (tenantOpt.isEmpty()) {
                    writeError(response, HttpStatus.FORBIDDEN, "TENANT_NOT_FOUND", "Associated tenant account does not exist.", correlationId);
                    return;
                }
                var tStatus = tenantOpt.get().getStatus();
                if (tStatus == TenantStatus.SUSPENDED ||
                    tStatus == TenantStatus.DEACTIVATED ||
                    tStatus == TenantStatus.ARCHIVED) {
                    writeError(response, HttpStatus.FORBIDDEN, "TENANT_SUSPENDED", "Tenant account is suspended or deactivated.", correlationId);
                    return;
                }

                if (tenantHeader != null && !tenantHeader.isBlank()) {
                    try {
                        UUID requestedTenantId = UUID.fromString(tenantHeader.trim());
                        if (!requestedTenantId.equals(claims.tenantId())) {
                            writeError(response, HttpStatus.FORBIDDEN, "CROSS_TENANT_ACCESS_DENIED",
                                    "Authenticated client is not authorized for the requested tenant.", correlationId);
                            return;
                        }
                    } catch (IllegalArgumentException ignored) {}
                }

                tenantId = claims.tenantId();
                clientId = claims.subject();
                userId = claims.isDelegated() && claims.delegatedBy() != null ? claims.delegatedBy() : claims.subject();
                roles.addAll(claims.roles());
                scopes.addAll(claims.scopes());
                if (roles.isEmpty()) {
                    roles.add("ROLE_TENANT_USER");
                }
            }
            // 3. Authority Auditor Header / Role
            else if (request.getHeader("X-Authority-Token") != null) {
                roles.add("ROLE_AUTHORITY_AUDITOR");
                scopes.add("authority:audit");
                tenantId = UUID.fromString("00000000-0000-0000-0000-000000000000"); // Authority scope
                clientId = "AUTHORITY_AUDITOR";
                userId = "AUTHORITY_AUDITOR";
            }

            // Reject if no valid identity could be established on protected /api/v1 routes
            if (tenantId == null && path.startsWith("/api/v1")) {
                writeError(response, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                        "Authentication credentials (X-API-Key / Bearer token) are required.", correlationId);
                return;
            }

            if (tenantId != null) {
                MDC.put("tenant_id", tenantId.toString());

                // Rate limiting check
                if (!rateLimitingService.tryAcquire(tenantId, clientId, 120)) {
                    writeError(response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                            "Request rate limit exceeded. Please throttle your requests.", correlationId);
                    return;
                }

                TenantContext context = new TenantContext(
                        tenantId,
                        tenantId.toString(),
                        null,
                        userId != null ? userId : "USER",
                        clientId,
                        roles,
                        scopes,
                        null,
                        correlationId
                );
                TenantContextHolder.setContext(context);

                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                roles.forEach(r -> authorities.add(new SimpleGrantedAuthority(r)));
                scopes.forEach(s -> authorities.add(new SimpleGrantedAuthority("SCOPE_" + s)));

                var auth = new UsernamePasswordAuthenticationToken(context.userId(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
            MDC.clear();
        }
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String code, String message, String correlationId) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("X-Correlation-ID", correlationId);
        ErrorEnvelope envelope = ErrorEnvelope.of(status.value(), code, message, message, correlationId, null);
        response.getWriter().write(objectMapper.writeValueAsString(envelope));
    }
}

