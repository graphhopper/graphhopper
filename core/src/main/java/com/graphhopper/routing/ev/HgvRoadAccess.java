package com.graphhopper.routing.ev;

/**
 * Like {@link RoadAccess} but for heavy goods vehicles, i.e. the hgv tag is preferred over the
 * more generic ones and instead of 'motorcar' access the 'hgv' is used.
 * The values are the same, so the RoadAccess enum is reused unlike e.g. FootRoadAccess.
 */
public class HgvRoadAccess {

    public static final String KEY = "hgv_road_access";

    public static EnumEncodedValue<RoadAccess> create() {
        return new EnumEncodedValue<>(KEY, RoadAccess.class);
    }
}
