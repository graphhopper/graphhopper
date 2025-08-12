// ORS-GH MOD: additional class
package com.graphhopper.routing.ev;

public enum OrsSurface {
    // Keep in sync with ORS documentation: surface.md
    UNKNOWN,
    PAVED,
    UNPAVED,
    ASPHALT,
    CONCRETE,
    METAL,
    WOOD,
    COMPACTED_GRAVEL,
    GRAVEL,
    DIRT,
    GROUND,
    ICE,
    PAVING_STONES,
    SAND,
    GRASS,
    GRASS_PAVER;

    public static final String KEY = "ors_surface";
}
