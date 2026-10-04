package et.ut.einvoice.platform.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class ApiVersionNegotiationFilterTest {

    private final ApiVersionNegotiationFilter filter = new ApiVersionNegotiationFilter(
            "v1", new ObjectMapper().findAndRegisterModules()
    );

    @Test
    void acceptsTheFrozenV1PathAndPublishesTheNegotiatedVersion() throws Exception {
        MockHttpServletRequest request = request("/api/v1/invoices");
        request.addHeader("X-API-Version", "1");
        request.addHeader("Accept", "application/vnd.ut-einvoice.v1+json");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> chainInvoked.set(true));

        assertThat(chainInvoked).isTrue();
        assertThat(request.getAttribute(ApiVersionNegotiationFilter.VERSION_ATTRIBUTE)).isEqualTo(1);
        assertThat(response.getHeader(ApiVersionNegotiationFilter.VERSION_HEADER)).isEqualTo("1");
        assertThat(response.getHeader(ApiVersionNegotiationFilter.SUPPORTED_VERSIONS_HEADER)).isEqualTo("v1");
    }

    @Test
    void rejectsUnversionedApiPathsEvenWhenAHeaderIsSupplied() throws Exception {
        MockHttpServletRequest request = request("/api/invoices");
        request.addHeader("X-API-Version", "1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            throw new AssertionError("The request must not reach the downstream chain");
        });

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("API_VERSION_REQUIRED");
    }

    @Test
    void rejectsAnUnsupportedFutureMajorBeforeItCanFallThroughToV1() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("/api/v2/invoices"), response, (req, res) -> {
            throw new AssertionError("The request must not reach the downstream chain");
        });

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentAsString()).contains("API_VERSION_UNSUPPORTED");
        assertThat(response.getHeader(ApiVersionNegotiationFilter.SUPPORTED_VERSIONS_HEADER)).isEqualTo("v1");
    }

    @Test
    void rejectsConflictingPathAndNegotiationHeaders() throws Exception {
        MockHttpServletRequest request = request("/api/v1/invoices");
        request.addHeader("Accept", "application/vnd.ut-einvoice.v2+json");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            throw new AssertionError("The request must not reach the downstream chain");
        });

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("API_VERSION_MISMATCH");
    }

    @Test
    void canActivateV2IndependentlyWhenThatVersionIsDeployed() throws Exception {
        ApiVersionNegotiationFilter v1AndV2Filter = new ApiVersionNegotiationFilter(
                "v1,v2", new ObjectMapper().findAndRegisterModules()
        );
        MockHttpServletRequest request = request("/api/v2/invoices");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean();

        v1AndV2Filter.doFilter(request, response, (req, res) -> chainInvoked.set(true));

        assertThat(chainInvoked).isTrue();
        assertThat(request.getAttribute(ApiVersionNegotiationFilter.VERSION_ATTRIBUTE)).isEqualTo(2);
        assertThat(response.getHeader(ApiVersionNegotiationFilter.VERSION_HEADER)).isEqualTo("2");
    }

    private MockHttpServletRequest request(String path) {
        return new MockHttpServletRequest("GET", path);
    }
}
