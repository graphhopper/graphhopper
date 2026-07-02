package com.graphhopper.routing.ev

/**
 * This interface defines access to an edge property of type boolean. The default value is false.
 */
interface BooleanEncodedValue : EncodedValue {
    fun setBool(reverse: Boolean, edgeId: Int, edgeIntAccess: EdgeIntAccess, value: Boolean)

    fun getBool(reverse: Boolean, edgeId: Int, edgeIntAccess: EdgeIntAccess): Boolean
}
