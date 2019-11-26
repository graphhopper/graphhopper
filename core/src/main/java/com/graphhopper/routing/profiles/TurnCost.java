package com.graphhopper.routing.profiles;

import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.Helper;

import static com.graphhopper.routing.util.EncodingManager.getKey;

public class TurnCost {
    /**
     * You need to call EncodingManager.getKey(prefix, EV_SUFFIX) as this EncodedValue can be used for e.g. car and bike
     */
    public final static String EV_SUFFIX = "turn_cost";

    /**
     * This creates an EncodedValue specifically for the turn costs
     */
    public static DecimalEncodedValue create(String name, int maxTurnCosts) {
        int turnBits = Helper.countBitValue(maxTurnCosts);
        return new UnsignedDecimalEncodedValue(getKey(name, EV_SUFFIX), turnBits, 1, 0, false, true);
    }

    public static IntsRef createFlags() {
        return new IntsRef(1);
    }
}
