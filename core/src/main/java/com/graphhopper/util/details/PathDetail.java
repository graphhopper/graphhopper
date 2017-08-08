package com.graphhopper.util.details;

/**
 * A detail information of a Path
 *
 * @author Robin Boldt
 */
public class PathDetail {
    public final Object value;
    public int numberOfPoints;

    // unprotected constructor used only in AbstractPathDetailsBuilder
    PathDetail(Object value) {
        this.value = value;
    }

    public PathDetail(long value) {
        this.value = value;
    }

    public PathDetail(double value) {
        this.value = value;
    }

    public PathDetail(boolean value) {
        this.value = value;
    }

    public PathDetail(String value) {
        this.value = value;
    }
}
