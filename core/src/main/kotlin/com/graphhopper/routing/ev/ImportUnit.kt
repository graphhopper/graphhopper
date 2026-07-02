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
package com.graphhopper.routing.ev

import com.graphhopper.routing.util.parsers.TagParser
import com.graphhopper.util.PMap
import java.util.function.BiFunction
import java.util.function.Function

class ImportUnit private constructor(
        private val name: String,
        val createEncodedValue: Function<PMap, EncodedValue>?,
        val createTagParser: BiFunction<EncodedValueLookup, PMap, TagParser>?,
        val requiredImportUnits: List<String>
) {
    override fun toString(): String = "ImportUnit: $name (requires: $requiredImportUnits)"

    companion object {
        @JvmStatic
        fun create(name: String, createEncodedValue: Function<PMap, EncodedValue>?,
                   createTagParser: BiFunction<EncodedValueLookup, PMap, TagParser>?,
                   vararg requiredImportUnits: String): ImportUnit {
            return ImportUnit(name, createEncodedValue, createTagParser, java.util.List.of(*requiredImportUnits))
        }
    }
}
