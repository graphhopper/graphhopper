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

/**
 * Registration point for the process-wide [CustomWeightingBackend]. All public entry points
 * ([CustomModelParser.createWeighting], [CustomModelParser.createWeightingParameters] and thus
 * DefaultWeightingFactory) go through [default].
 *
 * Replacing the default is intended for platforms without runtime classloading (Android/iOS,
 * see [CustomWeightingBackend]) and should happen once at startup, before any weighting is
 * created. The field is volatile so a replacement set during startup is visible to all
 * request threads, but there is deliberately no further synchronization: swapping backends
 * while requests are in flight is not a supported use case.
 */
object CustomWeightingBackends {
    @JvmStatic
    @Volatile
    var default: CustomWeightingBackend = JaninoBackend
}
