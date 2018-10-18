package com.graphhopper.routing.profiles;

import com.graphhopper.routing.util.EncodingManager;

public enum Toll {
    DEFAULT("no"), ALL("all"), HGV("hgv");

    String name;

    Toll(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    public static EnumEncodedValue<Toll> create() {
        return new EnumEncodedValueImpl<>(EncodingManager.TOLL, values(), DEFAULT);
    }
}
