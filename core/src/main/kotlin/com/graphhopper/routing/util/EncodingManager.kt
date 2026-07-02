/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.util

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.node.ArrayNode
import com.graphhopper.jackson.Jackson
import com.graphhopper.routing.ev.BooleanEncodedValue
import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.routing.ev.EncodedValue
import com.graphhopper.routing.ev.EncodedValueLookup
import com.graphhopper.routing.ev.EncodedValueSerializer
import com.graphhopper.routing.ev.EnumEncodedValue
import com.graphhopper.routing.ev.IntEncodedValue
import com.graphhopper.routing.ev.StringEncodedValue
import com.graphhopper.routing.ev.VehicleSpeed
import com.graphhopper.storage.IntsRef
import com.graphhopper.storage.StorableProperties
import com.graphhopper.util.Constants
import java.io.UncheckedIOException
import java.util.Collections

/**
 * Manager class to register encoder, assign their flag values and check objects with all encoders
 * during parsing. Create one via:
 *
 * EncodingManager.start(4).add(new CarFlagEncoder()).build();
 *
 * @author Peter Karich
 * @author Nop
 */
class EncodingManager(
    bytesForFlags: Int,
    intsForTurnCostFlags: Int,
    internal val encodedValueMap: LinkedHashMap<String, EncodedValue>,
    internal val turnEncodedValueMap: LinkedHashMap<String, EncodedValue>
) : EncodedValueLookup {

    var bytesForFlags: Int = bytesForFlags
        internal set
    internal var intsForTurnCostFlags: Int = intsForTurnCostFlags

    class Builder {
        private val edgeConfig = EncodedValue.InitializerConfig()
        private val turnCostConfig = EncodedValue.InitializerConfig()
        private var em: EncodingManager? = EncodingManager(0, 0, LinkedHashMap(), LinkedHashMap())

        fun add(encodedValue: EncodedValue): Builder {
            val em = checkNotBuiltAlready()
            if (em.hasEncodedValue(encodedValue.name))
                throw IllegalArgumentException("EncodedValue already exists: " + encodedValue.name)
            if (em.hasTurnEncodedValue(encodedValue.name))
                throw IllegalArgumentException("Already defined as 'turn'-EncodedValue: " + encodedValue.name)
            encodedValue.init(edgeConfig)
            em.encodedValueMap[encodedValue.name] = encodedValue
            return this
        }

        fun addTurnCostEncodedValue(turnCostEnc: EncodedValue): Builder {
            val em = checkNotBuiltAlready()
            if (em.hasTurnEncodedValue(turnCostEnc.name))
                throw IllegalArgumentException("Already defined: " + turnCostEnc.name)
            if (em.hasEncodedValue(turnCostEnc.name))
                throw IllegalArgumentException("Already defined as EncodedValue: " + turnCostEnc.name)
            turnCostEnc.init(turnCostConfig)
            em.turnEncodedValueMap[turnCostEnc.name] = turnCostEnc
            return this
        }

        private fun checkNotBuiltAlready(): EncodingManager =
            em ?: throw IllegalStateException("Cannot call method after Builder.build() was called")

        fun build(): EncodingManager {
            val result = checkNotBuiltAlready()
            result.bytesForFlags = edgeConfig.requiredBytes
            result.intsForTurnCostFlags = turnCostConfig.requiredInts
            em = null
            return result
        }
    }

    override fun hasEncodedValue(key: String): Boolean = encodedValueMap[key] != null

    fun hasTurnEncodedValue(key: String): Boolean = turnEncodedValueMap[key] != null

    /**
     * @return list of all prefixes of xy_access and xy_average_speed encoded values.
     */
    fun getVehicles(): List<String> = encodedValues
        .filter { it.name.endsWith("_access") }
        .map { it.name.replace("_access", "") }
        .filter { v -> encodedValues.any { it.name.contains(VehicleSpeed.key(v)) } }

    fun toEncodedValuesAsString(): String {
        val serializedEVsList = encodedValueMap.values.map { EncodedValueSerializer.serializeEncodedValue(it) }
        try {
            return Jackson.newObjectMapper().writeValueAsString(serializedEVsList)
        } catch (e: JsonProcessingException) {
            throw UncheckedIOException(e)
        }
    }

    override fun toString(): String = getVehicles().joinToString(",")

    // TODO hide IntsRef even more in a later version: https://gist.github.com/karussell/f4c2b2b1191be978d7ee9ec8dd2cd48f
    fun createEdgeFlags(): IntsRef = IntsRef(Math.ceil(bytesForFlags.toDouble() / 4).toInt())

    fun createRelationFlags(): IntsRef {
        // for backward compatibility use 2 ints
        return IntsRef(2)
    }

    fun needsTurnCostsSupport(): Boolean = intsForTurnCostFlags > 0

    override val encodedValues: List<EncodedValue>
        get() = Collections.unmodifiableList(ArrayList(encodedValueMap.values))

    override fun getBooleanEncodedValue(key: String): BooleanEncodedValue =
        getEncodedValue(key, BooleanEncodedValue::class.java)

    override fun getIntEncodedValue(key: String): IntEncodedValue =
        getEncodedValue(key, IntEncodedValue::class.java)

    override fun getDecimalEncodedValue(key: String): DecimalEncodedValue =
        getEncodedValue(key, DecimalEncodedValue::class.java)

    @Suppress("UNCHECKED_CAST")
    override fun <T : Enum<*>> getEnumEncodedValue(key: String, enumType: Class<T>): EnumEncodedValue<T> =
        getEncodedValue(key, EnumEncodedValue::class.java) as EnumEncodedValue<T>

    override fun getStringEncodedValue(key: String): StringEncodedValue =
        getEncodedValue(key, StringEncodedValue::class.java)

    @Suppress("UNCHECKED_CAST")
    override fun <T : EncodedValue> getEncodedValue(key: String, encodedValueType: Class<T>): T {
        val ev = encodedValueMap[key]
        // todo: why do we not just return null when EV is missing? just like java.util.Map? -> https://github.com/graphhopper/graphhopper/pull/2561#discussion_r859770067
            ?: throw IllegalArgumentException("Cannot find EncodedValue '$key' in collection: ${encodedValueMap.keys}")
        return ev as T
    }

    fun getTurnEncodedValues(): List<EncodedValue> =
        Collections.unmodifiableList(ArrayList(turnEncodedValueMap.values))

    fun getTurnDecimalEncodedValue(key: String): DecimalEncodedValue =
        getTurnEncodedValue(key, DecimalEncodedValue::class.java)

    fun getTurnBooleanEncodedValue(key: String): BooleanEncodedValue =
        getTurnEncodedValue(key, BooleanEncodedValue::class.java)

    @Suppress("UNCHECKED_CAST")
    fun <T : EncodedValue> getTurnEncodedValue(key: String, encodedValueType: Class<T>): T {
        val ev = turnEncodedValueMap[key]
        // todo: why do we not just return null when EV is missing? just like java.util.Map? -> https://github.com/graphhopper/graphhopper/pull/2561#discussion_r859770067
            ?: throw IllegalArgumentException("Cannot find Turn-EncodedValue $key in collection: ${turnEncodedValueMap.keys}")
        return ev as T
    }

    private fun toTurnEncodedValuesAsString(): String {
        val serializedEVsList = turnEncodedValueMap.values.map { EncodedValueSerializer.serializeEncodedValue(it) }
        try {
            return Jackson.newObjectMapper().writeValueAsString(serializedEVsList)
        } catch (e: JsonProcessingException) {
            throw UncheckedIOException(e)
        }
    }

    companion object {
        @JvmStatic
        fun putEncodingManagerIntoProperties(encodingManager: EncodingManager, properties: StorableProperties) {
            properties.put("graph.em.version", Constants.VERSION_EM)
            properties.put("graph.em.bytes_for_flags", encodingManager.bytesForFlags)
            properties.put("graph.em.ints_for_turn_cost_flags", encodingManager.intsForTurnCostFlags)
            properties.put("graph.encoded_values", encodingManager.toEncodedValuesAsString())
            properties.put("graph.turn_encoded_values", encodingManager.toTurnEncodedValuesAsString())
        }

        @JvmStatic
        fun fromProperties(properties: StorableProperties): EncodingManager {
            if (properties.containsVersion())
                throw IllegalStateException("The GraphHopper file format is not compatible with the data you are " +
                        "trying to load. You either need to use an older version of GraphHopper or run a new import")

            val versionStr = properties.get("graph.em.version")
            if (versionStr.isEmpty() || Constants.VERSION_EM.toString() != versionStr)
                throw IllegalStateException("Incompatible encoding version. You need to use the same GraphHopper version you used to import the graph" +
                        " in '" + properties.directory.location + "', delete the folder, or run a new import with another location. "
                        + " Stored encoding version: " + (if (versionStr.isEmpty()) "missing" else versionStr) + ", used encoding version: " + Constants.VERSION_EM)
            val encodedValueStr = properties.get("graph.encoded_values")
            val evList = deserializeEncodedValueList(encodedValueStr)
            val encodedValues = LinkedHashMap<String, EncodedValue>()
            evList.forEach { serializedEV ->
                val encodedValue = EncodedValueSerializer.deserializeEncodedValue(serializedEV.textValue())
                if (encodedValues.put(encodedValue.name, encodedValue) != null)
                    throw IllegalStateException("Duplicate encoded value name: " + encodedValue.name + " in: graph.encoded_values=" + encodedValueStr)
            }

            val turnEncodedValueStr = properties.get("graph.turn_encoded_values")
            val tevList = deserializeEncodedValueList(turnEncodedValueStr)
            val turnEncodedValues = LinkedHashMap<String, EncodedValue>()
            tevList.forEach { serializedEV ->
                val encodedValue = EncodedValueSerializer.deserializeEncodedValue(serializedEV.textValue())
                if (turnEncodedValues.put(encodedValue.name, encodedValue) != null)
                    throw IllegalStateException("Duplicate turn encoded value name: " + encodedValue.name + " in: graph.turn_encoded_values=" + turnEncodedValueStr)
            }

            return EncodingManager(getIntegerProperty(properties, "graph.em.bytes_for_flags"),
                getIntegerProperty(properties, "graph.em.ints_for_turn_cost_flags"), encodedValues,
                turnEncodedValues
            )
        }

        private fun getIntegerProperty(properties: StorableProperties, key: String): Int {
            val str = properties.get(key)
            if (str.isEmpty())
                throw IllegalStateException("Missing EncodingManager property: '$key'")
            return Integer.parseInt(str)
        }

        private fun deserializeEncodedValueList(encodedValueStr: String): ArrayNode {
            try {
                return Jackson.newObjectMapper().readValue(encodedValueStr, ArrayNode::class.java)
            } catch (e: JsonProcessingException) {
                throw UncheckedIOException(e)
            }
        }

        /**
         * Starts the build process of an EncodingManager
         */
        @JvmStatic
        fun start(): Builder = Builder()

        @JvmStatic
        fun getKey(prefix: String, str: String): String = prefix + "_" + str
    }
}
