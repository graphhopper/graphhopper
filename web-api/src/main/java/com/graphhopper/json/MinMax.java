package com.graphhopper.json;

public class MinMax {
    public double min;
    public double max;

    public MinMax(double min, double max) {
        this.min = min;
        this.max = max;
    }

    @Override
    public String toString() {
        return "min=" + min + ", max=" + max;
    }
}
