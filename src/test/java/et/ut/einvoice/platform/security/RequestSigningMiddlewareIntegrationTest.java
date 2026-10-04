package et.ut.einvoice.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:request_signing_db;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "platform.security.request-signing.enabled=true",
        "platform.security.request-signing.shared-secret=request-signing-test-secret-at-least-32-bytes",
        "management.health.redis.enabled=false"
})
class RequestSigningMiddlewareIntegrationTest {

    private static final String SECRET = "request-signing-test-secret-at-least-32-bytes";
    private static final String PATH = "/api/v1/auth/login";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unsignedMutationIsRejectedByTheRegisteredServletFilter() throws Exception {
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("REQUEST_SIGNATURE_REQUIRED"));
    }

    @Test
    void validSignaturePassesMiddlewareAndReachesRequestValidation() throws Exception {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String timestamp = Long.toString(java.time.Instant.now().getEpochSecond());
        String signature = RequestSignatureVerifier.sign("POST", PATH, null, timestamp, body, SECRET);

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(RequestSignatureVerifier.TIMESTAMP_HEADER, timestamp)
                        .header(RequestSignatureVerifier.SIGNATURE_HEADER, signature)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }
}
