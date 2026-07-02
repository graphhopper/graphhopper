package com.graphhopper.routing.util.parsers

import com.graphhopper.reader.ReaderWay
import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.routing.ev.EdgeIntAccess
import com.graphhopper.routing.ev.EnumEncodedValue
import com.graphhopper.routing.ev.RouteNetwork
import com.graphhopper.routing.ev.Smoothness
import com.graphhopper.routing.util.FerrySpeedCalculator
import com.graphhopper.routing.util.parsers.AbstractAccessParser.Companion.INTENDED
import kotlin.math.max
import kotlin.math.min

abstract class BikeCommonAverageSpeedParser protected constructor(
    speedEnc: DecimalEncodedValue,
    private val smoothnessEnc: EnumEncodedValue<Smoothness>,
    private val bikeRouteEnc: EnumEncodedValue<RouteNetwork>
) : AbstractAverageSpeedParser(speedEnc), TagParser {

    private val trackTypeSpeeds = HashMap<String, Int>()
    private val surfaceSpeeds = HashMap<String, Int>()
    private val smoothnessFactor = HashMap<Smoothness, Double>()
    private val highwaySpeeds = HashMap<String, Int>()
    private val restrictedValues = setOf("no", "agricultural", "forestry", "restricted", "military", "emergency", "private", "permit")

    init {
        setTrackTypeSpeed("grade1", 18) // paved
        setTrackTypeSpeed("grade2", 14) // like compacted
        setTrackTypeSpeed("grade3", 12) // like unpaved
        setTrackTypeSpeed("grade4", 10) // better than grass, more like dirt ("hard or compacted materials mixed in")
        setTrackTypeSpeed("grade5", PUSHING_SECTION_SPEED) // like sand

        setSurfaceSpeed("paved", 18)
        setSurfaceSpeed("asphalt", 18)
        setSurfaceSpeed("cobblestone", 8)
        setSurfaceSpeed("cobblestone:flattened", 10)
        setSurfaceSpeed("sett", 12)
        setSurfaceSpeed("concrete", 18)
        setSurfaceSpeed("concrete:lanes", 16)
        setSurfaceSpeed("concrete:plates", 16)
        setSurfaceSpeed("paving_stones", 16)
        setSurfaceSpeed("paving_stones:30", 16)
        setSurfaceSpeed("unpaved", 12)
        setSurfaceSpeed("compacted", 14)
        setSurfaceSpeed("dirt", 10)
        setSurfaceSpeed("earth", 10)
        setSurfaceSpeed("ground", 10)
        setSurfaceSpeed("fine_gravel", 14) // should not be faster than compacted
        setSurfaceSpeed("grass", 8)
        setSurfaceSpeed("grass_paver", 8)
        setSurfaceSpeed("gravel", 12)
        setSurfaceSpeed("ice", MIN_SPEED)
        setSurfaceSpeed("metal", 10)
        setSurfaceSpeed("mud", 10)
        setSurfaceSpeed("pebblestone", 14)
        setSurfaceSpeed("salt", PUSHING_SECTION_SPEED)
        setSurfaceSpeed("sand", PUSHING_SECTION_SPEED)
        setSurfaceSpeed("wood", PUSHING_SECTION_SPEED)

        setHighwaySpeed("steps", MIN_SPEED)

        setHighwaySpeed("cycleway", 18)
        setHighwaySpeed("path", 6)
        setHighwaySpeed("footway", 6)
        setHighwaySpeed("platform", 6)
        setHighwaySpeed("pedestrian", 6)
        setHighwaySpeed("bridleway", 6)
        setHighwaySpeed("track", 12)

        setHighwaySpeed("living_street", 12)
        setHighwaySpeed("service", 18)
        setHighwaySpeed("residential", 18)
        setHighwaySpeed("unclassified", 18)
        setHighwaySpeed("road", 18)
        setHighwaySpeed("trunk", 18)
        setHighwaySpeed("trunk_link", 18)
        setHighwaySpeed("primary", 18)
        setHighwaySpeed("primary_link", 18)
        setHighwaySpeed("secondary", 18)
        setHighwaySpeed("secondary_link", 18)
        setHighwaySpeed("tertiary", 18)
        setHighwaySpeed("tertiary_link", 18)

        // special case see tests and #191
        setHighwaySpeed("motorway", 18)
        setHighwaySpeed("motorway_link", 18)

        // note that this factor reduces the speed but only until MIN_SPEED
        setSmoothnessSpeedFactor(Smoothness.MISSING, 1.0)
        setSmoothnessSpeedFactor(Smoothness.OTHER, 0.7)
        setSmoothnessSpeedFactor(Smoothness.EXCELLENT, 1.1)
        setSmoothnessSpeedFactor(Smoothness.GOOD, 1.0)
        setSmoothnessSpeedFactor(Smoothness.INTERMEDIATE, 0.9)
        setSmoothnessSpeedFactor(Smoothness.BAD, 0.7)
        setSmoothnessSpeedFactor(Smoothness.VERY_BAD, 0.4)
        setSmoothnessSpeedFactor(Smoothness.HORRIBLE, 0.3)
        setSmoothnessSpeedFactor(Smoothness.VERY_HORRIBLE, 0.1)
        setSmoothnessSpeedFactor(Smoothness.IMPASSABLE, 0.0)
    }

    /**
     * @param way   needed to retrieve tags
     * @param speed speed guessed e.g. from the road type or other tags
     * @return The assumed average speed.
     */
    @JvmName("applyMaxSpeed")
    internal fun applyMaxSpeed(way: ReaderWay, speed: Double, bwd: Boolean): Double {
        val maxSpeed = OSMMaxSpeedParser.parseMaxSpeed(way, bwd)
        // We strictly obey speed limits, see #600
        return min(speed, maxSpeed)
    }

    override fun handleWayTags(edgeId: Int, edgeIntAccess: EdgeIntAccess, way: ReaderWay) {
        val highwayValue = way.getTag("highway", "")
        if (highwayValue.isEmpty()) {
            if (FerrySpeedCalculator.isFerry(way) || !way.hasTag("railway", "platform") && !way.hasTag("man_made", "pier"))
                return
        }

        var speed = (highwaySpeeds[highwayValue] ?: PUSHING_SECTION_SPEED).toDouble()
        val surfaceValue = way.getTag("surface")
        val trackTypeValue = way.getTag("tracktype")
        val pushingRestriction = way.getTag("vehicle", "").split(";").any { restrictedValues.contains(it) }
        var surfaceSpeed = surfaceSpeeds[surfaceValue]
        val trackTypeSpeed = trackTypeSpeeds[trackTypeValue]
        if (trackTypeSpeed != null)
            surfaceSpeed = if (surfaceSpeed == null) trackTypeSpeed else min(surfaceSpeed, trackTypeSpeed)
        val bikeDesignated = RouteNetwork.MISSING != bikeRouteEnc.getEnum(false, edgeId, edgeIntAccess)
                || BikeCommonPriorityParser.isBikeDesignated(way)

        if (way.hasTag("surface") && surfaceSpeed == null
                || way.hasTag("bicycle", "dismount")
                || way.hasTag("railway", "platform")
                || pushingRestriction && !way.hasTag("bicycle", INTENDED)) {
            speed = PUSHING_SECTION_SPEED.toDouble()

        } else {

            var bikeAllowed = way.hasTag("bicycle", "yes") || bikeDesignated
            val isRacingBike = this is RacingBikeAverageSpeedParser

            // increase speed for certain highway tags because of a good surface or a more permissive bike access.
            // this mirrors the fall-through Java switch: "track" falls into "path"/"bridleway", which falls into
            // "footway"/"pedestrian"/"platform" unless the racing bike 'break' applies
            val trackCase = highwayValue == "track"
            val pathCase = trackCase || highwayValue == "path" || highwayValue == "bridleway"
            val footwayCase = pathCase || highwayValue == "footway" || highwayValue == "pedestrian" || highwayValue == "platform"
            var breakBeforeFootwayCase = false

            if (trackCase) // assume bicycle=yes even if no bicycle tag
                bikeAllowed = bikeAllowed || !way.hasTag("bicycle")

            if (pathCase) {
                if (surfaceSpeed != null)
                    speed = max(speed, if (bikeAllowed) surfaceSpeed.toDouble() else surfaceSpeed * 0.7)
                else if (isRacingBike)
                    breakBeforeFootwayCase = true // no speed increase if no surface tag
            }

            if (footwayCase && !breakBeforeFootwayCase) {
                // speed increase if bike allowed or even designated
                if (bikeDesignated)
                    speed = max(speed, highwaySpeeds["cycleway"]!!.toDouble())
                else if (bikeAllowed)
                    speed = max(speed, 12.0)
            }

            if (way.hasTag("service", "parking_aisle") && !bikeDesignated)
                speed = min(speed, 8.0)

            val smoothSpeed = smoothnessFactor[smoothnessEnc.getEnum(false, edgeId, edgeIntAccess)]!! * speed

            // speed reduction if bad surface
            speed = if (surfaceSpeed != null) {
                // pick the smallest of smoothness<->surface, if both are present
                max(MIN_SPEED.toDouble(), min(min(surfaceSpeed.toDouble(), speed), smoothSpeed))
            } else {
                max(MIN_SPEED.toDouble(), smoothSpeed)
            }
        }

        setSpeed(false, edgeId, edgeIntAccess, applyMaxSpeed(way, speed, false))
        if (avgSpeedEnc.isStoreTwoDirections)
            setSpeed(true, edgeId, edgeIntAccess, applyMaxSpeed(way, speed, true))
    }

    internal fun setHighwaySpeed(highway: String, speed: Int) {
        highwaySpeeds[highway] = speed
    }

    internal fun setTrackTypeSpeed(tracktype: String, speed: Int) {
        trackTypeSpeeds[tracktype] = speed
    }

    internal fun setSurfaceSpeed(surface: String, speed: Int) {
        surfaceSpeeds[surface] = speed
    }

    internal fun setSmoothnessSpeedFactor(smoothness: Smoothness, speedfactor: Double) {
        smoothnessFactor[smoothness] = speedfactor
    }

    companion object {
        protected const val PUSHING_SECTION_SPEED = 4
        protected const val MIN_SPEED = 2
    }
}
