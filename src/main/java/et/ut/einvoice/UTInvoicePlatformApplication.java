package et.ut.einvoice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * UT Electronic Invoicing Platform
 * Compliant with Ethiopian Ministry of Revenues Directive No. 1142/2026.
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class UTInvoicePlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(UTInvoicePlatformApplication.class, args);
    }
}
