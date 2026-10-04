package et.ut.einvoice.platform.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.platform.security.RequestRateTrackingFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestRateTrackingTest {

    @Test
    void tracksRequestsIndependentlyPerActorAndEndpoint() {
        RequestRateTrackingService service = new RequestRateTrackingService();

        assertTrue(service.tryAcquire("user:tenant-a:alice", "GET/api/v1/invoices/{value}", "authenticated", 2).allowed());
        assertTrue(service.tryAcquire("user:tenant-a:alice", "GET/api/v1/invoices/{value}", "authenticated", 2).allowed());
        assertFalse(service.tryAcquire("user:tenant-a:alice", "GET/api/v1/invoices/{value}", "authenticated", 2).allowed());

        assertTrue(service.tryAcquire("user:tenant-a:bob", "GET/api/v1/invoices/{value}", "authenticated", 2).allowed());
        assertTrue(service.tryAcquire("user:tenant-a:alice", "GET/api/v1/customers/{value}", "authenticated", 2).allowed());
    }

    @Test
    void middlewareBlocksBurstBeforeTheFilterChainAndUsesNormalizedRoutes() throws Exception {
        RequestRateTrackingFilter filter = new RequestRateTrackingFilter(
                new RequestRateTrackingService(), objectMapper(), 2, 2
        );
        AtomicBoolean reachedApplication = new AtomicBoolean();
        FilterChain chain = (request, response) -> reachedApplication.set(true);

        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest request = request("/api/v1/public/verify/IRN-" + i);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
            assertTrue(reachedApplication.get());
            reachedApplication.set(false);
        }

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(request("/api/v1/public/verify/IRN-evade-by-changing-id"), blocked, chain);

        assertEquals(429, blocked.getStatus());
        assertFalse(reachedApplication.get());
        assertEquals("2", blocked.getHeader("X-RateLimit-Limit"));
        assertEquals("0", blocked.getHeader("X-RateLimit-Remaining"));
        assertEquals("1", blocked.getHeader("Retry-After"));
        assertTrue(blocked.getContentAsString().contains("REQUEST_RATE_LIMITED"));
        assertFalse(blocked.getContentAsString().contains("IRN-evade-by-changing-id"));
    }

    @Test
    void middlewareKeysAuthenticatedTrafficByUserInsteadOfSharedClientAddress() throws Exception {
        RequestRateTrackingFilter filter = new RequestRateTrackingFilter(
                new RequestRateTrackingService(), objectMapper(), 1, 1
        );
        AtomicBoolean reachedApplication = new AtomicBoolean();
        FilterChain chain = (request, response) -> reachedApplication.set(true);

        try {
            authenticateAs("alice");
            filter.doFilter(request("/api/v1/invoices/111"), new MockHttpServletResponse(), chain);
            assertTrue(reachedApplication.get());

            reachedApplication.set(false);
            authenticateAs("bob");
            filter.doFilter(request("/api/v1/invoices/222"), new MockHttpServletResponse(), chain);
            assertTrue(reachedApplication.get());

            reachedApplication.set(false);
            authenticateAs("alice");
            MockHttpServletResponse blocked = new MockHttpServletResponse();
            filter.doFilter(request("/api/v1/invoices/333"), blocked, chain);
            assertEquals(429, blocked.getStatus());
            assertFalse(reachedApplication.get());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void preflightAndNonApiRequestsAreNotRateLimited() throws Exception {
        RequestRateTrackingFilter filter = new RequestRateTrackingFilter(
                new RequestRateTrackingService(), objectMapper(), 1, 1
        );
        AtomicBoolean reachedApplication = new AtomicBoolean();
        FilterChain chain = (request, response) -> reachedApplication.set(true);

        MockHttpServletRequest preflight = request("/api/v1/invoices");
        preflight.setMethod("OPTIONS");
        filter.doFilter(preflight, new MockHttpServletResponse(), chain);
        assertTrue(reachedApplication.get());

        reachedApplication.set(false);
        filter.doFilter(request("/swagger-ui/index.html"), new MockHttpServletResponse(), chain);
        assertTrue(reachedApplication.get());
    }

    private MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRemoteAddr("203.0.113.24");
        return request;
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    private void authenticateAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(username, null, java.util.List.of())
        );
    }
}
