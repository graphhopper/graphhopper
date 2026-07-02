package com.graphhopper.routing.ev

import com.graphhopper.routing.util.EncodingManager.Companion.getKey
import com.graphhopper.util.BitUtil

object TurnCost {

    @JvmStatic
    fun key(prefix: String): String = getKey(prefix, "turn_cost")

    /**
     * This creates an EncodedValue specifically for the turn costs
     */
    @JvmStatic
    fun create(name: String, maxTurnCosts: Int): DecimalEncodedValue {
        val turnBits = BitUtil.countBitValue(maxTurnCosts)
        return DecimalEncodedValueImpl(key(name), turnBits, 0.0, 1.0, false, false, true)
    }
}
