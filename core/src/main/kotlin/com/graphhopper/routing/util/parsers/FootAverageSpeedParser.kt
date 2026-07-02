package com.graphhopper.routing.util.parsers

import com.graphhopper.reader.ReaderWay
import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.routing.ev.EdgeIntAccess
import com.graphhopper.routing.ev.EncodedValueLookup
import com.graphhopper.routing.ev.RouteNetwork
import com.graphhopper.routing.ev.RouteNetwork.INTERNATIONAL
import com.graphhopper.routing.ev.RouteNetwork.LOCAL
import com.graphhopper.routing.ev.RouteNetwork.NATIONAL
import com.graphhopper.routing.ev.RouteNetwork.REGIONAL
import com.graphhopper.routing.ev.VehicleSpeed
import com.graphhopper.routing.util.FerrySpeedCalculator
import com.graphhopper.routing.util.PriorityCode.UNCHANGED

open class FootAverageSpeedParser(speedEnc: DecimalEncodedValue) : AbstractAverageSpeedParser(speedEnc), TagParser {

    @JvmField
    protected var routeMap: MutableMap<RouteNetwork, Int> = HashMap()

    constructor(lookup: EncodedValueLookup) : this(lookup.getDecimalEncodedValue(VehicleSpeed.key("foot")))

    init {
        routeMap[INTERNATIONAL] = UNCHANGED.value
        routeMap[NATIONAL] = UNCHANGED.value
        routeMap[REGIONAL] = UNCHANGED.value
        routeMap[LOCAL] = UNCHANGED.value
    }

    override fun handleWayTags(edgeId: Int, edgeIntAccess: EdgeIntAccess, way: ReaderWay) {
        val highwayValue = way.getTag("highway")
        if (highwayValue == null) {
            if (FerrySpeedCalculator.isFerry(way) || !way.hasTag("railway", "platform") && !way.hasTag("man_made", "pier"))
                return
        }

        val sacScale = way.getTag("sac_scale")
        if (sacScale != null) {
            setSpeed(false, edgeId, edgeIntAccess, (if ("hiking" == sacScale) MEAN_SPEED else SLOW_SPEED).toDouble())
            if (avgSpeedEnc.isStoreTwoDirections)
                setSpeed(true, edgeId, edgeIntAccess, (if ("hiking" == sacScale) MEAN_SPEED else SLOW_SPEED).toDouble())
        } else {
            setSpeed(false, edgeId, edgeIntAccess, (if (way.hasTag("highway", "steps")) MEAN_SPEED - 2 else MEAN_SPEED).toDouble())
            if (avgSpeedEnc.isStoreTwoDirections)
                setSpeed(true, edgeId, edgeIntAccess, (if (way.hasTag("highway", "steps")) MEAN_SPEED - 2 else MEAN_SPEED).toDouble())
        }
    }

    companion object {
        internal const val SLOW_SPEED = 2
        internal const val MEAN_SPEED = 5
    }
}
