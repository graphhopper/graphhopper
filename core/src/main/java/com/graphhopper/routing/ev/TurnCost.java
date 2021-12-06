package com.graphhopper.routing.ev;

import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.Helper;

import static com.graphhopper.routing.util.EncodingManager.getKey;

public class TurnCost {

    public static String key(String prefix) {
        return getKey(prefix, "turn_cost");
    }

    /**
     * This creates an EncodedValue specifically for the turn costs
     */
    public static DecimalEncodedValue create(String name, int maxTurnCosts) {
        int turnBits = Helper.countBitValue(maxTurnCosts);
        return new DecimalEncodedValueImpl(key(name), turnBits, 0, 1, false, false, false, true);
    }

    public static IntsRef createFlags() {
        return new IntsRef(1);
    }
}
