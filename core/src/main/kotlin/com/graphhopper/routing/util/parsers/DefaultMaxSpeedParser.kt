package com.graphhopper.routing.util.parsers

import com.graphhopper.reader.ReaderWay
import com.graphhopper.routing.ev.Country
import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.routing.ev.EdgeIntAccess
import com.graphhopper.routing.ev.MaxSpeed.MAXSPEED_150
import com.graphhopper.routing.ev.MaxSpeed.MAXSPEED_MISSING
import com.graphhopper.routing.ev.State
import com.graphhopper.storage.IntsRef
import de.westnordost.osm_legal_default_speeds.LegalDefaultSpeeds

class DefaultMaxSpeedParser(private val speeds: LegalDefaultSpeeds) : TagParser {

    private var ruralMaxSpeedEnc: DecimalEncodedValue? = null
    private var urbanMaxSpeedEnc: DecimalEncodedValue? = null
    private var externalAccess: EdgeIntAccess? = null

    fun init(ruralMaxSpeedEnc: DecimalEncodedValue, urbanMaxSpeedEnc: DecimalEncodedValue, externalAccess: EdgeIntAccess) {
        this.ruralMaxSpeedEnc = ruralMaxSpeedEnc
        this.urbanMaxSpeedEnc = urbanMaxSpeedEnc
        this.externalAccess = externalAccess
    }

    override fun handleWayTags(edgeId: Int, edgeIntAccess: EdgeIntAccess, way: ReaderWay, relationFlags: IntsRef?) {
        val externalAccess = this.externalAccess
            ?: throw IllegalArgumentException("Call init before using " + javaClass.name)
        val maxSpeed = maxOf(OSMMaxSpeedParser.parseMaxSpeed(way, false), OSMMaxSpeedParser.parseMaxSpeed(way, true))
        var ruralSpeedInt: Int? = null
        var urbanSpeedInt: Int? = null
        if (maxSpeed == MAXSPEED_MISSING) {
            val country = way.getTag("country", Country.MISSING)
            val state = way.getTag("country_state", State.MISSING)
            if (country != Country.MISSING) {
                val code = if (state == State.MISSING) country.alpha2 else state.stateCode
                val tags = filter(way.getTags())
                // Workaround for GBR. Default is used for "urban" but ignored for "rural".
                if (country == Country.GBR) tags["lit"] = "yes"

                // with computeIfAbsent we calculate the expensive hashCode of the key only once
                val result = cache.computeIfAbsent(tags) {
                    val internRes = Result()
                    var tmpResult = speeds.getSpeedLimits(code,
                        tags, emptyList()) { name, eval -> eval() || "rural" == name }
                    if (tmpResult != null) {
                        internRes.rural = parseInt(tmpResult.tags["maxspeed"])
                        if (internRes.rural == null && "130" == tmpResult.tags["maxspeed:advisory"])
                            internRes.rural = MAXSPEED_150.toInt()
                    }

                    tmpResult = speeds.getSpeedLimits(code,
                        tags, emptyList()) { name, eval -> eval() || "urban" == name }
                    if (tmpResult != null) {
                        internRes.urban = parseInt(tmpResult.tags["maxspeed"])
                        if (internRes.urban == null && "130" == tmpResult.tags["maxspeed:advisory"])
                            internRes.urban = MAXSPEED_150.toInt()
                    }
                    internRes
                }

                ruralSpeedInt = result.rural
                urbanSpeedInt = result.urban
            }
        }

        urbanMaxSpeedEnc!!.setDecimal(false, edgeId, externalAccess, urbanSpeedInt?.toDouble() ?: MAXSPEED_MISSING)
        ruralMaxSpeedEnc!!.setDecimal(false, edgeId, externalAccess, ruralSpeedInt?.toDouble() ?: MAXSPEED_MISSING)
    }

    private fun filter(tags: Map<String, Any>): MutableMap<String, String> {
        val map = HashMap<String, String>(tags.size)
        for ((key, value) in tags) {
            if (speeds.isRelevantTagKey(key)
                || key == "country"
                || key == "country_state"
                // the :conditional tags are not yet necessary for us and expensive in the speeds library
                // see https://github.com/westnordost/osm-legal-default-speeds/issues/7
                || key.startsWith("maxspeed:") && !key.endsWith(":conditional")
            )
                map[key] = value.toString()
        }
        return map
    }

    private class Result {
        var urban: Int? = null
        var rural: Int? = null
    }

    private val cache = object : LinkedHashMap<Map<String, String>, Result>(SIZE + 1, .75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<Map<String, String>, Result>): Boolean {
            return size > SIZE
        }
    }

    companion object {
        private const val SIZE = 3_000

        private fun parseInt(str: String?): Int? {
            return try {
                Integer.parseInt(str)
            } catch (ex: NumberFormatException) {
                null
            }
        }
    }
}
