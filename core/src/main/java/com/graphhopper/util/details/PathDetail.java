package com.graphhopper.util.details;

/**
 * A detail of a Path
 *
 * @author Robin Boldt
 */
public class PathDetail {
    public Object value;
    public int numberOfPoints;

    public PathDetail() {
    }

    public PathDetail(Object value) {
        this.value = value;
    }
}
