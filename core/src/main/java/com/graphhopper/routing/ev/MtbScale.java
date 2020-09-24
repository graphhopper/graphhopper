package com.graphhopper.routing.ev;

public enum  MtbScale {
    NO("no"),
    S0("0"),
    S1("1"),
    S2("2"),
    S3("3"),
    S4("4"),
    S5("5"),
    S6("6");

    private final String name;

    MtbScale(String name) {
        this.name = name;
    }

    public static final String KEY = "mtb_scale";

    public static MtbScale find(String name) {
        if (name == null)
            return NO;
        try {
            return MtbScale.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return NO;
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
