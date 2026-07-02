package com.graphhopper.routing.util

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.graphhopper.routing.ev.BooleanEncodedValue
import com.graphhopper.routing.ev.Country
import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.routing.ev.DecimalEncodedValueImpl
import com.graphhopper.routing.ev.EdgeIntAccess
import com.graphhopper.routing.ev.EncodedValue
import com.graphhopper.routing.ev.MaxSpeed
import com.graphhopper.routing.ev.MaxSpeedEstimated
import com.graphhopper.routing.ev.UrbanDensity
import com.graphhopper.routing.util.parsers.DefaultMaxSpeedParser
import com.graphhopper.routing.util.parsers.OSMMaxSpeedParser
import com.graphhopper.routing.util.parsers.TagParser
import com.graphhopper.storage.DataAccess
import com.graphhopper.storage.Directory
import com.graphhopper.storage.Graph
import com.graphhopper.util.EdgeIteratorState
import com.graphhopper.util.StopWatch
import de.westnordost.osm_legal_default_speeds.LegalDefaultSpeeds
import de.westnordost.osm_legal_default_speeds.RoadType
import de.westnordost.osm_legal_default_speeds.RoadTypeFilter
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.function.Function

class MaxSpeedCalculator(val defaultSpeeds: LegalDefaultSpeeds) {

    private val parser: DefaultMaxSpeedParser = DefaultMaxSpeedParser(defaultSpeeds)
    private var internalMaxSpeedStorage: EdgeIntAccess? = null
    private var ruralMaxSpeedEnc: DecimalEncodedValue? = null
    private var urbanMaxSpeedEnc: DecimalEncodedValue? = null
    private var dataAccess: DataAccess? = null

    @JvmName("getRuralMaxSpeedEnc")
    internal fun getRuralMaxSpeedEnc(): DecimalEncodedValue? = ruralMaxSpeedEnc

    fun getUrbanMaxSpeedEnc(): DecimalEncodedValue? = urbanMaxSpeedEnc

    @JvmName("getInternalMaxSpeedStorage")
    internal fun getInternalMaxSpeedStorage(): EdgeIntAccess? = internalMaxSpeedStorage

    /**
     * Creates temporary uni dir max_speed storage that is removed after import.
     */
    private fun createMaxSpeedStorage(dataAccess: DataAccess): EdgeIntAccess = object : EdgeIntAccess {

        override fun getInt(edgeId: Int, index: Int): Int {
            dataAccess.ensureCapacity(edgeId * 2L + 2L)
            return dataAccess.getShort(edgeId * 2L).toInt()
        }

        override fun setInt(edgeId: Int, index: Int, value: Int) {
            dataAccess.ensureCapacity(edgeId * 2L + 2L)
            if (value > Short.MAX_VALUE)
                throw IllegalStateException("value too large for short: $value")
            dataAccess.setShort(edgeId * 2L, value.toShort())
        }
    }

    fun getParser(): TagParser = parser

    fun createDataAccessForParser(directory: Directory) {
        val dataAccess = directory.create("max_speed_storage_tmp").create(1000)
        this.dataAccess = dataAccess
        val internalMaxSpeedStorage = createMaxSpeedStorage(dataAccess)
        this.internalMaxSpeedStorage = internalMaxSpeedStorage
        val ruralMaxSpeedEnc = DecimalEncodedValueImpl("tmp_rural", 7, 0.0, 2.0, false, false, true)
        this.ruralMaxSpeedEnc = ruralMaxSpeedEnc
        val urbanMaxSpeedEnc = DecimalEncodedValueImpl("tmp_urban", 7, 0.0, 2.0, false, false, true)
        this.urbanMaxSpeedEnc = urbanMaxSpeedEnc
        val config = EncodedValue.InitializerConfig()
        ruralMaxSpeedEnc.init(config)
        urbanMaxSpeedEnc.init(config)
        if (config.requiredBytes > 2)
            throw IllegalStateException("bytes are not sufficient " + config.requiredBytes)

        parser.init(ruralMaxSpeedEnc, urbanMaxSpeedEnc, internalMaxSpeedStorage)
    }

    /**
     * This method sets max_speed values where the value is [MaxSpeed.MAXSPEED_MISSING] to a
     * value determined by the default speed library which is country-dependent.
     */
    fun fillMaxSpeed(graph: Graph, em: EncodingManager) {
        // In DefaultMaxSpeedParser and in OSMMaxSpeedParser we don't have the rural/urban info,
        // but now we have and can fill the country-dependent max_speed value where missing.
        val udEnc = em.getEnumEncodedValue(UrbanDensity.KEY, UrbanDensity::class.java)
        fillMaxSpeed(graph, em, Function { edge -> edge.get(udEnc) != UrbanDensity.RURAL })
    }

    fun fillMaxSpeed(graph: Graph, em: EncodingManager, isUrbanDensityFun: Function<EdgeIteratorState, Boolean>) {
        val maxSpeedEnc = em.getDecimalEncodedValue(MaxSpeed.KEY)
        val maxSpeedEstEnc = em.getBooleanEncodedValue(MaxSpeedEstimated.KEY)
        val urbanMaxSpeedEnc = this.urbanMaxSpeedEnc!!
        val ruralMaxSpeedEnc = this.ruralMaxSpeedEnc!!
        val internalMaxSpeedStorage = this.internalMaxSpeedStorage!!

        val sw = StopWatch().start()
        val iter = graph.allEdges
        while (iter.next()) {
            val fwdMaxSpeedPureOSM = iter.get(maxSpeedEnc)
            val bwdMaxSpeedPureOSM = iter.getReverse(maxSpeedEnc)

            // skip speeds-library if max_speed is known for both directions
            if (fwdMaxSpeedPureOSM != MaxSpeed.MAXSPEED_MISSING
                && bwdMaxSpeedPureOSM != MaxSpeed.MAXSPEED_MISSING
            ) continue

            val maxSpeed = if (isUrbanDensityFun.apply(iter))
                urbanMaxSpeedEnc.getDecimal(false, iter.edge, internalMaxSpeedStorage)
            else
                ruralMaxSpeedEnc.getDecimal(false, iter.edge, internalMaxSpeedStorage)
            if (maxSpeed != MaxSpeed.MAXSPEED_MISSING) {
                if (maxSpeed == 0.0) {
                    // TODO fix properly: RestrictionSetter adds artificial edges for which
                    //  we didn't set the speed in DefaultMaxSpeedParser, #2914
                    iter.set(maxSpeedEnc, MaxSpeed.MAXSPEED_MISSING, MaxSpeed.MAXSPEED_MISSING)
                } else {
                    iter.set(maxSpeedEnc,
                        if (fwdMaxSpeedPureOSM == MaxSpeed.MAXSPEED_MISSING) maxSpeed else fwdMaxSpeedPureOSM,
                        if (bwdMaxSpeedPureOSM == MaxSpeed.MAXSPEED_MISSING) maxSpeed else bwdMaxSpeedPureOSM)
                    iter.set(maxSpeedEstEnc, true)
                }
            }
        }

        LoggerFactory.getLogger(javaClass).info("max_speed_calculator took: " + sw.stop().getSeconds())
    }

    fun close() {
        dataAccess!!.close()
    }

    fun checkEncodedValues(encodingManager: EncodingManager) {
        if (!encodingManager.hasEncodedValue(Country.KEY))
            throw IllegalArgumentException("max_speed_calculator needs country")
        if (!encodingManager.hasEncodedValue(UrbanDensity.KEY))
            throw IllegalArgumentException("max_speed_calculator needs urban_density")
    }

    internal class SpeedLimitsJson {
        @JsonProperty
        var meta: Map<String, String>? = null

        @JsonProperty
        var roadTypesByName: Map<String, RoadTypeFilterImpl>? = null

        @JsonProperty
        var speedLimitsByCountryCode: Map<String, List<RoadTypeImpl>>? = null

        @JsonProperty
        var warnings: List<String>? = null
    }

    internal class RoadTypeFilterImpl : RoadTypeFilter {
        override var filter: String? = null
        override var fuzzyFilter: String? = null
        override var relationFilter: String? = null
    }

    internal class RoadTypeImpl : RoadType {
        override var name: String? = null
        override var tags: Map<String, String> = emptyMap()
    }

    companion object {
        @JvmStatic
        fun createLegalDefaultSpeeds(): LegalDefaultSpeeds {
            val data: SpeedLimitsJson
            try {
                data = ObjectMapper().readValue(MaxSpeedCalculator::class.java.getResource("legal_default_speeds.json"), SpeedLimitsJson::class.java)
            } catch (e: IOException) {
                throw RuntimeException(e)
            }

            // pre-converts kmh, mph and "walk" into kmh
            convertMaxspeed(data.speedLimitsByCountryCode!!.entries)

            return LegalDefaultSpeeds(data.roadTypesByName!!, data.speedLimitsByCountryCode!!)
        }

        private fun convertMaxspeed(entrySet: Set<Map.Entry<String, List<RoadTypeImpl>>>) {
            for (entry in entrySet) {
                for (roadType in entry.value) {
                    val newTags = HashMap<String, String>(roadType.tags.size)
                    for (tags in roadType.tags.entries) {
                        // note, we could remove conditional tags here to reduce load a bit at import

                        if ("maxspeed" == tags.key || "maxspeed:advisory" == tags.key) {
                            val tmp = OSMMaxSpeedParser.parseMaxspeedString(tags.value)
                            if (tmp == MaxSpeed.MAXSPEED_MISSING || tmp == OSMMaxSpeedParser.MAXSPEED_NONE)
                                throw IllegalStateException("illegal maxspeed " + tags.value)
                            newTags[tags.key] = "" + Math.round(tmp)
                        }
                    }
                    roadType.tags = newTags
                }
            }
        }
    }
}
