package et.ut.einvoice.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.platform.context.TenantContext;
import et.ut.einvoice.platform.context.TenantContextHolder;
import et.ut.einvoice.platform.exception.ErrorEnvelope;
import et.ut.einvoice.platform.ratelimit.RequestRateTrackingService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

/**
 * Rejects request bursts after authentication and before Spring MVC resolves a controller.
 * Authenticated traffic is bucketed by tenant and authenticated subject; public traffic is
 * bucketed by remote address. Endpoint keys deliberately omit dynamic path values so changing
 * an identifier cannot evade a route's quota or create unbounded metric/key cardinality.
 */
public class RequestRateTrackingFilter extends OncePerRequestFilter {

    private static final Set<String> STATIC_PATH_SEGMENTS = Set.of(
            "api", "v", "v1", "public", "auth", "saas", "master", "admin", "authority",
            "invoices", "invoice", "receipts", "catalog", "products", "services", "categories",
            "customers", "cancellations", "adjustments", "reports", "webhooks", "subscriptions",
            "offline", "sync", "queue", "audit", "verification", "portability", "exports",
            "tenant", "configuration", "features", "users", "rbac", "account", "environment",
            "access-reviews", "readiness", "api-clients", "compliance-evidence", "verify", "token",
            "by-irn", "summary", "all", "lookup", "sales", "withholding", "stream", "event",
            "checkpoint", "export", "definitions", "jobs", "download", "metrics", "health", "status",
            "login", "logout", "refresh", "send-otp", "step-up", "reprint", "document", "pdf",
            "receipt", "rotate-secret", "lifecycle-transition", "support-session", "terminate", "revoke",
            "finalize", "decision", "enabled", "toggle", "apply", "scopes", "permissions", "profile",
            "sessions", "mfa", "email", "phone", "password", "recovery-codes", "invite", "invitations"
    );

    private final RequestRateTrackingService rateTrackingService;
    private final ObjectMapper objectMapper;
    private final int authenticatedRequestsPerSecond;
    private final int anonymousRequestsPerSecond;

    public RequestRateTrackingFilter(
            RequestRateTrackingService rateTrackingService,
            ObjectMapper objectMapper,
            int authenticatedRequestsPerSecond,
            int anonymousRequestsPerSecond
    ) {
        this.rateTrackingService = rateTrackingService;
        this.objectMapper = objectMapper;
        this.authenticatedRequestsPerSecond = Math.max(1, authenticatedRequestsPerSecond);
        this.anonymousRequestsPerSecond = Math.max(1, anonymousRequestsPerSecond);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || !(request.getRequestURI().startsWith("/api/") || request.getRequestURI().startsWith("/v/"));
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return true;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return true;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        RequestActor actor = requestActor(request);
        String endpoint = endpointKey(request);
        int limit = actor.authenticated() ? authenticatedRequestsPerSecond : anonymousRequestsPerSecond;
        RequestRateTrackingService.RateLimitDecision decision = rateTrackingService.tryAcquire(
                actor.key(), endpoint, actor.type(), limit
        );

        response.setHeader("X-RateLimit-Limit", Integer.toString(limit));
        response.setHeader("X-RateLimit-Remaining", Long.toString(decision.remaining()));
        if (!decision.allowed()) {
            response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
            writeRateLimitError(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private RequestActor requestActor(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getName() != null
                && !authentication.getName().isBlank()) {
            TenantContext tenantContext = TenantContextHolder.getContext();
            String tenant = tenantContext != null && tenantContext.tenantId() != null
                    ? tenantContext.tenantId().toString()
                    : "platform";
            return new RequestActor("authenticated", "user:" + tenant + ":" + authentication.getName(), true);
        }

        String remoteAddress = request.getRemoteAddr();
        return new RequestActor("anonymous", "ip:" + (remoteAddress == null ? "unknown" : remoteAddress), false);
    }

    private String endpointKey(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }

        String normalizedPath = Arrays.stream(path.split("/"))
                .filter(segment -> !segment.isBlank())
                .map(this::normalizeSegment)
                .reduce("", (left, right) -> left + "/" + right);
        return request.getMethod().toUpperCase() + (normalizedPath.isBlank() ? "/" : normalizedPath);
    }

    private String normalizeSegment(String segment) {
        if (segment.matches("v[1-9][0-9]*")) {
            return segment.toLowerCase();
        }
        return STATIC_PATH_SEGMENTS.contains(segment.toLowerCase()) ? segment.toLowerCase() : "{value}";
    }

    private void writeRateLimitError(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String correlationId = response.getHeader("X-Correlation-ID");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = request.getHeader("X-Correlation-ID");
        }
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = java.util.UUID.randomUUID().toString();
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("X-Correlation-ID", correlationId);
        ErrorEnvelope envelope = ErrorEnvelope.of(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "REQUEST_RATE_LIMITED",
                "Request rate exceeded for this endpoint. Please retry shortly.",
                "Request rate exceeded for this endpoint. Please retry shortly.",
                correlationId,
                null
        );
        response.getWriter().write(objectMapper.writeValueAsString(envelope));
    }

    private record RequestActor(String type, String key, boolean authenticated) {
    }
}
