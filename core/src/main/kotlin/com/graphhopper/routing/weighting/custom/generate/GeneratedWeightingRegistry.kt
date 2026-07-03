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
package com.graphhopper.routing.weighting.custom.generate

import com.graphhopper.routing.ev.EncodedValueLookup
import com.graphhopper.routing.weighting.custom.ClosureBackend
import com.graphhopper.routing.weighting.custom.CustomWeighting
import com.graphhopper.routing.weighting.custom.CustomWeightingBackend
import com.graphhopper.routing.weighting.custom.CustomWeightingBackends
import com.graphhopper.routing.weighting.custom.CustomWeightingHelper
import com.graphhopper.util.CustomModel
import com.graphhopper.util.Parameters as GHParameters
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier

/**
 * Registry of build-time generated [CustomWeightingHelper] classes (stage 5, see
 * [CustomWeightingSourceGenerator]). Keyed by the custom model's IDENTITY —
 * `CustomModel.toString()`, the same key the Janino back-end's class cache uses — so no
 * profile name is needed and the registry matches the [CustomWeightingBackend] seam's shape.
 *
 * Apps register their generated classes once at startup (before any weighting is created),
 * either with the [CustomModel] instance they load anyway for their profile configuration,
 * or with the key string captured at generation time (the `registerAll` snippet written by
 * [GenerateCustomWeightingMain] does the latter — valid as long as the model JSON is
 * byte-compatible with the one used at build time):
 *
 * ```
 * GeneratedWeightingRegistry.register(customModel, Supplier { GeneratedCarCustomWeighting() })
 * CustomWeightingBackends.setDefault(RegistryBackend)
 * ```
 */
object GeneratedWeightingRegistry {

    private val factories = ConcurrentHashMap<String, Supplier<CustomWeightingHelper>>()

    /** Registers a generated class for the given custom model (keyed by its `toString()`). */
    @JvmStatic
    fun register(customModel: CustomModel, factory: Supplier<CustomWeightingHelper>) {
        register(customModel.toString(), factory)
    }

    /** Registers by the raw key (`CustomModel.toString()` captured at generation time). */
    @JvmStatic
    fun register(key: String, factory: Supplier<CustomWeightingHelper>) {
        factories[key] = factory
    }

    @JvmStatic
    fun clear() {
        factories.clear()
    }

    @JvmStatic
    fun size(): Int = factories.size

    internal fun factoryFor(key: String): Supplier<CustomWeightingHelper>? = factories[key]
}

/**
 * The pre-generated-class [CustomWeightingBackend] (stage 5): looks the custom model up in
 * the [GeneratedWeightingRegistry] by its identity and instantiates the generated
 * [CustomWeightingHelper] — NO runtime codegen, NO Janino, full AOT speed. The
 * per-request instance + init(...) flow mirrors `createJaninoWeightingParameters` 1:1; the
 * max-speed/max-priority calculators are the Janino-free ones shared with [ClosureBackend]
 * (bit-identical to the Janino path, locked by ClosureBackendDifferentialTest).
 *
 * This backend is NOT the production default (Janino stays); select it explicitly via
 * [CustomWeightingBackends.default].
 */
object RegistryBackend : CustomWeightingBackend {

    override fun createParameters(customModel: CustomModel, lookup: EncodedValueLookup): CustomWeighting.Parameters {
        val key = customModel.toString()
        val factory = GeneratedWeightingRegistry.factoryFor(key)
                ?: throw IllegalArgumentException(
                        "No generated custom weighting registered for this custom model " +
                                "(" + GeneratedWeightingRegistry.size() + " registered). Generate a class with " +
                                "GenerateCustomWeightingMain at build time and register it at startup via " +
                                "GeneratedWeightingRegistry.register(customModel, factory). Lookup key: " + key)
        // like Janino's helper: one instance per request, no thread-safety required
        val helper = factory.get()
        helper.init(customModel, lookup, CustomModel.getAreasAsMap(customModel.getAreas()))
        return CustomWeighting.Parameters(
                helper::getSpeed, { ClosureBackend.calcMaxSpeed(customModel, lookup) },
                helper::getPriority, { ClosureBackend.calcMaxPriority(customModel, lookup) },
                helper::getTurnPenalty,
                customModel.getDistanceInfluence() ?: 0.0,
                customModel.getHeadingPenalty() ?: GHParameters.Routing.DEFAULT_HEADING_PENALTY)
    }
}
