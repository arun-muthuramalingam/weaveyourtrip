package com.weaveyourtrip.model;

/**
 * Trip style / traveller archetype. Selected in wizard step 4. Shapes the
 * mode-specific fields shown in step 5 and biases the AI prompt for restaurant
 * style, accommodation tier, pace, and cultural notes.
 *
 * MVP v1.0 ships BACKPACKER + COUPLE + FAMILY. GROUP + SENIOR land in v1.1.
 */
public enum TripMode {
    BACKPACKER("🎒", "Solo Explorer"),
    COUPLE("💑", "Couple Getaway"),
    FAMILY("👨‍👩‍👧‍👦", "Family Adventure"),
    GROUP("👥", "Group of Friends"),
    SENIOR("🌿", "Senior Explorer");

    private final String icon;
    private final String displayName;

    TripMode(String icon, String displayName) {
        this.icon = icon;
        this.displayName = displayName;
    }

    public String getIcon() {
        return icon;
    }

    public String getDisplayName() {
        return displayName;
    }
}
