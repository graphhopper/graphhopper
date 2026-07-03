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
package com.graphhopper.routing.weighting.custom.expression

import com.graphhopper.routing.ev.BooleanEncodedValue
import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.routing.ev.EncodedValue
import com.graphhopper.routing.ev.EncodedValueLookup
import com.graphhopper.routing.ev.EnumEncodedValue
import com.graphhopper.routing.ev.IntEncodedValue
import com.graphhopper.routing.ev.StringEncodedValue

/**
 * Builds [ExpressionScope]s from an [EncodedValueLookup]. This is the only file of the
 * expression package that touches JVM-specific reflection (enum constants of an
 * [EnumEncodedValue]); everything else stays KMP-clean. In a future multiplatform split
 * this factory becomes the JVM `actual`.
 */
object ExpressionScopes {

    /**
     * Scope for if/else_if conditions; the name whitelist mirrors
     * CustomModelParser's `nameInConditionValidator` 1:1.
     */
    @JvmStatic
    @JvmOverloads
    fun conditionScope(lookup: EncodedValueLookup, areaIds: Set<String> = emptySet()): ExpressionScope =
            ExpressionScope(
                    isValidName = { name ->
                        lookup.hasEncodedValue(name)
                                || name.uppercase() == name
                                || name.startsWith(ExpressionScope.IN_AREA_PREFIX)
                                || name == ExpressionScope.CHANGE_ANGLE
                                || name == ExpressionScope.STREET_NAME
                                || name == ExpressionScope.PREV_PREFIX + ExpressionScope.STREET_NAME
                                || (name.startsWith(ExpressionScope.BACKWARD_PREFIX)
                                && lookup.hasEncodedValue(name.substring(ExpressionScope.BACKWARD_PREFIX.length)))
                                || (name.startsWith(ExpressionScope.PREV_PREFIX)
                                && lookup.hasEncodedValue(name.substring(ExpressionScope.PREV_PREFIX.length)))
                    },
                    encodedValue = { name -> toMeta(lookup, name) },
                    areaIds = areaIds)

    /**
     * Scope for limit_to/multiply_by/add value expressions; the name whitelist mirrors
     * the validator of ValueExpressionVisitor.findVariables 1:1.
     */
    @JvmStatic
    fun valueScope(lookup: EncodedValueLookup): ExpressionScope =
            ExpressionScope(
                    isValidName = { name -> lookup.hasEncodedValue(name) || name.contains("Infinity") },
                    encodedValue = { name -> toMeta(lookup, name) })

    private fun toMeta(lookup: EncodedValueLookup, name: String): EvMeta? {
        if (!lookup.hasEncodedValue(name)) return null
        val enc = lookup.getEncodedValue(name, EncodedValue::class.java)
        // order matters: EnumEncodedValue, StringEncodedValue and BooleanEncodedValue
        // implementations also implement IntEncodedValue
        return when (enc) {
            is EnumEncodedValue<*> -> EvMeta(EvKind.ENUMERATION,
                    enc.enumType.enumConstants.mapTo(LinkedHashSet()) { (it as Enum<*>).name })
            is StringEncodedValue -> EvMeta(EvKind.STRING)
            is BooleanEncodedValue -> EvMeta(EvKind.BOOLEAN)
            is DecimalEncodedValue -> EvMeta(EvKind.NUMBER,
                    minValue = enc.minStorableDecimal, maxValue = enc.maxOrMaxStorableDecimal)
            is IntEncodedValue -> EvMeta(EvKind.NUMBER,
                    minValue = enc.minStorableInt.toDouble(), maxValue = enc.maxOrMaxStorableInt.toDouble())
            else -> EvMeta(EvKind.STRING) // unknown kind: treat as non-numeric, non-enum
        }
    }
}
