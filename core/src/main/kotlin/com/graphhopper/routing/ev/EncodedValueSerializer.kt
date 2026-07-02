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

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies

object EncodedValueSerializer {
    private val MAPPER = ObjectMapper().apply {
        setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
        setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
        setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
    }

    @JvmStatic
    fun serializeEncodedValue(encodedValue: EncodedValue): String {
        try {
            val tree: JsonNode = MAPPER.valueToTree(encodedValue)
            return MAPPER.writeValueAsString(tree)
        } catch (e: JsonProcessingException) {
            throw IllegalStateException("Could not serialize encoded value: $encodedValue, error: ${e.message}")
        }
    }

    @JvmStatic
    fun deserializeEncodedValue(serializedEncodedValue: String): EncodedValue {
        try {
            val jsonNode = MAPPER.readTree(serializedEncodedValue)
            return MAPPER.treeToValue(jsonNode, EncodedValue::class.java)
        } catch (e: JsonProcessingException) {
            throw IllegalStateException("Could not deserialize encoded value: $serializedEncodedValue, error: ${e.message}")
        }
    }
}
