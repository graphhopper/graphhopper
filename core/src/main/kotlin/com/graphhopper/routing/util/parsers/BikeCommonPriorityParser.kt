package com.graphhopper.routing.util.parsers

import com.graphhopper.reader.ReaderWay
import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.routing.ev.EdgeIntAccess
import com.graphhopper.routing.ev.MaxSpeed
import com.graphhopper.routing.util.FerrySpeedCalculator
import com.graphhopper.routing.util.PriorityCode
import com.graphhopper.routing.util.PriorityCode.AVOID
import com.graphhopper.routing.util.PriorityCode.AVOID_MORE
import com.graphhopper.routing.util.PriorityCode.BAD
import com.graphhopper.routing.util.PriorityCode.BEST
import com.graphhopper.routing.util.PriorityCode.EXCLUDE
import com.graphhopper.routing.util.PriorityCode.PREFER
import com.graphhopper.routing.util.PriorityCode.REACH_DESTINATION
import com.graphhopper.routing.util.PriorityCode.SLIGHT_AVOID
import com.graphhopper.routing.util.PriorityCode.SLIGHT_PREFER
import com.graphhopper.routing.util.PriorityCode.UNCHANGED
import com.graphhopper.routing.util.PriorityCode.VERY_NICE
import com.graphhopper.routing.util.parsers.AbstractAccessParser.Companion.INTENDED
import com.graphhopper.storage.IntsRef
import java.util.TreeMap
import kotlin.math.max

abstract class BikeCommonPriorityParser protected constructor(
    @JvmField protected val priorityEnc: DecimalEncodedValue
) : TagParser {

    // pushing section highways are parts where you need to get off your bike and push it
    @JvmField
    protected val pushingSectionsHighways = HashSet<String>()

    @JvmField
    protected val preferHighwayTags: MutableSet<String> = HashSet()

    @JvmField
    protected val avoidHighwayTags: MutableMap<String, PriorityCode> = HashMap()

    internal var avoidSpeedLimit: Double = 71.0

    @JvmField
    protected val goodSurface: Set<String> = setOf("paved", "asphalt", "concrete")

    // This is the specific bicycle class
    private var classBicycleKey: String? = null

    init {
        addPushingSection("footway")
        addPushingSection("pedestrian")
        addPushingSection("steps")
        addPushingSection("platform")

        avoidHighwayTags["motorway"] = REACH_DESTINATION
        avoidHighwayTags["motorway_link"] = REACH_DESTINATION
        avoidHighwayTags["trunk"] = REACH_DESTINATION
        avoidHighwayTags["trunk_link"] = REACH_DESTINATION
        avoidHighwayTags["secondary"] = AVOID
        avoidHighwayTags["secondary_link"] = AVOID
        avoidHighwayTags["bridleway"] = AVOID
    }

    override fun handleWayTags(edgeId: Int, edgeIntAccess: EdgeIntAccess, way: ReaderWay, relationFlags: IntsRef?) {
        val highwayValue = way.getTag("highway")
        if (highwayValue == null && !FerrySpeedCalculator.isFerry(way)) return

        val weightToPrioMap = TreeMap<Double, PriorityCode>()
        weightToPrioMap[0.0] = UNCHANGED
        collect(way, isBikeDesignated(way), weightToPrioMap)

        // pick priority with the biggest order value
        val prio = PriorityCode.getValue(weightToPrioMap.lastEntry().value.value)
        priorityEnc.setDecimal(false, edgeId, edgeIntAccess, prio)
    }

    // Conversion of class value to priority. See http://wiki.openstreetmap.org/wiki/Class:bicycle
    private fun convertClassValueToPriority(tagvalue: String): PriorityCode {
        return try {
            when (tagvalue.toInt()) {
                3 -> BEST
                2 -> VERY_NICE
                1 -> PREFER
                -1 -> AVOID
                -2 -> BAD
                -3 -> REACH_DESTINATION
                else -> UNCHANGED
            }
        } catch (e: NumberFormatException) {
            UNCHANGED
        }
    }

    /**
     * @param weightToPrioMap associate a weight with every priority. This sorted map allows
     *                        subclasses to 'insert' more important priorities as well as overwrite determined priorities.
     */
    internal open fun collect(way: ReaderWay, bikeDesignated: Boolean, weightToPrioMap: TreeMap<Double, PriorityCode>) {
        val highway = way.getTag("highway")
        if (bikeDesignated) {
            val isGoodSurface = way.getTag("tracktype", "") == "grade1" || goodSurface.contains(way.getTag("surface", ""))
            if ("path" == highway || "track" == highway && isGoodSurface)
                weightToPrioMap[100.0] = VERY_NICE
            else
                weightToPrioMap[100.0] = PREFER
        }

        val maxSpeed = max(OSMMaxSpeedParser.parseMaxSpeed(way, false), OSMMaxSpeedParser.parseMaxSpeed(way, true))
        if ("cycleway" == highway && preferHighwayTags.contains(highway)) {
            if (way.hasTag("foot", INTENDED) && !way.hasTag("segregated", "yes"))
                weightToPrioMap[100.0] = PREFER
            else
                weightToPrioMap[100.0] = VERY_NICE
        } else if (preferHighwayTags.contains(highway) || maxSpeed <= 30) {
            if (maxSpeed == MaxSpeed.MAXSPEED_MISSING || maxSpeed < avoidSpeedLimit) {
                weightToPrioMap[40.0] = SLIGHT_PREFER
                if (way.hasTag("tunnel", INTENDED))
                    weightToPrioMap[40.0] = UNCHANGED
            }
        } else if (avoidHighwayTags.containsKey(highway)
                || (maxSpeed != MaxSpeed.MAXSPEED_MISSING && maxSpeed >= avoidSpeedLimit && "track" != highway)) {
            val priorityCode = avoidHighwayTags[highway]
            weightToPrioMap[50.0] = priorityCode ?: AVOID
            if (way.hasTag("tunnel", INTENDED)) {
                val worse = priorityCode?.worse()?.worse() ?: BAD
                weightToPrioMap[50.0] = if (worse == EXCLUDE) REACH_DESTINATION else worse
            }
        }

        if (way.hasTag("bicycle", "use_sidepath")) {
            weightToPrioMap[100.0] = REACH_DESTINATION
        } else if (way.hasTag("bicycle", "optional_sidepath")) {
            weightToPrioMap[100.0] = AVOID
        }

        val cyclewayValues = listOf("cycleway", "cycleway:left", "cycleway:both", "cycleway:right")
                .mapTo(HashSet()) { way.getTag(it, "") }
        if (cyclewayValues.contains("track")) {
            weightToPrioMap[100.0] = VERY_NICE
        } else if (listOf("lane", "opposite_track", "shared_lane", "share_busway", "shoulder").any { cyclewayValues.contains(it) }) {
            val current = weightToPrioMap.lastEntry().value
            if (current.value < PREFER.value)
                weightToPrioMap[100.0] = current.better()
        } else if (pushingSectionsHighways.contains(highway) || "parking_aisle" == way.getTag("service")) {
            var pushingSectionPrio = SLIGHT_AVOID
            if (way.hasTag("highway", "steps"))
                pushingSectionPrio = BAD
            else if (way.hasTag("bicycle", "yes") || way.hasTag("bicycle", "permissive"))
                pushingSectionPrio = PREFER
            else if (bikeDesignated)
                pushingSectionPrio = VERY_NICE

            if (way.hasTag("foot", "yes") && !way.hasTag("segregated", "yes"))
                pushingSectionPrio = pushingSectionPrio.worse()

            weightToPrioMap[100.0] = pushingSectionPrio
        }

        if (way.hasTag("railway", "tram"))
            weightToPrioMap[50.0] = AVOID_MORE

        var classBicycleValue = classBicycleKey?.let { way.getTag(it) }
        if (classBicycleValue == null) classBicycleValue = way.getTag("class:bicycle")

        // We assume that humans are better in classifying preferences compared to our algorithm above
        if (classBicycleValue != null) {
            val prio = convertClassValueToPriority(classBicycleValue)
            // do not overwrite if e.g. designated
            weightToPrioMap.compute(100.0) { _, existing ->
                if (existing == null || existing.value < prio.value) prio else existing
            }
        }
    }

    internal fun addPushingSection(highway: String) {
        pushingSectionsHighways.add(highway)
    }

    internal fun setSpecificClassBicycle(subkey: String) {
        classBicycleKey = "class:bicycle:$subkey"
    }

    fun getPriorityEnc(): DecimalEncodedValue = priorityEnc

    companion object {
        private val CYCLEWAY_KEYS = setOf("cycleway", "cycleway:left", "cycleway:both", "cycleway:right")

        // rare use case when a bicycle lane has access tag
        private val CYCLEWAY_BICYCLE_KEYS = listOf("cycleway:bicycle", "cycleway:both:bicycle", "cycleway:left:bicycle", "cycleway:right:bicycle")

        internal fun isBikeDesignated(way: ReaderWay): Boolean {
            return way.hasTag("bicycle", "designated")
                    || way.hasTag("bicycle", "official")
                    || way.hasTag("segregated", "yes")
                    || way.hasTag("bicycle_road", "yes")
                    || way.hasTag("cyclestreet", "yes")
                    || CYCLEWAY_KEYS.any { way.getTag(it, "") == "track" }
                    || way.hasTag(CYCLEWAY_BICYCLE_KEYS, "designated")
        }
    }
}
