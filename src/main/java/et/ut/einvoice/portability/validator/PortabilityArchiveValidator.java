package et.ut.einvoice.portability.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Independent Portability Archive Importer and Integrity Validator.
 * Fulfills Directive No. 1142/2026 Art. 5(3) and Certification Rule 17.
 * Verifies tenant export archives without relying on running database or Spring context.
 */
public class PortabilityArchiveValidator {

    private final ObjectMapper objectMapper;

    public PortabilityArchiveValidator() {
        this.objectMapper = new ObjectMapper();
    }

    public record ArchiveValidationResult(
            boolean isValid,
            String status, // VALID, CORRUPT, CHECKSUM_MISMATCH, RECORD_COUNT_MISMATCH, MISSING_MANIFEST, PATH_TRAVERSAL_DETECTED
            String tenantId,
            String sellerTin,
            String directive,
            Map<String, Integer> verifiedRecordCounts,
            List<String> errors,
            List<String> warnings
    ) {}

    /**
     * Independently inspects and cryptographically validates a tenant export ZIP archive.
     */
    public ArchiveValidationResult validateArchive(File zipFile) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, Integer> verifiedCounts = new LinkedHashMap<>();

        if (zipFile == null || !zipFile.exists() || !zipFile.isFile()) {
            return new ArchiveValidationResult(
                    false, "MISSING_FILE", null, null, null,
                    verifiedCounts, List.of("Archive file does not exist or is not a readable file"), warnings
            );
        }

        try (ZipFile zf = new ZipFile(zipFile)) {
            // 1. Find and extract manifest.json
            ZipEntry manifestEntry = zf.getEntry("manifest.json");
            if (manifestEntry == null) {
                return new ArchiveValidationResult(
                        false, "MISSING_MANIFEST", null, null, null,
                        verifiedCounts, List.of("Archive missing mandatory manifest.json"), warnings
                );
            }

            byte[] manifestBytes = readEntryBytes(zf, manifestEntry);
            JsonNode manifestNode = objectMapper.readTree(manifestBytes);

            String tenantId = manifestNode.has("tenantId") ? manifestNode.get("tenantId").asText() : null;
            String sellerTin = manifestNode.has("sellerTin") ? manifestNode.get("sellerTin").asText() : null;
            String directive = manifestNode.has("directive") ? manifestNode.get("directive").asText() : null;

            JsonNode filesNode = manifestNode.get("files");
            if (filesNode == null || !filesNode.isObject()) {
                errors.add("Manifest missing 'files' checksum map");
            }

            JsonNode recordCountsNode = manifestNode.get("recordCounts");

            // 2. Validate path safety and entries
            Enumeration<? extends ZipEntry> entries = zf.entries();
            Map<String, byte[]> extractedEntries = new HashMap<>();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                // Path traversal check
                if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                    return new ArchiveValidationResult(
                            false, "PATH_TRAVERSAL_DETECTED", tenantId, sellerTin, directive,
                            verifiedCounts, List.of("Illegal path traversal sequence detected: " + name), warnings
                    );
                }

                if (!entry.isDirectory()) {
                    extractedEntries.put(name, readEntryBytes(zf, entry));
                }
            }

            // 3. Cryptographic Checksum Verification
            if (filesNode != null) {
                Iterator<Map.Entry<String, JsonNode>> fields = filesNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    String expectedPath = field.getKey();
                    String expectedChecksum = field.getValue().asText();

                    byte[] entryBytes = extractedEntries.get(expectedPath);
                    if (entryBytes == null) {
                        errors.add("Manifest specifies file '" + expectedPath + "' but entry not found in ZIP archive");
                        continue;
                    }

                    String computedChecksum = calculateSha256(entryBytes);
                    if (!computedChecksum.equalsIgnoreCase(expectedChecksum)) {
                        errors.add("Checksum mismatch for '" + expectedPath + "': expected " + expectedChecksum + " but computed " + computedChecksum);
                    }
                }
            }

            // 4. Record Count Verification for JSON collections
            if (recordCountsNode != null) {
                checkCount(zf, extractedEntries, "invoices/invoices.json", "invoices", recordCountsNode, verifiedCounts, errors);
                checkCount(zf, extractedEntries, "customers/customers.json", "customers", recordCountsNode, verifiedCounts, errors);
                checkCount(zf, extractedEntries, "catalog/products.json", "products", recordCountsNode, verifiedCounts, errors);
                checkCount(zf, extractedEntries, "catalog/services.json", "services", recordCountsNode, verifiedCounts, errors);
                checkCount(zf, extractedEntries, "catalog/categories.json", "categories", recordCountsNode, verifiedCounts, errors);
                checkCount(zf, extractedEntries, "adjustments/adjustments.json", "adjustments", recordCountsNode, verifiedCounts, errors);
                checkCount(zf, extractedEntries, "cancellations/cancellations.json", "cancellations", recordCountsNode, verifiedCounts, errors);
                checkCount(zf, extractedEntries, "audit/audit_events.json", "auditEvents", recordCountsNode, verifiedCounts, errors);
            }

            boolean valid = errors.isEmpty();
            String status = valid ? "VALID" : "CHECKSUM_OR_COUNT_MISMATCH";

            return new ArchiveValidationResult(
                    valid, status, tenantId, sellerTin, directive,
                    verifiedCounts, errors, warnings
            );

        } catch (Exception ex) {
            return new ArchiveValidationResult(
                    false, "CORRUPT", null, null, null,
                    verifiedCounts, List.of("Failed to read or parse ZIP archive: " + ex.getMessage()), warnings
            );
        }
    }

    private void checkCount(
            ZipFile zf,
            Map<String, byte[]> extracted,
            String entryPath,
            String countKey,
            JsonNode manifestCounts,
            Map<String, Integer> verifiedCounts,
            List<String> errors
    ) {
        if (!extracted.containsKey(entryPath)) return;

        try {
            byte[] data = extracted.get(entryPath);
            JsonNode json = objectMapper.readTree(data);
            int count = json.isArray() ? json.size() : 0;
            verifiedCounts.put(countKey, count);

            if (manifestCounts.has(countKey)) {
                int expectedCount = manifestCounts.get(countKey).asInt();
                if (count != expectedCount) {
                    errors.add("Record count mismatch for '" + entryPath + "': manifest says " + expectedCount + " but file contains " + count);
                }
            }
        } catch (Exception e) {
            errors.add("Failed to parse JSON for '" + entryPath + "': " + e.getMessage());
        }
    }

    private byte[] readEntryBytes(ZipFile zf, ZipEntry entry) throws Exception {
        try (InputStream is = zf.getInputStream(entry);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = is.read(buf)) != -1) {
                baos.write(buf, 0, r);
            }
            return baos.toByteArray();
        }
    }

    private String calculateSha256(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
