package et.ut.einvoice.platform.config;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
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
                    {
                        // Request schemas deliberately ignore fields they do not declare. This
                        // makes mass-assignment attempts inert while allowing clients to roll
                        // out independently. Declared fields remain strictly typed below.
                        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

                        // Do not silently turn e.g. "12" into a number, 1 into a boolean,
                        // or 1.5 into an integer. A malformed request must fail before it
                        // reaches a controller or service.
                        objectMapper.disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
                        objectMapper.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
                        objectMapper.enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
                        objectMapper.enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS);
                        objectMapper.getFactory().setStreamReadConstraints(constraints);
                    }
            );
        };
    }
}
