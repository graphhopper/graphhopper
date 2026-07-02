package com.graphhopper.routing.util.parsers

import com.graphhopper.reader.ReaderWay
import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.routing.ev.EdgeIntAccess
import com.graphhopper.routing.ev.EncodedValueLookup
import com.graphhopper.routing.ev.MaxSpeed
import com.graphhopper.routing.ev.VehiclePriority
import com.graphhopper.routing.util.FerrySpeedCalculator
import com.graphhopper.routing.util.PriorityCode
import com.graphhopper.routing.util.PriorityCode.AVOID
import com.graphhopper.routing.util.PriorityCode.BAD
import com.graphhopper.routing.util.PriorityCode.PREFER
import com.graphhopper.routing.util.PriorityCode.REACH_DESTINATION
import com.graphhopper.routing.util.PriorityCode.SLIGHT_AVOID
import com.graphhopper.routing.util.PriorityCode.UNCHANGED
import com.graphhopper.routing.util.PriorityCode.VERY_BAD
import com.graphhopper.routing.util.parsers.AbstractAccessParser.Companion.INTENDED
import com.graphhopper.storage.IntsRef
import java.util.TreeMap
import kotlin.math.max

open class FootPriorityParser protected constructor(priorityEnc: DecimalEncodedValue) : TagParser {

    internal val safeHighwayTags: MutableSet<String> = HashSet()
    internal val avoidHighwayTags: MutableMap<String, PriorityCode> = HashMap()

    @JvmField
    protected var sidewalksNoValues: HashSet<String> = HashSet(5)

    @JvmField
    protected val priorityWayEncoder: DecimalEncodedValue = priorityEnc

    constructor(lookup: EncodedValueLookup) : this(lookup.getDecimalEncodedValue(VehiclePriority.key("foot")))

    init {
        sidewalksNoValues.add("no")
        sidewalksNoValues.add("none")
        // see #712
        sidewalksNoValues.add("separate")

        safeHighwayTags.add("footway")
        safeHighwayTags.add("path")
        safeHighwayTags.add("steps")
        safeHighwayTags.add("pedestrian")
        safeHighwayTags.add("living_street")
        safeHighwayTags.add("track")
        safeHighwayTags.add("residential")
        safeHighwayTags.add("service")
        safeHighwayTags.add("platform")

        avoidHighwayTags["motorway"] = REACH_DESTINATION // could be allowed when they have sidewalks
        avoidHighwayTags["motorway_link"] = REACH_DESTINATION
        avoidHighwayTags["trunk"] = REACH_DESTINATION
        avoidHighwayTags["trunk_link"] = REACH_DESTINATION
        avoidHighwayTags["primary"] = BAD
        avoidHighwayTags["primary_link"] = BAD
        avoidHighwayTags["secondary"] = BAD
        avoidHighwayTags["secondary_link"] = BAD
        avoidHighwayTags["tertiary"] = AVOID
        avoidHighwayTags["tertiary_link"] = AVOID
    }

    override fun handleWayTags(edgeId: Int, edgeIntAccess: EdgeIntAccess, way: ReaderWay, relationFlags: IntsRef?) {
        val highwayValue = way.getTag("highway")
        val weightToPrioMap = TreeMap<Double, PriorityCode>()
        weightToPrioMap[0.0] = UNCHANGED
        collect(way, weightToPrioMap)

        // pick priority with the biggest order value
        val priority = PriorityCode.getValue(weightToPrioMap.lastEntry().value.value)

        if (highwayValue == null) {
            if (FerrySpeedCalculator.isFerry(way))
                priorityWayEncoder.setDecimal(false, edgeId, edgeIntAccess, priority)
        } else {
            priorityWayEncoder.setDecimal(false, edgeId, edgeIntAccess, priority)
        }
    }

    /**
     * @param weightToPrioMap associate a weight with every priority. This sorted map allows
     *                        subclasses to 'insert' more important priorities as well as overwrite determined priorities.
     */
    internal open fun collect(way: ReaderWay, weightToPrioMap: TreeMap<Double, PriorityCode>) {
        val highway = way.getTag("highway")
        if (way.hasTag("foot", "designated"))
            weightToPrioMap[100.0] = PREFER

        if (way.hasTag("foot", "use_sidepath")) {
            weightToPrioMap[100.0] = VERY_BAD // see #3035
        }

        val maxSpeed = max(OSMMaxSpeedParser.parseMaxSpeed(way, false), OSMMaxSpeedParser.parseMaxSpeed(way, true))
        if (safeHighwayTags.contains(highway) || maxSpeed <= 20) {
            weightToPrioMap[40.0] = PREFER
            if (way.hasTag("tunnel", INTENDED)) {
                if (way.hasTag("sidewalk", sidewalksNoValues))
                    weightToPrioMap[40.0] = AVOID
                else
                    weightToPrioMap[40.0] = UNCHANGED
            }
        } else if ((maxSpeed != MaxSpeed.MAXSPEED_MISSING && maxSpeed > 50) || avoidHighwayTags.containsKey(highway)) {
            val priorityCode = avoidHighwayTags[highway]
            if (way.hasTag("sidewalk", sidewalksNoValues))
                weightToPrioMap[40.0] = priorityCode ?: BAD
            else
                weightToPrioMap[40.0] = priorityCode?.better()?.better() ?: AVOID
        } else if (way.hasTag("sidewalk", sidewalksNoValues))
            weightToPrioMap[40.0] = AVOID

        if (way.hasTag("bicycle", "official") || way.hasTag("bicycle", "designated"))
            weightToPrioMap[44.0] = SLIGHT_AVOID
    }
}
