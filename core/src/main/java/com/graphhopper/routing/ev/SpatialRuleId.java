package com.graphhopper.routing.ev;

public class SpatialRuleId {
    public static final String KEY = "spatial_rule";

    public static IntEncodedValue create(int ruleCount) {
        int bits = 32 - Integer.numberOfLeadingZeros(ruleCount + 1);
        return new UnsignedIntEncodedValue(SpatialRuleId.KEY, bits, false);
    }
}
