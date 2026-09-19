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

    public TenantAuthenticationFilter(
            ApiClientRepository apiClientRepository,
            TenantRepository tenantRepository,
            RateLimitingService rateLimitingService,
            ObjectMapper objectMapper,
            JwtTokenService jwtTokenService
    ) {
        this.apiClientRepository = apiClientRepository;
        this.tenantRepository = tenantRepository;
        this.rateLimitingService = rateLimitingService;
        this.objectMapper = objectMapper;
        this.jwtTokenService = jwtTokenService;
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

        // Allow public/open endpoints
        if (path.startsWith("/actuator") || path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs") || path.startsWith("/api/v1/public/")) {
            filterChain.doFilter(request, response);
            MDC.clear();
            return;
        }

        try {
            UUID tenantId = null;
            String clientId = "DEFAULT_CLIENT";
            Set<String> scopes = new HashSet<>();
            Set<String> roles = new HashSet<>();

            String apiKey = request.getHeader("X-API-Key");
            String clientSecret = request.getHeader("X-Client-Secret");
            String authHeader = request.getHeader("Authorization");
            String tenantHeader = request.getHeader("X-Tenant-ID");

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
                // Constant-time secret comparison (supports both plaintext secret match or SHA-256 digest match)
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
            }

            // Reject if no valid identity could be established on protected /api/v1 routes
            if (tenantId == null && path.startsWith("/api/v1")) {
                writeError(response, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                        "Authentication credentials (X-API-Key / Bearer token) are required.", correlationId);
                return;
            }

            // Fallback for non-api or integration test contexts if tenantId established
            if (tenantId != null) {
                MDC.put("tenant_id", tenantId.toString());

                // Rate limiting check
                if (!rateLimitingService.tryAcquire(tenantId, clientId, 120)) {
                    writeError(response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                            "Request rate limit exceeded. Please throttle your requests.", correlationId);
                    return;
                }

                TenantContext context = TenantContext.createWithClient(tenantId, clientId, roles, scopes, correlationId);
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
