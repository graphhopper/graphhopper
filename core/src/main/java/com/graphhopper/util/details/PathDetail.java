package com.graphhopper.util.details;

/**
 * A detail information of a Path
 *
 * @author Robin Boldt
 */
public class PathDetail {
    public final Object value;
    public int numberOfPoints;

    public PathDetail(Object value) {
        this.value = value;
    }
}
