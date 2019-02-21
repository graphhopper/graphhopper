package com.graphhopper.routing.profiles;

public class DefaultIndexBased implements IndexBased {
    private final int ordinal;
    private final String name;

    public DefaultIndexBased(String name, int ordinal) {
        this.ordinal = ordinal;
        this.name = name;
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
        // strict equality is important to keep type safety
        if (!(obj.getClass() == getClass()))
            return false;
        return ((IndexBased) obj).ordinal() == ordinal;
    }

    @Override
    public String toString() {
        return name;
    }
}
