package et.ut.einvoice.platform.validation;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers request-path sanitization for every Spring MVC route. */
@Configuration
public class InputSanitizationWebMvcConfig implements WebMvcConfigurer {

    private final PathVariableSanitizationInterceptor pathVariableSanitizationInterceptor;

    public InputSanitizationWebMvcConfig(PathVariableSanitizationInterceptor pathVariableSanitizationInterceptor) {
        this.pathVariableSanitizationInterceptor = pathVariableSanitizationInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(pathVariableSanitizationInterceptor);
    }
}
