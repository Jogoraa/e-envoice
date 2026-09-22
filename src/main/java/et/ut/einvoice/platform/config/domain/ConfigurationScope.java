package et.ut.einvoice.platform.config.domain;

public enum ConfigurationScope {
    APPLICATION("Application Core"),
    DATABASE("Database Cluster"),
    REDIS("Redis & Caching"),
    SECURITY("Security Policies"),
    AUTHENTICATION("JWT & Identity"),
    MOEIRS("Ministry of Revenues / EIRS"),
    SMS("SMS Gateway"),
    EMAIL("Email Delivery"),
    STORAGE("Storage & Uploads"),
    OBSERVABILITY("Observability & Metrics"),
    RATE_LIMITING("Rate Limiting & Throttling");

    private final String displayName;

    ConfigurationScope(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
