package com.graphhopper.routing.ev;

public class Orientation {
    public static final String KEY = "orientation";

    public static void main(String[] args) {
        System.out.println(Math.toDegrees(create().getMinStorableDecimal()) + " " + Math.toDegrees(create().getMaxStorableDecimal()));
    }

    // Due to pillar nodes we need 4 values: x and 360-x at base node and y and 360-y at adjacent node but store in radians.
    public static DecimalEncodedValue create() {
        // rad / pi == deg / 180
        return new DecimalEncodedValueImpl(KEY, 5, - Math.PI, 2 * Math.PI / 30,
                false, true, false);
    }
}
