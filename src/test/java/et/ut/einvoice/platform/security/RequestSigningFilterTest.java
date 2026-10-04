package et.ut.einvoice.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestSigningFilterTest {

    private static final String SECRET = "request-signing-test-secret-at-least-32-bytes";
    private static final long NOW = 1_800_000_000L;

    @Test
    void validSignatureAllowsTheOriginalBodyToReachTheApplication() throws Exception {
        byte[] body = "{\"description\":\"signed request\"}".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = request("POST", "/api/v1/invoices", "async=false", body);
        sign(request, body, Long.toString(NOW));
        AtomicBoolean reachedApplication = new AtomicBoolean();
        AtomicReference<byte[]> receivedBody = new AtomicReference<>();
        FilterChain chain = (wrappedRequest, response) -> {
            reachedApplication.set(true);
            receivedBody.set(wrappedRequest.getInputStream().readAllBytes());
        };

        filter().doFilter(request, new MockHttpServletResponse(), chain);

        assertTrue(reachedApplication.get());
        assertArrayEquals(body, receivedBody.get());
    }

    @Test
    void changedBodyIsRejectedBeforeTheApplication() throws Exception {
        byte[] signedBody = "{\"amount\":100}".getBytes(StandardCharsets.UTF_8);
        byte[] tamperedBody = "{\"amount\":999}".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = request("POST", "/api/v1/invoices", null, tamperedBody);
        sign(request, signedBody, Long.toString(NOW));
        AtomicBoolean reachedApplication = new AtomicBoolean();

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter().doFilter(request, response, (ignored, ignoredResponse) -> reachedApplication.set(true));

        assertEquals(401, response.getStatus());
        assertFalse(reachedApplication.get());
        assertTrue(response.getContentAsString().contains("REQUEST_SIGNATURE_INVALID"));
        assertFalse(response.getContentAsString().contains("999"));
    }

    @Test
    void missingOrExpiredSignatureIsRejected() throws Exception {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest unsigned = request("PATCH", "/api/v1/customers/123", null, body);
        MockHttpServletResponse missingResponse = new MockHttpServletResponse();
        filter().doFilter(unsigned, missingResponse, (ignored, ignoredResponse) -> {
            throw new AssertionError("unsigned request must not reach the application");
        });
        assertEquals(401, missingResponse.getStatus());
        assertTrue(missingResponse.getContentAsString().contains("REQUEST_SIGNATURE_REQUIRED"));

        MockHttpServletRequest stale = request("DELETE", "/api/v1/customers/123", null, new byte[0]);
        sign(stale, new byte[0], Long.toString(NOW - 301));
        MockHttpServletResponse staleResponse = new MockHttpServletResponse();
        filter().doFilter(stale, staleResponse, (ignored, ignoredResponse) -> {
            throw new AssertionError("expired signature must not reach the application");
        });
        assertEquals(401, staleResponse.getStatus());
        assertTrue(staleResponse.getContentAsString().contains("REQUEST_SIGNATURE_EXPIRED"));
    }

    @Test
    void signaturesBindMethodPathAndQueryAndGetRequestsRemainUnaffected() throws Exception {
        byte[] body = "{\"x\":1}".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest changedQuery = request("PUT", "/api/v1/invoices", "tenant=other", body);
        String timestamp = Long.toString(NOW);
        changedQuery.addHeader(RequestSignatureVerifier.TIMESTAMP_HEADER, timestamp);
        changedQuery.addHeader(RequestSignatureVerifier.SIGNATURE_HEADER, RequestSignatureVerifier.sign(
                "PUT", "/api/v1/invoices", "tenant=original", timestamp, body, SECRET
        ));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter().doFilter(changedQuery, response, (ignored, ignoredResponse) -> {
            throw new AssertionError("query-tampered request must not reach the application");
        });
        assertEquals(401, response.getStatus());

        AtomicBoolean reachedApplication = new AtomicBoolean();
        filter().doFilter(request("GET", "/api/v1/invoices", null, new byte[0]), new MockHttpServletResponse(),
                (ignored, ignoredResponse) -> reachedApplication.set(true));
        assertTrue(reachedApplication.get());
    }

    @Test
    void signedFormRequestsRetainTheirParametersAfterRawBodyVerification() throws Exception {
        byte[] body = "description=signed+form&tag=one&tag=two".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = request("POST", "/api/v1/invoices", "source=api", body);
        request.setContentType("application/x-www-form-urlencoded");
        sign(request, body, Long.toString(NOW));
        AtomicReference<String[]> tags = new AtomicReference<>();
        AtomicReference<String> description = new AtomicReference<>();
        AtomicReference<String> source = new AtomicReference<>();

        filter().doFilter(request, new MockHttpServletResponse(), (wrappedRequest, response) -> {
            description.set(wrappedRequest.getParameter("description"));
            tags.set(wrappedRequest.getParameterValues("tag"));
            source.set(wrappedRequest.getParameter("source"));
        });

        assertEquals("signed form", description.get());
        assertArrayEquals(new String[]{"one", "two"}, tags.get());
        assertEquals("api", source.get());
    }

    @Test
    void missingServerSecretFailsClosed() throws Exception {
        RequestSigningFilter unavailableFilter = new RequestSigningFilter(
                new RequestSignatureVerifier("", 300, fixedClock()), objectMapper(), true, 1_024
        );
        MockHttpServletResponse response = new MockHttpServletResponse();
        unavailableFilter.doFilter(request("POST", "/api/v1/invoices", null, new byte[0]), response,
                (ignored, ignoredResponse) -> {
                    throw new AssertionError("unsigned request must not reach the application");
                });

        assertEquals(503, response.getStatus());
        assertTrue(response.getContentAsString().contains("REQUEST_SIGNING_UNAVAILABLE"));
    }

    private RequestSigningFilter filter() {
        return new RequestSigningFilter(
                new RequestSignatureVerifier(SECRET, 300, fixedClock()), objectMapper(), true, 1_024 * 1_024
        );
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    private MockHttpServletRequest request(String method, String uri, String query, byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setQueryString(query);
        request.setContentType("application/json");
        request.setContent(body);
        return request;
    }

    private void sign(MockHttpServletRequest request, byte[] body, String timestamp) {
        request.addHeader(RequestSignatureVerifier.TIMESTAMP_HEADER, timestamp);
        request.addHeader(RequestSignatureVerifier.SIGNATURE_HEADER, RequestSignatureVerifier.sign(
                request.getMethod(), request.getRequestURI(), request.getQueryString(), timestamp, body, SECRET
        ));
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC);
    }
}
