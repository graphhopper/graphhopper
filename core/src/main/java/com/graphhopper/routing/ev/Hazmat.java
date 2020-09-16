package com.graphhopper.routing.ev;

/**
 * Defines general restrictions for the transport of hazardous materials.<br>
 * If not tagged it will be {@link #YES}
 */
public enum Hazmat {
    YES("yes"), NO("no");

    public static final String KEY = "hazmat";

    private final String name;

    Hazmat(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
