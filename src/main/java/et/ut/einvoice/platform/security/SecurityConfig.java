package et.ut.einvoice.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.platform.api.ApiVersionNegotiationFilter;
import et.ut.einvoice.platform.exception.ErrorEnvelope;
import et.ut.einvoice.platform.ratelimit.RequestRateTrackingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final TenantAuthenticationFilter tenantAuthenticationFilter;
    private final ObjectMapper objectMapper;
    private final List<String> allowedOrigins;

    public SecurityConfig(
            TenantAuthenticationFilter tenantAuthenticationFilter,
            ObjectMapper objectMapper,
            @Value("${platform.security.cors.allowed-origins:http://localhost:3000,http://localhost:8080}") String allowedOriginsStr
    ) {
        this.tenantAuthenticationFilter = tenantAuthenticationFilter;
        this.objectMapper = objectMapper;
        this.allowedOrigins = Arrays.stream(allowedOriginsStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, RequestRateTrackingFilter requestRateTrackingFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> {
                    headers.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'; sandbox"));
                    headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::deny);
                    headers.contentTypeOptions(Customizer.withDefaults());
                    headers.httpStrictTransportSecurity(hsts -> hsts
                            .includeSubDomains(true)
                            .maxAgeInSeconds(31536000)
                    );
                    headers.referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER));
                    headers.permissionsPolicy(permissions -> permissions.policy("accelerometer=(), camera=(), geolocation=(), gyroscope=(), magnetometer=(), microphone=(), payment=(), usb=()"));
                    headers.cacheControl(Customizer.withDefaults());
                })
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            String correlationId = request.getHeader("X-Correlation-ID");
                            if (correlationId == null || correlationId.isBlank()) {
                                correlationId = UUID.randomUUID().toString();
                            }
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setHeader("X-Correlation-ID", correlationId);
                            ErrorEnvelope env = ErrorEnvelope.of(
                                    HttpStatus.UNAUTHORIZED.value(),
                                    "AUTHENTICATION_REQUIRED",
                                    "Authentication credentials are missing or invalid.",
                                    "የማረጋገጫ መረጃ አልቀረበም ወይም ትክክል አይደለም።",
                                    correlationId,
                                    request.getRequestURI()
                            );
                            response.getWriter().write(objectMapper.writeValueAsString(env));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            String correlationId = request.getHeader("X-Correlation-ID");
                            if (correlationId == null || correlationId.isBlank()) {
                                correlationId = UUID.randomUUID().toString();
                            }
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setHeader("X-Correlation-ID", correlationId);
                            ErrorEnvelope env = ErrorEnvelope.of(
                                    HttpStatus.FORBIDDEN.value(),
                                    "AUTHORIZATION_DENIED",
                                    "Authenticated principal lacks the required permissions or scopes for this operation.",
                                    "ይህን ተግባር ለማከናወን የሚያስፈልገው ፈቃድ (Scope) የለዎትም።",
                                    correlationId,
                                    request.getRequestURI()
                            );
                            response.getWriter().write(objectMapper.writeValueAsString(env));
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/actuator/health/**",
                                "/actuator/prometheus",
                                "/api/*/public/**",
                                "/v/**",
                                "/api/*/auth/**",
                                "/api/*/saas/auth/**",
                                "/api/*/master/auth/**"
                        ).permitAll()
                        .requestMatchers("/api/*/authority/**").hasAnyAuthority("ROLE_AUTHORITY_AUDITOR", "ROLE_PLATFORM_ADMIN")
                        .requestMatchers(
                                "/api/*/admin/environment/**",
                                "/api/*/master/environment/**",
                                "/api/*/master/account/**",
                                "/api/*/master/users/**",
                                "/api/*/master/rbac/**",
                                "/api/*/master/access-reviews/**"
                        ).hasAuthority("ROLE_PLATFORM_ADMIN")
                        .requestMatchers("/api/*/master/provider-tiers/**").hasAnyAuthority("ROLE_PLATFORM_ADMIN", "ROLE_SAAS_ADMIN", "ROLE_AUTHORITY_AUDITOR")
                        .requestMatchers("/api/*/saas/**", "/api/*/master/**", "/api/*/admin/**").hasAnyAuthority("ROLE_SAAS_ADMIN", "ROLE_PLATFORM_ADMIN", "ROLE_SAAS_OPERATOR", "ROLE_DELEGATED_OPERATOR")
                        .requestMatchers("/actuator/**").hasRole("PLATFORM_ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(tenantAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(requestRateTrackingFilter, TenantAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public RequestRateTrackingFilter requestRateTrackingFilter(
            RequestRateTrackingService requestRateTrackingService,
            @Value("${platform.security.rate-tracking.authenticated-requests-per-second:20}") int authenticatedRequestsPerSecond,
            @Value("${platform.security.rate-tracking.anonymous-requests-per-second:10}") int anonymousRequestsPerSecond
    ) {
        return new RequestRateTrackingFilter(
                requestRateTrackingService,
                objectMapper,
                authenticatedRequestsPerSecond,
                anonymousRequestsPerSecond
        );
    }

    @Bean
    public RequestSignatureVerifier requestSignatureVerifier(
            @Value("${platform.security.request-signing.shared-secret:}") String sharedSecret,
            @Value("${platform.security.request-signing.max-clock-skew-seconds:300}") long maxClockSkewSeconds
    ) {
        return new RequestSignatureVerifier(sharedSecret, maxClockSkewSeconds);
    }

    @Bean
    public FilterRegistrationBean<ApiVersionNegotiationFilter> apiVersionNegotiationFilterRegistration(
            et.ut.einvoice.platform.api.ApiVersionProperties apiVersionProperties
    ) {
        FilterRegistrationBean<ApiVersionNegotiationFilter> registration = new FilterRegistrationBean<>(
                new ApiVersionNegotiationFilter(apiVersionProperties.getSupportedVersions(), objectMapper)
        );
        registration.setName("apiVersionNegotiationFilter");
        registration.addUrlPatterns("/api", "/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RequestSigningFilter> requestSigningFilterRegistration(
            RequestSignatureVerifier requestSignatureVerifier,
            @Value("${platform.security.request-signing.enabled:true}") boolean enabled,
            @Value("${platform.security.request-signing.max-body-bytes:10485760}") long maxBodyBytes
    ) {
        RequestSigningFilter requestSigningFilter = new RequestSigningFilter(
                requestSignatureVerifier, objectMapper, enabled, maxBodyBytes
        );
        FilterRegistrationBean<RequestSigningFilter> registration = new FilterRegistrationBean<>(requestSigningFilter);
        registration.setName("requestSigningFilter");
        registration.addUrlPatterns("/api/*", "/v/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    @Bean
    public org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-API-Key", "X-Client-Secret", "X-Tenant-ID", "X-Authority-Token", "Idempotency-Key", "X-Correlation-ID", "X-Privileged-Token", ApiVersionNegotiationFilter.VERSION_HEADER, RequestSignatureVerifier.SIGNATURE_HEADER, RequestSignatureVerifier.TIMESTAMP_HEADER));
        configuration.setExposedHeaders(List.of(
                "X-Correlation-ID", "X-Reprint-Count", "X-Privileged-Token",
                "X-RateLimit-Limit", "X-RateLimit-Remaining", "Retry-After",
                ApiVersionNegotiationFilter.VERSION_HEADER,
                ApiVersionNegotiationFilter.SUPPORTED_VERSIONS_HEADER
        ));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
