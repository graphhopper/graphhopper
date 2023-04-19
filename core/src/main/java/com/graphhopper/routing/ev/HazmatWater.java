package com.graphhopper.routing.ev;

/**
 * Defines general restrictions for the transport of goods through water protection areas.<br>
 * If not tagged it will be {@link #YES}
 */
public enum HazmatWater {
    YES("yes"), PERMISSIVE("permissive"), NO("no");

    public static final String KEY = "hazmat_water";

    public static EnumEncodedValue<HazmatWater> create() {
        return new EnumEncodedValue<>(KEY, HazmatWater.class);
    }

    private final String name;

    HazmatWater(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
