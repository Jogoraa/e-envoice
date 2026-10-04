package et.ut.einvoice.platform.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    /** A stable OpenAPI document for the frozen v1 wire contract. */
    @Bean
    public GroupedOpenApi v1OpenApi() {
        return GroupedOpenApi.builder()
                .group("v1")
                .pathsToMatch("/api/v1/**")
                .build();
    }

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("UT Electronic Invoicing & Sales Registration SaaS Platform API")
                        .version("1.0.0-RELEASE")
                        .description("Official public REST API for Directive No. 1142/2026 compliant electronic invoicing in Ethiopia.")
                        .contact(new Contact().name("UT Systems Technical Team").email("contact@utsolutionsplc.com").url("https://utsystems.et"))
                        .license(new License().name("Proprietary - UT Systems PLC").url("https://utsystems.et/terms")))
                .components(new Components()
                        .addSecuritySchemes("BearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"))
                        .addSecuritySchemes("ApiKeyAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-API-KEY"))
                        .addSecuritySchemes("RequestSignature", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Request-Signature")
                                .description("Required for POST, PUT, PATCH, and DELETE. Base64url HMAC-SHA-256 over the canonical request."))
                        .addSecuritySchemes("RequestTimestamp", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Request-Timestamp")
                                .description("Required Unix epoch seconds for signed mutating requests.")));
    }
}
