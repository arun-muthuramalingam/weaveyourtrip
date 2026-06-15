package com.weaveyourtrip.model;

/**
 * Supported passport nationalities. The visa-aware step does real work for
 * hard-passport users (IN at MVP launch) and collapses to "✓ no visa needed"
 * for soft-passport users (US, GB, EU). Pakistan, Bangladesh, Nigeria, Egypt,
 * and Philippines land in v1.1.
 */
public enum Passport {
    IN("🇮🇳", "India"),
    US("🇺🇸", "United States"),
    GB("🇬🇧", "United Kingdom"),
    EU("🇪🇺", "EU passport"),
    OTHER("🌐", "Other");

    private final String flag;
    private final String displayName;

    Passport(String flag, String displayName) {
        this.flag = flag;
        this.displayName = displayName;
    }

    public String getFlag() {
        return flag;
    }

    public String getDisplayName() {
        return displayName;
    }
}
