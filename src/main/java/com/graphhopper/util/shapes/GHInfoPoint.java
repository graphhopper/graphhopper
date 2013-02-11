package com.graphhopper.util.shapes;

/**
 * @author Peter Karich
 */
public class GHInfoPoint extends GHPoint {

    private String name;

    public GHInfoPoint(double lat, double lon) {
        super(lat, lon);
    }

    public GHInfoPoint name(String name) {
        this.name = name;
        return this;
    }

    public String name() {
        return name;
    }

    @Override public String toString() {
        return name + " " + super.toString();
    }
}
