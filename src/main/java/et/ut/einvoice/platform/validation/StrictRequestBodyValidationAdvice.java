package et.ut.einvoice.platform.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;

/**
 * Enforces Jakarta Bean Validation for every JSON request body before a
 * controller method is invoked. Individual controllers still use {@code @Valid}
 * for OpenAPI/Spring metadata, but this advice is the non-bypassable boundary.
 */
@ControllerAdvice(annotations = Controller.class)
public class StrictRequestBodyValidationAdvice extends RequestBodyAdviceAdapter {

    private final Validator validator;
    private final ObjectMapper objectMapper;
    private final InputSanitizer inputSanitizer;

    public StrictRequestBodyValidationAdvice(Validator validator, ObjectMapper objectMapper, InputSanitizer inputSanitizer) {
        this.validator = validator;
        this.objectMapper = objectMapper;
        this.inputSanitizer = inputSanitizer;
    }

    @Override
    public boolean supports(
            MethodParameter methodParameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType
    ) {
        return methodParameter.hasParameterAnnotation(RequestBody.class);
    }

    @Override
    public HttpInputMessage beforeBodyRead(
            HttpInputMessage inputMessage,
            MethodParameter parameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType
    ) throws IOException {
        if (!isJson(inputMessage.getHeaders().getContentType())) {
            return inputMessage;
        }

        byte[] rawBody = inputMessage.getBody().readAllBytes();
        if (rawBody.length == 0) {
            return new SanitizedHttpInputMessage(inputMessage.getHeaders(), rawBody);
        }

        JsonNode parsed = objectMapper.readTree(rawBody);
        if (parsed == null) {
            return new SanitizedHttpInputMessage(inputMessage.getHeaders(), rawBody);
        }

        byte[] sanitizedBody = objectMapper.writeValueAsBytes(inputSanitizer.sanitizeJson(parsed));
        return new SanitizedHttpInputMessage(inputMessage.getHeaders(), sanitizedBody);
    }

    @Override
    public Object afterBodyRead(
            Object body,
            HttpInputMessage inputMessage,
            MethodParameter parameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType
    ) {
        if (body instanceof Map<?, ?> || body instanceof Object[] || body instanceof Iterable<?>) {
            throw new IllegalArgumentException("API request bodies must use an explicit schema type.");
        }

        Set<ConstraintViolation<Object>> violations = validator.validate(body);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        return body;
    }

    private boolean isJson(MediaType contentType) {
        return contentType != null
                && "application".equalsIgnoreCase(contentType.getType())
                && ("json".equalsIgnoreCase(contentType.getSubtype())
                || contentType.getSubtype().toLowerCase(java.util.Locale.ROOT).endsWith("+json"));
    }

    private static final class SanitizedHttpInputMessage implements HttpInputMessage {
        private final HttpHeaders headers;
        private final byte[] body;

        private SanitizedHttpInputMessage(HttpHeaders sourceHeaders, byte[] body) {
            this.headers = new HttpHeaders();
            this.headers.putAll(sourceHeaders);
            this.headers.setContentLength(body.length);
            this.body = body;
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }
    }
}
