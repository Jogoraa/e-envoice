package et.ut.einvoice.platform.validation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Sanitizes URI-template values before Spring resolves {@code @PathVariable} arguments. */
@Component
public class PathVariableSanitizationInterceptor implements HandlerInterceptor {

    private final InputSanitizer inputSanitizer;

    public PathVariableSanitizationInterceptor(InputSanitizer inputSanitizer) {
        this.inputSanitizer = inputSanitizer;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(attribute instanceof Map<?, ?> variables)) {
            return true;
        }

        Map<String, String> sanitized = new LinkedHashMap<>();
        variables.forEach((key, value) -> sanitized.put(
                String.valueOf(key),
                inputSanitizer.sanitize(String.valueOf(key), value != null ? String.valueOf(value) : null)
        ));
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Collections.unmodifiableMap(sanitized));
        return true;
    }
}
