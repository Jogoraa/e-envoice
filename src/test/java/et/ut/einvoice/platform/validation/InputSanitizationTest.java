package et.ut.einvoice.platform.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.platform.config.JacksonSecurityConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.HandlerMapping;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InputSanitizationTest {

    private final InputSanitizer inputSanitizer = new InputSanitizer();

    @Test
    void removesExecutableMarkupProtocolsAndControlCharactersFromDisplayText() {
        String clean = inputSanitizer.sanitize(
                "description",
                "Safe <script>alert('x')</script><img src=x onerror=alert(1)>"
                        + "<a href=\"javascript:alert(1)\">link</a>\u0000"
        );

        String lower = clean.toLowerCase();
        assertTrue(clean.contains("Safe"));
        assertFalse(lower.contains("<script"));
        assertFalse(lower.contains("<img"));
        assertFalse(lower.contains("onerror"));
        assertFalse(lower.contains("javascript:"));
        assertFalse(clean.contains("\u0000"));
    }

    @Test
    void preservesOpaqueSecretsSignaturesAndPayloadsExactly() {
        String secret = "Ab<cd>!+251$%^&*";
        String signedPayload = "{\"markup\":\"<script>must-remain-for-signature</script>\"}";

        assertEquals(secret, inputSanitizer.sanitize("clientSecret", secret));
        assertEquals(signedPayload, inputSanitizer.sanitize("payloadJson", signedPayload));
        assertEquals(secret, inputSanitizer.sanitize("deviceSignature", secret));
    }

    @Test
    void sanitizesJsonBodiesBeforeDeserializerValidationAndControllerInvocation() throws Exception {
        ObjectMapper objectMapper = strictObjectMapper();
        StrictRequestBodyValidationAdvice advice = new StrictRequestBodyValidationAdvice(
                Validation.buildDefaultValidatorFactory().getValidator(), objectMapper, inputSanitizer
        );
        Method method = Fixture.class.getDeclaredMethod("endpoint", BodyFixture.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        MockHttpInputMessage input = new MockHttpInputMessage(
                "{\"description\":\"<script>alert(1)</script>Product\",\"items\":[\"<img src=x onerror=alert(1)>Item\"]}"
                        .getBytes(StandardCharsets.UTF_8)
        );
        input.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        HttpInputMessage sanitized = advice.beforeBodyRead(
                input, parameter, BodyFixture.class, MappingJackson2HttpMessageConverter.class
        );
        JsonNode result = objectMapper.readTree(sanitized.getBody());

        assertFalse(result.path("description").asText().toLowerCase().contains("<script"));
        assertFalse(result.path("items").get(0).asText().toLowerCase().contains("onerror"));
    }

    @Test
    void sanitizesQueryParametersAndPathVariablesBeforeControllerBinding() throws Exception {
        QueryParameterSanitizationFilter filter = new QueryParameterSanitizationFilter(inputSanitizer);
        MockHttpServletRequest queryRequest = new MockHttpServletRequest("GET", "/api/v1/customers");
        queryRequest.addParameter("query", "<script>alert(1)</script>Acme");
        AtomicReference<String> querySeenByHandler = new AtomicReference<>();

        filter.doFilter(queryRequest, new MockHttpServletResponse(), (request, response) ->
                querySeenByHandler.set(((HttpServletRequest) request).getParameter("query"))
        );

        assertFalse(querySeenByHandler.get().toLowerCase().contains("<script"));

        MockHttpServletRequest pathRequest = new MockHttpServletRequest("GET", "/api/v1/invoices/value");
        pathRequest.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                Map.of("irn", "<script>alert(1)</script>IRN-001"));
        new PathVariableSanitizationInterceptor(inputSanitizer)
                .preHandle(pathRequest, new MockHttpServletResponse(), new Object());

        @SuppressWarnings("unchecked")
        Map<String, String> pathVariables = (Map<String, String>) pathRequest
                .getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        assertFalse(pathVariables.get("irn").toLowerCase().contains("<script"));
    }

    private ObjectMapper strictObjectMapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonSecurityConfig().jacksonSecurityCustomizer().customize(builder);
        return builder.build();
    }

    private record BodyFixture(String description, java.util.List<String> items) {}

    @SuppressWarnings("unused")
    private static class Fixture {
        void endpoint(@RequestBody BodyFixture body) {}
    }
}
