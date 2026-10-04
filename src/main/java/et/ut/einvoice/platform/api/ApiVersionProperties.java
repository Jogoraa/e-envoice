package et.ut.einvoice.platform.api;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Deployment-owned API-version catalog. Version activation remains an infrastructure change;
 * it is deliberately not mutable through the administrative HTTP API.
 */
@Component
@ConfigurationProperties(prefix = "platform.api")
public class ApiVersionProperties {

    private String currentVersion = "v1";
    private String supportedVersions = "v1";
    private Map<String, VersionMetadata> catalog = new LinkedHashMap<>();

    public String getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(String currentVersion) {
        this.currentVersion = currentVersion;
    }

    public String getSupportedVersions() {
        return supportedVersions;
    }

    public void setSupportedVersions(String supportedVersions) {
        this.supportedVersions = supportedVersions;
    }

    public Map<String, VersionMetadata> getCatalog() {
        return catalog;
    }

    public void setCatalog(Map<String, VersionMetadata> catalog) {
        this.catalog = catalog == null ? new LinkedHashMap<>() : new LinkedHashMap<>(catalog);
    }

    public static class VersionMetadata {
        private String lifecycle;
        private String contractVersion;
        private String releasedAt;
        private boolean frozen;
        private String changePolicy;
        private String deprecationDate;
        private String sunsetDate;
        private String notes;

        public String getLifecycle() {
            return lifecycle;
        }

        public void setLifecycle(String lifecycle) {
            this.lifecycle = lifecycle;
        }

        public String getContractVersion() {
            return contractVersion;
        }

        public void setContractVersion(String contractVersion) {
            this.contractVersion = contractVersion;
        }

        public String getReleasedAt() {
            return releasedAt;
        }

        public void setReleasedAt(String releasedAt) {
            this.releasedAt = releasedAt;
        }

        public boolean isFrozen() {
            return frozen;
        }

        public void setFrozen(boolean frozen) {
            this.frozen = frozen;
        }

        public String getChangePolicy() {
            return changePolicy;
        }

        public void setChangePolicy(String changePolicy) {
            this.changePolicy = changePolicy;
        }

        public String getDeprecationDate() {
            return deprecationDate;
        }

        public void setDeprecationDate(String deprecationDate) {
            this.deprecationDate = deprecationDate;
        }

        public String getSunsetDate() {
            return sunsetDate;
        }

        public void setSunsetDate(String sunsetDate) {
            this.sunsetDate = sunsetDate;
        }

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }
    }
}
