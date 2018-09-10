package com.graphhopper.util.details;

/**
 * A detail information of a Path
 *
 * @author Robin Boldt
 */
public class PathDetail {
    private final Object value;
    private int first;
    private int last;

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

    public Object getValue() {
        return value;
    }

    public void setFirst(int first) {
        this.first = first;
    }

    public int getFirst() {
        return first;
    }

    public void setLast(int last) {
        this.last = last;
    }

    public int getLast() {
        if (last < first)
            throw new IllegalStateException("last cannot be smaller than first");
        return last;
    }

    public int getLength() {
        return last - first;
    }

    @Override
    public String toString() {
        return value + " [" + getFirst() + ", " + getLast() + "]";
    }
}
