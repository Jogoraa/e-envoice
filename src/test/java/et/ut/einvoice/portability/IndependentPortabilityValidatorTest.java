package et.ut.einvoice.portability;

import com.fasterxml.jackson.databind.ObjectMapper;
import et.ut.einvoice.portability.validator.PortabilityArchiveValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class IndependentPortabilityValidatorTest {

    private final PortabilityArchiveValidator validator = new PortabilityArchiveValidator();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Independent Portability: Valid export ZIP archive passes full integrity validation")
    void test_ValidArchive_PassesIntegrityVerification(@TempDir Path tempDir) throws Exception {
        File zipFile = tempDir.resolve("valid_export.zip").toFile();

        Map<String, String> files = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();

        byte[] invoiceBytes = "[{\"documentNumber\":\"INV-001\",\"grandTotal\":500.00}]".getBytes(StandardCharsets.UTF_8);
        byte[] customerBytes = "[{\"tin\":\"0099887766\",\"legalName\":\"Customer A\"}]".getBytes(StandardCharsets.UTF_8);

        files.put("invoices/invoices.json", sha256Hex(invoiceBytes));
        files.put("customers/customers.json", sha256Hex(customerBytes));
        counts.put("invoices", 1);
        counts.put("customers", 1);

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("tenantId", UUID.randomUUID().toString());
        manifest.put("sellerTin", "0011223344");
        manifest.put("directive", "FDRE MoR Directive No. 1142/2026 Art. 5(3)");
        manifest.put("recordCounts", counts);
        manifest.put("files", files);

        byte[] manifestBytes = objectMapper.writeValueAsBytes(manifest);

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write(manifestBytes);
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("invoices/invoices.json"));
            zos.write(invoiceBytes);
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("customers/customers.json"));
            zos.write(customerBytes);
            zos.closeEntry();
        }

        var result = validator.validateArchive(zipFile);
        assertTrue(result.isValid(), "Archive must be reported as valid");
        assertEquals("VALID", result.status());
        assertEquals("0011223344", result.sellerTin());
        assertEquals(1, result.verifiedRecordCounts().get("invoices"));
        assertEquals(1, result.verifiedRecordCounts().get("customers"));
        assertTrue(result.errors().isEmpty());
    }

    @Test
    @DisplayName("Independent Portability: Tampered content inside ZIP triggers checksum mismatch error")
    void test_TamperedArchive_FailsVerification(@TempDir Path tempDir) throws Exception {
        File zipFile = tempDir.resolve("tampered_export.zip").toFile();

        Map<String, String> files = new LinkedHashMap<>();
        byte[] invoiceBytes = "[{\"documentNumber\":\"INV-001\",\"grandTotal\":500.00}]".getBytes(StandardCharsets.UTF_8);
        files.put("invoices/invoices.json", sha256Hex(invoiceBytes));

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("tenantId", UUID.randomUUID().toString());
        manifest.put("sellerTin", "0011223344");
        manifest.put("files", files);

        byte[] manifestBytes = objectMapper.writeValueAsBytes(manifest);
        byte[] tamperedBytes = "[{\"documentNumber\":\"INV-001\",\"grandTotal\":99999.00}]".getBytes(StandardCharsets.UTF_8);

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write(manifestBytes);
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("invoices/invoices.json"));
            zos.write(tamperedBytes); // Tampered content!
            zos.closeEntry();
        }

        var result = validator.validateArchive(zipFile);
        assertFalse(result.isValid(), "Tampered archive must fail validation");
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Checksum mismatch")));
    }

    @Test
    @DisplayName("Independent Portability: Archive with path traversal entry is immediately rejected")
    void test_PathTraversal_Rejected(@TempDir Path tempDir) throws Exception {
        File zipFile = tempDir.resolve("traversal_export.zip").toFile();

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("tenantId", UUID.randomUUID().toString());
        manifest.put("sellerTin", "0011223344");
        manifest.put("files", Map.of());

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write(objectMapper.writeValueAsBytes(manifest));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("../../../etc/shadow"));
            zos.write("root:x:0:0".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        var result = validator.validateArchive(zipFile);
        assertFalse(result.isValid());
        assertEquals("PATH_TRAVERSAL_DETECTED", result.status());
    }

    private String sha256Hex(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
