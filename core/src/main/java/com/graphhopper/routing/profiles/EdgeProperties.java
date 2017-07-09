package com.graphhopper.routing.profiles;

public class EdgeProperties {
    private int[] data;

    public EdgeProperties(int bytes) {
        data = new int[bytes / 4];
    }

    public int[] getData() {
        return data;
    }
}
