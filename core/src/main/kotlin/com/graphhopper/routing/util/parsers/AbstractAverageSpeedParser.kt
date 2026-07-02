package com.graphhopper.routing.util.parsers

import com.graphhopper.reader.ReaderWay
import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.routing.ev.EdgeIntAccess
import com.graphhopper.storage.IntsRef

abstract class AbstractAverageSpeedParser protected constructor(
    // http://wiki.openstreetmap.org/wiki/Mapfeatures#Barrier
    @JvmField protected val avgSpeedEnc: DecimalEncodedValue
) : TagParser {

    fun getAverageSpeedEnc(): DecimalEncodedValue = avgSpeedEnc

    protected fun setSpeed(reverse: Boolean, edgeId: Int, edgeIntAccess: EdgeIntAccess, speed: Double) {
        if (speed < avgSpeedEnc.smallestNonZeroValue / 2) {
            throw IllegalArgumentException("Speed was $speed but cannot be lower than ${avgSpeedEnc.smallestNonZeroValue / 2}")
        } else {
            avgSpeedEnc.setDecimal(reverse, edgeId, edgeIntAccess, speed)
        }
    }

    fun getName(): String = avgSpeedEnc.name

    override fun handleWayTags(edgeId: Int, edgeIntAccess: EdgeIntAccess, way: ReaderWay, relationFlags: IntsRef?) {
        handleWayTags(edgeId, edgeIntAccess, way)
    }

    abstract fun handleWayTags(edgeId: Int, edgeIntAccess: EdgeIntAccess, way: ReaderWay)

    override fun toString(): String = getName()
}
