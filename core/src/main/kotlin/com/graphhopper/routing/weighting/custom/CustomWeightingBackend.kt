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
package com.graphhopper.routing.weighting.custom

import com.graphhopper.routing.ev.EncodedValueLookup
import com.graphhopper.util.CustomModel

/**
 * Backend seam for turning a [CustomModel] into executable [CustomWeighting.Parameters]
 * (the speed/priority/turn-penalty mappings plus their max calculators).
 *
 * Three backends are intended:
 *
 *  1. **Janino runtime codegen** ([JaninoBackend], the default): generates a Java source
 *     subclass of [CustomWeightingHelper] and compiles + classloads it at runtime. Fastest,
 *     but requires a full JVM with runtime classloading — the server default.
 *  2. **Closure composer** (future, stage 4): builds a function-object DAG once per profile,
 *     no classloading required. For platforms where runtime codegen is impossible
 *     (Android has no runtime DEX generation, iOS is AOT-compiled).
 *  3. **Pre-generated class registry** (future, stage 5): custom models of fixed (mobile)
 *     profiles are translated to Kotlin sources at app build time and looked up by profile
 *     name at runtime — full speed under AOT.
 *
 * The signature deliberately mirrors [CustomModelParser.createWeightingParameters] 1:1: the
 * areas and all other inputs are part of the [CustomModel] itself.
 */
fun interface CustomWeightingBackend {
    fun createParameters(customModel: CustomModel, lookup: EncodedValueLookup): CustomWeighting.Parameters
}
