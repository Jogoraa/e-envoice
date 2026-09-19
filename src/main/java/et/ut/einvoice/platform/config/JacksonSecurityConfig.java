package et.ut.einvoice.platform.config;

import com.fasterxml.jackson.core.StreamReadConstraints;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures resource defense limits on Jackson parser to prevent deeply nested
 * payload recursion attacks and oversized numeric/string DoS.
 */
@Configuration
public class JacksonSecurityConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonSecurityCustomizer() {
        return builder -> {
            StreamReadConstraints constraints = StreamReadConstraints.builder()
                    .maxNestingDepth(50)
                    .maxStringLength(20_000_000)
                    .maxNumberLength(1000)
                    .build();
            builder.postConfigurer(objectMapper ->
                    objectMapper.getFactory().setStreamReadConstraints(constraints)
            );
        };
    }
}
