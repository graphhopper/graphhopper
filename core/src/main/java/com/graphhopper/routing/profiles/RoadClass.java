package com.graphhopper.routing.profiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RoadClass implements IndexBased {
    private static final List<RoadClass> values = create("_default",
            "motorway", "motorway_link", "motorroad",
            "trunk", "trunk_link",
            "primary", "primary_link",
            "secondary", "secondary_link",
            "tertiary", "tertiary_link",
            "residential", "unclassified",
            "service", "road", "track", "forestry", "steps", "cycleway", "path", "living_street");

    private final String name;
    private final int ordinal;

    private RoadClass(String name, int ordinal) {
        this.name = name;
        this.ordinal = ordinal;
    }


    @Override
    public String toString() {
        return name;
    }

    @Override
    public int ordinal() {
        return ordinal;
    }

    @Override
    public int hashCode() {
        return ordinal;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof RoadClass))
            return false;
        return ((RoadClass) obj).ordinal == ordinal;
    }

    public static List<RoadClass> create(String... values) {
        List<RoadClass> list = new ArrayList<>(values.length);
        for (int i = 0; i < values.length; i++) {
            list.add(new RoadClass(values[i], i));
        }
        return Collections.unmodifiableList(list);
    }
}
