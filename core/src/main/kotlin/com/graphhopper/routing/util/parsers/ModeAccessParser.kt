package com.graphhopper.routing.util.parsers

import com.graphhopper.reader.ReaderWay
import com.graphhopper.routing.ev.BooleanEncodedValue
import com.graphhopper.routing.ev.EdgeIntAccess
import com.graphhopper.routing.util.FerrySpeedCalculator
import com.graphhopper.routing.util.parsers.OSMTemporalAccessParser.Companion.hasPermissiveTemporalRestriction
import com.graphhopper.storage.IntsRef

class ModeAccessParser @JvmOverloads constructor(
    private val restrictionKeys: List<String>,
    private val accessEnc: BooleanEncodedValue,
    private val skipEmergency: Boolean,
    private val roundaboutEnc: BooleanEncodedValue,
    allow: Set<String> = emptySet(),
    restrict: Set<String> = emptySet()
) : TagParser {

    private val intended: MutableSet<String> = HashSet(INTENDED)
    private val restricted: MutableSet<String> = HashSet(RESTRICTED)
    private val vehicleForward: List<String> = restrictionKeys.map { "$it:forward" }
    private val vehicleBackward: List<String> = restrictionKeys.map { "$it:backward" }
    private val onewayModeKeys: List<String> = restrictionKeys.map { "oneway:$it" }

    init {
        for (value in allow) {
            if (restricted.remove(value))
                intended.add(value)
            else if (!intended.contains(value))
                throw IllegalArgumentException("cannot allow '$value' — not a known restricted value")
        }
        for (value in restrict) {
            if (intended.contains(value))
                throw IllegalArgumentException("cannot restrict '$value' — it is an intended value")
            restricted.add(value)
        }
    }

    override fun handleWayTags(edgeId: Int, edgeIntAccess: EdgeIntAccess, way: ReaderWay, relationFlags: IntsRef?) {
        val highwayValue = way.getTag("highway")
        if (highwayValue != null || FerrySpeedCalculator.isFerry(way)) {
            handleHighwayAndFerryTags(edgeId, edgeIntAccess, way, highwayValue)
        }
        // don't want platforms and other random stuff here for now
    }

    private fun handleHighwayAndFerryTags(edgeId: Int, edgeIntAccess: EdgeIntAccess, way: ReaderWay, highwayValue: String?) {
        if (skipEmergency && "service" == highwayValue && "emergency_access" == way.getTag("service"))
            return

        var defaults: Map<String, String> =
            if (highwayValue == null) emptyMap() else HIGHWAY_TYPE_DEFAULTS.getOrDefault(highwayValue, emptyMap())
        // motorroad=yes is an annoying special case: it's not a highway=* value but a separate tag
        // that implies foot=no, bicycle=no. If we find more tags like this, we'll need a more
        // general mechanism for non-highway implied defaults.
        if (way.hasTag("motorroad", "yes")) {
            val merged = HashMap(defaults)
            MOTORROAD_DEFAULTS.forEach { (k, v) -> merged.putIfAbsent(k, v) }
            defaults = merged
        }
        var firstIndex = -1
        var firstValue = ""
        for (i in restrictionKeys.indices) {
            val key = restrictionKeys[i]
            val explicit = way.getTag(key)
            if (explicit != null) {
                if (intended.contains(explicit) || restricted.contains(explicit)) {
                    firstIndex = i
                    firstValue = explicit
                    break
                }
                // unknown value — fall through to implied default for same key, then keep looking
            }
            val implied = defaults[key]
            if (implied != null) {
                firstIndex = i
                firstValue = implied
                break
            }
        }
        if (restricted.contains(firstValue) && !hasPermissiveTemporalRestriction(way, firstIndex, restrictionKeys, intended))
            return

        if (way.hasTag("gh:barrier_edge") && way.hasTag("node_tags")) {
            val nodeTags: List<Map<String, Any>> = way.getTag<List<Map<String, Any>>?>("node_tags", null)!!
            val firstNodeTags = nodeTags[0]
            val barrierValue = if (firstNodeTags.containsKey("barrier")) firstNodeTags["barrier"] as String else ""
            val barrierDefaults = BARRIER_TYPE_DEFAULTS.getOrDefault(barrierValue, emptyMap())
            // Walk restriction keys checking explicit node tags and barrier type defaults
            var nodeValue = ""
            for (key in restrictionKeys) {
                val explicit = firstNodeTags[key] as String?
                if (explicit != null) {
                    if (intended.contains(explicit) || restricted.contains(explicit)) {
                        nodeValue = explicit
                        break
                    }
                }
                val implied = barrierDefaults[key]
                if (implied != null) {
                    nodeValue = implied
                    break
                }
            }
            if (restricted.contains(nodeValue))
                return
            if ("yes" == firstNodeTags["locked"] && !intended.contains(nodeValue))
                return
        }

        if (FerrySpeedCalculator.isFerry(way)) {
            val isCar = restrictionKeys.contains("motorcar")
            if (intended.contains(firstValue)
                // implied default is allowed only if foot and bicycle is not specified:
                || isCar && firstValue.isEmpty() && !way.hasTag("foot") && !way.hasTag("bicycle")
                // if hgv is allowed then smaller trucks and cars are allowed too even if not specified
                || isCar && way.hasTag("hgv", "yes")
            ) {
                // ferry is allowed via explicit tag
            } else {
                return
            }
        }

        val isRoundabout = roundaboutEnc.getBool(false, edgeId, edgeIntAccess)
        if (isForwardAccessible(way))
            accessEnc.setBool(false, edgeId, edgeIntAccess, true)
        if (isBackwardAccessible(way, isRoundabout))
            accessEnc.setBool(true, edgeId, edgeIntAccess, true)
    }

    /**
     * The two directions are computed independently so that blockings from different
     * tag families stack: e.g. oneway=yes closing backward plus bus:forward=no closing
     * forward leaves a bus with nowhere to go, rather than being treated as a single
     * backward-oneway by the dominant rule.
     *
     * Within each direction, rules are evaluated top-to-bottom; the first rule that
     * matches wins. More-specific tags come first, so a mode-specific tag always beats
     * a generic one.
     */
    private fun isForwardAccessible(way: ReaderWay): Boolean {
        val modeOneway = way.getFirstValue(onewayModeKeys)
        // 1. explicit per-mode oneway override: oneway:<mode>=yes/-1/no
        if ("no" == modeOneway) return true
        if (ONEWAYS_FW.contains(modeOneway)) return true
        if ("-1" == modeOneway) return false
        // 2. mode-specific directional prohibition: <mode>:forward=no
        if ("no" == way.getFirstValue(vehicleForward)) return false
        // 3. generic oneway tag (forward direction is only closed by oneway=-1;
        //    motorway/roundabout imply forward-oneway, which leaves forward open)
        return !way.hasTag("oneway", "-1")
    }

    private fun isBackwardAccessible(way: ReaderWay, isRoundabout: Boolean): Boolean {
        val modeOneway = way.getFirstValue(onewayModeKeys)
        // 1. explicit per-mode oneway override
        if ("no" == modeOneway) return true
        if (ONEWAYS_FW.contains(modeOneway)) return false
        if ("-1" == modeOneway) return true
        // 2. mode-specific directional prohibition: <mode>:backward=no
        if ("no" == way.getFirstValue(vehicleBackward)) return false
        // 3. generic oneway tag, then implied oneway from highway type / roundabout.
        //    oneway=no explicitly relaxes the motorway/roundabout implication.
        if (way.hasTag("oneway", ONEWAYS_FW)) return false
        if (way.hasTag("oneway", "no")) return true
        if (way.hasTag("highway", "motorway", "motorway_link")) return false
        return !isRoundabout
    }

    companion object {
        private val INTENDED = setOf("yes", "designated", "official", "permissive", "destination")
        private val RESTRICTED = setOf(
            "no", "restricted", "military", "emergency",
            "private", "permit", "service", "delivery", "customers", "agricultural", "forestry"
        )
        private val MOTORROAD_DEFAULTS = mapOf("foot" to "no", "bicycle" to "no")
        private val ONEWAYS_FW = setOf("yes", "true", "1")

        // https://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Access_restrictions
        // For implied bicycle defaults we use "dismount" rather than "no" on highways where
        // pushing a bike is physically reasonable (pedestrian streets, footways, stairs, indoor
        // corridors, station platforms). Our routing layer treats DISMOUNT as "can push" —
        // usable but qualified — whereas NO is treated as a hard block. motorway/motorway_link
        // keep "no" because bikes are legally prohibited even on foot. Explicit bicycle=no tags
        // on a way still resolve to NO; only the implicit highway-type defaults become DISMOUNT.
        internal val HIGHWAY_TYPE_DEFAULTS: Map<String, Map<String, String>> = mapOf(
            "motorway" to mapOf("motor_vehicle" to "designated", "foot" to "no", "bicycle" to "no"),
            "motorway_link" to mapOf("motor_vehicle" to "designated", "foot" to "no", "bicycle" to "no"),
            "steps" to mapOf("motor_vehicle" to "no", "bicycle" to "dismount", "foot" to "designated"),
            "footway" to mapOf("motor_vehicle" to "no", "bicycle" to "dismount", "foot" to "designated"),
            "cycleway" to mapOf("motor_vehicle" to "no", "bicycle" to "designated", "foot" to "no"),
            "pedestrian" to mapOf("motor_vehicle" to "no", "bicycle" to "dismount", "foot" to "designated"),
            "path" to mapOf("motor_vehicle" to "no", "foot" to "yes", "bicycle" to "yes"),
            "bridleway" to mapOf("motor_vehicle" to "no", "foot" to "yes", "bicycle" to "yes"),
            "busway" to mapOf("access" to "no", "bus" to "designated"),
            "construction" to mapOf("access" to "no"),
            "proposed" to mapOf("access" to "no"),
            "raceway" to mapOf("access" to "no"),
            // corridor stays bicycle=no (not "dismount") because BikeCommonAccessParser doesn't list
            // it in allowedHighways — bike_access is already false there. Marking road_access as
            // DISMOUNT would suggest "can push" while access says "can't use it at all".
            "corridor" to mapOf("motor_vehicle" to "no", "bicycle" to "no", "foot" to "yes"),
            "platform" to mapOf("motor_vehicle" to "no", "bicycle" to "dismount", "foot" to "yes")
        )

        // https://wiki.openstreetmap.org/wiki/Key:barrier
        internal val BARRIER_TYPE_DEFAULTS: Map<String, Map<String, String>> = mapOf(
            "fence" to mapOf("access" to "no"),
            "wall" to mapOf("access" to "no"),
            "hedge" to mapOf("access" to "no"),
            "retaining_wall" to mapOf("access" to "no"),
            "city_wall" to mapOf("access" to "no"),
            "ditch" to mapOf("access" to "no"),
            "kerb" to mapOf("vehicle" to "yes", "foot" to "yes"),
            "cattle_grid" to mapOf("motor_vehicle" to "yes", "foot" to "yes", "bicycle" to "yes"),
            "bollard" to mapOf("motor_vehicle" to "no", "foot" to "yes", "bicycle" to "yes"),
            "block" to mapOf("motor_vehicle" to "no", "foot" to "yes", "bicycle" to "yes"),
            "log" to mapOf("motor_vehicle" to "no", "foot" to "yes", "bicycle" to "yes"),
            "chain" to mapOf("motor_vehicle" to "no", "foot" to "yes", "bicycle" to "yes"),
            "jersey_barrier" to mapOf("motor_vehicle" to "no", "foot" to "yes", "bicycle" to "yes"),
            "cycle_barrier" to mapOf("motor_vehicle" to "no", "foot" to "yes", "bicycle" to "yes"),
            "motorcycle_barrier" to mapOf("motor_vehicle" to "no", "foot" to "yes", "bicycle" to "yes"),
            "bus_trap" to mapOf("motor_vehicle" to "no", "bus" to "yes", "foot" to "yes", "bicycle" to "yes"),
            "sump_buster" to mapOf("motor_vehicle" to "no", "bus" to "yes", "foot" to "yes", "bicycle" to "yes"),
            "kissing_gate" to mapOf("vehicle" to "no", "foot" to "yes"),
            "stile" to mapOf("vehicle" to "no", "foot" to "yes"),
            "turnstile" to mapOf("vehicle" to "no", "foot" to "yes")
        )
    }
}
