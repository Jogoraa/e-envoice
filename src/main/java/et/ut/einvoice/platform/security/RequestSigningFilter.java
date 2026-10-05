package et.ut.einvoice.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.platform.exception.ErrorEnvelope;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Servlet-boundary HMAC enforcement for mutating API requests. The request is buffered once and
 * replayed downstream so signature validation never consumes the controller's request body.
 */
public class RequestSigningFilter extends OncePerRequestFilter {

    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final RequestSignatureVerifier signatureVerifier;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final long maxBodyBytes;

    public RequestSigningFilter(
            RequestSignatureVerifier signatureVerifier,
            ObjectMapper objectMapper,
            boolean enabled,
            long maxBodyBytes
    ) {
        this.signatureVerifier = signatureVerifier;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.maxBodyBytes = Math.max(1L, maxBodyBytes);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        String path = request.getRequestURI();
        return !enabled
                || !MUTATING_METHODS.contains(method)
                // The invitation bearer token is the credential for this public, one-time activation endpoint.
                // Requiring the server-only request-signing secret here would make the browser flow impossible.
                || path.matches("/api/v[0-9]+/auth/invitations/accept")
                || !(path.startsWith("/api/") || path.startsWith("/v/"));
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
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        final byte[] body;
        try {
            body = readBody(request);
        } catch (PayloadTooLargeException ex) {
            writeError(request, response, HttpStatus.PAYLOAD_TOO_LARGE, "REQUEST_BODY_TOO_LARGE",
                    "Request body exceeds the configured signing limit.");
            return;
        }

        RequestSignatureVerifier.VerificationResult verification = signatureVerifier.verify(request, body);
        if (verification != RequestSignatureVerifier.VerificationResult.VALID) {
            writeVerificationError(request, response, verification);
            return;
        }

        filterChain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private byte[] readBody(HttpServletRequest request) throws IOException, PayloadTooLargeException {
        long declaredLength = request.getContentLengthLong();
        if (declaredLength > maxBodyBytes) {
            throw new PayloadTooLargeException();
        }

        InputStream input = request.getInputStream();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8_192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBodyBytes) {
                    throw new PayloadTooLargeException();
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private void writeVerificationError(
            HttpServletRequest request,
            HttpServletResponse response,
            RequestSignatureVerifier.VerificationResult verification
    ) throws IOException {
        switch (verification) {
            case NOT_CONFIGURED -> writeError(request, response, HttpStatus.SERVICE_UNAVAILABLE,
                    "REQUEST_SIGNING_UNAVAILABLE", "Request signing is not configured.");
            case MISSING_SIGNATURE -> writeError(request, response, HttpStatus.UNAUTHORIZED,
                    "REQUEST_SIGNATURE_REQUIRED", "A valid request signature and timestamp are required.");
            case EXPIRED -> writeError(request, response, HttpStatus.UNAUTHORIZED,
                    "REQUEST_SIGNATURE_EXPIRED", "Request signature timestamp is outside the allowed window.");
            case INVALID_TIMESTAMP, INVALID_SIGNATURE -> writeError(request, response, HttpStatus.UNAUTHORIZED,
                    "REQUEST_SIGNATURE_INVALID", "Request signature could not be verified.");
            case VALID -> throw new IllegalStateException("A valid signature does not produce an error response");
        }
    }

    private void writeError(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String message
    ) throws IOException {
        String correlationId = response.getHeader("X-Correlation-ID");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = request.getHeader("X-Correlation-ID");
        }
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("X-Correlation-ID", correlationId);
        ErrorEnvelope envelope = ErrorEnvelope.of(status.value(), code, message, message, correlationId, null);
        response.getWriter().write(objectMapper.writeValueAsString(envelope));
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        private final Map<String, String[]> formParameters;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
            this.formParameters = isFormUrlEncoded(request)
                    ? parseFormParameters(request, body)
                    : null;
        }

        @Override
        public ServletInputStream getInputStream() {
            return new CachedServletInputStream(body);
        }

        @Override
        public BufferedReader getReader() {
            Charset charset;
            try {
                charset = getCharacterEncoding() == null ? StandardCharsets.UTF_8 : Charset.forName(getCharacterEncoding());
            } catch (Exception ex) {
                charset = StandardCharsets.UTF_8;
            }
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }

        @Override
        public String getParameter(String name) {
            String[] values = getParameterValues(name);
            return values == null || values.length == 0 ? null : values[0];
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return formParameters == null ? super.getParameterMap() : formParameters;
        }

        @Override
        public Enumeration<String> getParameterNames() {
            return formParameters == null
                    ? super.getParameterNames()
                    : Collections.enumeration(formParameters.keySet());
        }

        @Override
        public String[] getParameterValues(String name) {
            if (formParameters == null) {
                return super.getParameterValues(name);
            }
            String[] values = formParameters.get(name);
            return values == null ? null : values.clone();
        }

        private static boolean isFormUrlEncoded(HttpServletRequest request) {
            String contentType = request.getContentType();
            return contentType != null && contentType.toLowerCase(Locale.ROOT)
                    .startsWith("application/x-www-form-urlencoded");
        }

        private static Map<String, String[]> parseFormParameters(HttpServletRequest request, byte[] body) {
            Charset charset;
            try {
                charset = request.getCharacterEncoding() == null
                        ? StandardCharsets.UTF_8
                        : Charset.forName(request.getCharacterEncoding());
            } catch (Exception ex) {
                charset = StandardCharsets.UTF_8;
            }

            Map<String, List<String>> parsed = new LinkedHashMap<>();
            appendUrlEncoded(parsed, request.getQueryString(), charset);
            appendUrlEncoded(parsed, new String(body, charset), charset);

            Map<String, String[]> result = new LinkedHashMap<>();
            parsed.forEach((name, values) -> result.put(name, values.toArray(String[]::new)));
            return Collections.unmodifiableMap(result);
        }

        private static void appendUrlEncoded(Map<String, List<String>> target, String encoded, Charset charset) {
            if (encoded == null || encoded.isBlank()) {
                return;
            }
            for (String pair : encoded.split("&")) {
                int delimiter = pair.indexOf('=');
                String name = decode(delimiter < 0 ? pair : pair.substring(0, delimiter), charset);
                String value = decode(delimiter < 0 ? "" : pair.substring(delimiter + 1), charset);
                target.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
            }
        }

        private static String decode(String value, Charset charset) {
            try {
                return URLDecoder.decode(value, charset);
            } catch (IllegalArgumentException ex) {
                return value;
            }
        }
    }

    private static final class CachedServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream input;

        private CachedServletInputStream(byte[] body) {
            this.input = new ByteArrayInputStream(body);
        }

        @Override
        public boolean isFinished() {
            return input.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            if (readListener == null) {
                return;
            }
            try {
                if (!isFinished()) {
                    readListener.onDataAvailable();
                }
                readListener.onAllDataRead();
            } catch (IOException ex) {
                readListener.onError(ex);
            }
        }

        @Override
        public int read() {
            return input.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            return input.read(buffer, offset, length);
        }
    }

    private static final class PayloadTooLargeException extends Exception {
    }
}
