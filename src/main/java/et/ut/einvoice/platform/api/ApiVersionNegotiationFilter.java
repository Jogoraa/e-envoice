package et.ut.einvoice.platform.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.platform.exception.ErrorEnvelope;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enforces the public API's major-version contract at the servlet boundary.
 *
 * <p>The path is the sole selector of the version: {@code /api/v1/...}. A client can send
 * {@value #VERSION_HEADER} or a vendor media type such as
 * {@code application/vnd.ut-einvoice.v1+json} as an explicit compatibility assertion, but
 * neither header may select a version for an unversioned path or disagree with the path.</p>
 */
public class ApiVersionNegotiationFilter extends OncePerRequestFilter {

    public static final String VERSION_HEADER = "X-API-Version";
    public static final String SUPPORTED_VERSIONS_HEADER = "X-Supported-API-Versions";
    public static final String VERSION_ATTRIBUTE = ApiVersionNegotiationFilter.class.getName() + ".version";

    private static final Pattern VERSIONED_API_PATH = Pattern.compile("^/api/v([1-9][0-9]*)(?:/|$)");
    private static final Pattern VERSION_HEADER_VALUE = Pattern.compile("^(?:v)?([1-9][0-9]*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern VENDOR_MEDIA_TYPE = Pattern.compile(
            "^application/vnd\\.ut-einvoice\\.v([1-9][0-9]*)\\+json$", Pattern.CASE_INSENSITIVE
    );

    private final Set<Integer> supportedVersions;
    private final String supportedVersionsHeaderValue;
    private final ObjectMapper objectMapper;

    public ApiVersionNegotiationFilter(String configuredVersions, ObjectMapper objectMapper) {
        this.supportedVersions = parseSupportedVersions(configuredVersions);
        this.supportedVersionsHeaderValue = this.supportedVersions.stream()
                .map(version -> "v" + version)
                .reduce((left, right) -> left + ", " + right)
                .orElseThrow();
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = applicationPath(request);
        return !("/api".equals(path) || path.startsWith("/api/"));
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
        String path = applicationPath(request);
        Matcher pathMatcher = VERSIONED_API_PATH.matcher(path);
        if (!pathMatcher.find()) {
            writeError(
                    request,
                    response,
                    HttpStatus.BAD_REQUEST,
                    "API_VERSION_REQUIRED",
                    "API requests must use a major-version path such as /api/v1/. Header-based version selection is not supported."
            );
            return;
        }

        int pathVersion = Integer.parseInt(pathMatcher.group(1));
        if (!supportedVersions.contains(pathVersion)) {
            writeError(
                    request,
                    response,
                    HttpStatus.NOT_FOUND,
                    "API_VERSION_UNSUPPORTED",
                    "API version v" + pathVersion + " is not supported. Supported versions: " + supportedVersionsHeaderValue + "."
            );
            return;
        }

        try {
            assertHeaderVersionsMatchPath(request, pathVersion);
        } catch (InvalidVersionHeaderException exception) {
            writeError(request, response, HttpStatus.BAD_REQUEST, exception.code(), exception.getMessage());
            return;
        }

        request.setAttribute(VERSION_ATTRIBUTE, pathVersion);
        applyVersionHeaders(response, pathVersion);
        filterChain.doFilter(request, response);
    }

    private void assertHeaderVersionsMatchPath(HttpServletRequest request, int pathVersion) {
        Set<Integer> assertedVersions = new LinkedHashSet<>();
        collectRequestHeaderVersions(request, assertedVersions);
        collectAcceptHeaderVersions(request, assertedVersions);

        if (assertedVersions.size() > 1) {
            throw new InvalidVersionHeaderException(
                    "API_VERSION_MISMATCH",
                    "Conflicting API versions were supplied in X-API-Version and/or Accept headers."
            );
        }
        if (!assertedVersions.isEmpty() && !assertedVersions.contains(pathVersion)) {
            throw new InvalidVersionHeaderException(
                    "API_VERSION_MISMATCH",
                    "The API version declared by X-API-Version or Accept must match the version in the URL path."
            );
        }
    }

    private void collectRequestHeaderVersions(HttpServletRequest request, Set<Integer> assertedVersions) {
        Enumeration<String> values = request.getHeaders(VERSION_HEADER);
        while (values.hasMoreElements()) {
            String value = values.nextElement();
            if (value == null || value.isBlank()) {
                throw new InvalidVersionHeaderException(
                        "API_VERSION_INVALID",
                        "X-API-Version must be a positive major version, for example 1."
                );
            }
            Matcher matcher = VERSION_HEADER_VALUE.matcher(value.trim());
            if (!matcher.matches()) {
                throw new InvalidVersionHeaderException(
                        "API_VERSION_INVALID",
                        "X-API-Version must be a positive major version, for example 1."
                );
            }
            assertedVersions.add(Integer.parseInt(matcher.group(1)));
        }
    }

    private void collectAcceptHeaderVersions(HttpServletRequest request, Set<Integer> assertedVersions) {
        Enumeration<String> acceptHeaders = request.getHeaders("Accept");
        while (acceptHeaders.hasMoreElements()) {
            String acceptHeader = acceptHeaders.nextElement();
            if (acceptHeader == null || acceptHeader.isBlank()) {
                continue;
            }
            for (String value : acceptHeader.split(",")) {
                String mediaType = value.trim().split(";", 2)[0].trim();
                String normalizedMediaType = mediaType.toLowerCase(Locale.ROOT);
                if (!normalizedMediaType.startsWith("application/vnd.ut-einvoice.")) {
                    continue;
                }

                Matcher matcher = VENDOR_MEDIA_TYPE.matcher(mediaType);
                if (!matcher.matches()) {
                    throw new InvalidVersionHeaderException(
                            "API_VERSION_INVALID",
                            "Vendor Accept media types must use application/vnd.ut-einvoice.v{major}+json."
                    );
                }
                assertedVersions.add(Integer.parseInt(matcher.group(1)));
            }
        }
    }

    private static Set<Integer> parseSupportedVersions(String configuredVersions) {
        Set<Integer> versions = new LinkedHashSet<>();
        if (configuredVersions != null) {
            for (String candidate : configuredVersions.split(",")) {
                String value = candidate.trim();
                if (value.isBlank()) {
                    continue;
                }
                Matcher matcher = VERSION_HEADER_VALUE.matcher(value);
                if (!matcher.matches()) {
                    throw new IllegalArgumentException(
                            "platform.api.supported-versions must contain positive major versions (for example: v1,v2)"
                    );
                }
                versions.add(Integer.parseInt(matcher.group(1)));
            }
        }
        if (versions.isEmpty()) {
            throw new IllegalArgumentException("platform.api.supported-versions must declare at least one API version");
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(versions));
    }

    private static String applicationPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
            return path.substring(contextPath.length());
        }
        return path;
    }

    private void applyVersionHeaders(HttpServletResponse response, int version) {
        response.setHeader(VERSION_HEADER, Integer.toString(version));
        response.setHeader(SUPPORTED_VERSIONS_HEADER, supportedVersionsHeaderValue);
        response.addHeader("Vary", VERSION_HEADER);
        response.addHeader("Vary", "Accept");
    }

    private void writeError(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String message
    ) throws IOException {
        String correlationId = request.getHeader("X-Correlation-ID");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("X-Correlation-ID", correlationId);
        response.setHeader(SUPPORTED_VERSIONS_HEADER, supportedVersionsHeaderValue);
        ErrorEnvelope envelope = ErrorEnvelope.of(status.value(), code, message, message, correlationId, applicationPath(request));
        response.getWriter().write(objectMapper.writeValueAsString(envelope));
    }

    private static final class InvalidVersionHeaderException extends RuntimeException {
        private final String code;

        private InvalidVersionHeaderException(String code, String message) {
            super(message);
            this.code = code;
        }

        private String code() {
            return code;
        }
    }
}
