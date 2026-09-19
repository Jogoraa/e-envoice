package et.ut.einvoice.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.platform.exception.ErrorEnvelope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
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
                                "/api/v1/public/**"
                        ).permitAll()
                        .requestMatchers("/api/v1/authority/**").hasAuthority("ROLE_AUTHORITY_AUDITOR")
                        .requestMatchers("/actuator/**").hasRole("PLATFORM_ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(tenantAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-API-Key", "X-Client-Secret", "X-Tenant-ID", "X-Authority-Token", "Idempotency-Key", "X-Correlation-ID"));
        configuration.setExposedHeaders(List.of("X-Correlation-ID", "X-Reprint-Count"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
