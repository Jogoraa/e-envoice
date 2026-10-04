package et.ut.einvoice.platform.validation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Sanitizes query/form parameters before Spring binds them to controller arguments. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
public class QueryParameterSanitizationFilter extends OncePerRequestFilter {

    private final InputSanitizer inputSanitizer;

    public QueryParameterSanitizationFilter(InputSanitizer inputSanitizer) {
        this.inputSanitizer = inputSanitizer;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        filterChain.doFilter(new SanitizingRequestWrapper(request, inputSanitizer), response);
    }

    private static final class SanitizingRequestWrapper extends HttpServletRequestWrapper {
        private final Map<String, String[]> sanitizedParameters;

        private SanitizingRequestWrapper(HttpServletRequest request, InputSanitizer inputSanitizer) {
            super(request);
            Map<String, String[]> cleaned = new LinkedHashMap<>();
            request.getParameterMap().forEach((name, values) -> {
                String[] sanitized = values == null ? new String[0] : new String[values.length];
                for (int i = 0; i < sanitized.length; i++) {
                    sanitized[i] = inputSanitizer.sanitize(name, values[i]);
                }
                cleaned.put(name, sanitized);
            });
            this.sanitizedParameters = Collections.unmodifiableMap(cleaned);
        }

        @Override
        public String getParameter(String name) {
            String[] values = sanitizedParameters.get(name);
            return values != null && values.length > 0 ? values[0] : null;
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return sanitizedParameters;
        }

        @Override
        public Enumeration<String> getParameterNames() {
            return Collections.enumeration(sanitizedParameters.keySet());
        }

        @Override
        public String[] getParameterValues(String name) {
            String[] values = sanitizedParameters.get(name);
            return values == null ? null : values.clone();
        }
    }
}
