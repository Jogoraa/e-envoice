package et.ut.einvoice.platform.security;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;

import javax.sql.DataSource;

/**
 * Wraps the primary DataSource with TenantAwareDataSource to guarantee PostgreSQL RLS session variables
 * are automatically populated for every database interaction.
 */
@Configuration
public class TenantDataSourceBeanPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) throws BeansException {
        if (bean instanceof DataSource dataSource && !(bean instanceof TenantAwareDataSource)) {
            return new TenantAwareDataSource(dataSource);
        }
        return bean;
    }
}
