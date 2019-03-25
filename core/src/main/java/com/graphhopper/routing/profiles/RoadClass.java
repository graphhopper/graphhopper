package com.graphhopper.routing.profiles;

public enum RoadClass {
    OTHER("other"), MOTORWAY("motorway"), MOTORROAD("motorroad"),
    TRUNK("trunk"), PRIMARY("primary"), SECONDARY("secondary"),
    TERTIARY("tertiary"), RESIDENTIAL("residential"), UNCLASSIFIED("unclassified"),
    SERVICE("service"), ROAD("road"), TRACK("track"),
    FORESTRY("forestry"), STEPS("steps"), CYCLEWAY("cycleway"),
    PATH("path"), LIVING_STREET("living_street");

    private final String name;

    RoadClass(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
