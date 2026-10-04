package et.ut.einvoice.platform.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.platform.config.JacksonSecurityConfig;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Valid;
import jakarta.validation.Constraint;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.core.MethodParameter;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StrictRequestSchemaConfigurationTest {

    @Test
    void unknownFieldsAreDroppedAndScalarCoercionIsRejected() throws Exception {
        ObjectMapper objectMapper = strictObjectMapper();

        SampleRequest request = objectMapper.readValue("{\"quantity\":3,\"active\":true,\"unexpected\":\"ignored\"}", SampleRequest.class);

        assertEquals(3, request.quantity());
        assertThrows(JsonProcessingException.class,
                () -> objectMapper.readValue("{\"quantity\":\"3\",\"active\":true}", SampleRequest.class));
        assertThrows(JsonProcessingException.class,
                () -> objectMapper.readValue("{\"quantity\":3,\"active\":\"true\"}", SampleRequest.class));
    }

    @Test
    void globalAdviceRejectsInvalidSchemasAndUntypedBodiesBeforeControllerInvocation() throws Exception {
        StrictRequestBodyValidationAdvice advice = new StrictRequestBodyValidationAdvice(
                Validation.buildDefaultValidatorFactory().getValidator(),
                strictObjectMapper(),
                new InputSanitizer()
        );
        Method method = Fixture.class.getDeclaredMethod("endpoint", SampleRequest.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        MockHttpInputMessage input = new MockHttpInputMessage("{}".getBytes(StandardCharsets.UTF_8));

        assertThrows(ConstraintViolationException.class, () -> advice.afterBodyRead(
                new SampleRequest(null, null), input, parameter, SampleRequest.class, MappingJackson2HttpMessageConverter.class
        ));
        assertThrows(IllegalArgumentException.class, () -> advice.afterBodyRead(
                Map.of("quantity", 3), input, parameter, Map.class, MappingJackson2HttpMessageConverter.class
        ));
    }

    @Test
    void everyControllerRequestBodyUsesAnExplicitConstrainedSchema() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Set<String> violations = new TreeSet<>();

        for (BeanDefinition definition : scanner.findCandidateComponents("et.ut.einvoice")) {
            Class<?> controller = Class.forName(definition.getBeanClassName());
            for (Method method : controller.getDeclaredMethods()) {
                for (Parameter parameter : method.getParameters()) {
                    if (!parameter.isAnnotationPresent(RequestBody.class)) {
                        continue;
                    }
                    Class<?> schemaType = parameter.getType();
                    String endpoint = controller.getSimpleName() + "#" + method.getName();
                    if (!parameter.isAnnotationPresent(Valid.class)) {
                        violations.add(endpoint + " is missing @Valid");
                    }
                    if (Map.class.isAssignableFrom(schemaType) || Object.class.equals(schemaType)
                            || Iterable.class.isAssignableFrom(schemaType)
                            || schemaType.getPackageName().contains(".domain")) {
                        violations.add(endpoint + " uses a non-schema request body: " + schemaType.getName());
                    } else if (!hasDirectConstraint(schemaType)) {
                        violations.add(endpoint + " request schema has no validation constraints: " + schemaType.getName());
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(), () -> "Request schema contract violations: " + violations);
    }

    private boolean hasDirectConstraint(Class<?> schemaType) {
        return Arrays.stream(schemaType.getDeclaredFields())
                .flatMap(field -> Arrays.stream(field.getAnnotations()))
                .map(Annotation::annotationType)
                .anyMatch(annotationType -> annotationType.isAnnotationPresent(Constraint.class));
    }

    private ObjectMapper strictObjectMapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonSecurityConfig().jacksonSecurityCustomizer().customize(builder);
        return builder.build();
    }

    private record SampleRequest(@NotNull Integer quantity, @NotNull Boolean active) {}

    @SuppressWarnings("unused")
    private static class Fixture {
        void endpoint(@RequestBody SampleRequest request) {}
    }
}
