package com.graphhopper.routing.profiles;

import com.graphhopper.routing.util.EncodingManager;

import java.util.Arrays;
import java.util.List;

public class Toll extends AbstractIndexBased {
    public final static Toll ALL = new Toll("all", 1), HGV = new Toll("hgv", 2);
    private static List<Toll> values = Arrays.asList(new Toll("no", 0), ALL, HGV);

    public Toll(String name, int ordinal) {
        super(name, ordinal);
    }

    public static ObjectEncodedValue create() {
        return new MappedObjectEncodedValue(EncodingManager.TOLL, values);
    }
}
