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

import com.graphhopper.json.MinMax
import com.graphhopper.json.Statement
import com.graphhopper.json.Statement.Keyword.ELSE
import com.graphhopper.json.Statement.Keyword.IF
import com.graphhopper.routing.ev.EncodedValueLookup
import com.graphhopper.util.CustomModel

object FindMinMax {

    /**
     * This method throws an exception when this CustomModel would decrease the edge weight compared to the specified
     * baseModel as in such a case the optimality of A* with landmarks can no longer be guaranteed (as the preparation
     * is based on baseModel).
     */
    @JvmStatic
    fun checkLMConstraints(baseModel: CustomModel?, queryModel: CustomModel?, lookup: EncodedValueLookup) {
        if (queryModel!!.getDistanceInfluence() != null) {
            val bmDI = baseModel!!.getDistanceInfluence() ?: 0.0
            if (queryModel.getDistanceInfluence() < bmDI)
                throw IllegalArgumentException("CustomModel in query can only use distance_influence bigger or equal to "
                        + bmDI + ", but was: " + queryModel.getDistanceInfluence())
        }

        checkMultiplyValue(queryModel.getPriority(), lookup)
        checkMultiplyValue(queryModel.getSpeed(), lookup)
    }

    private fun checkMultiplyValue(list: List<Statement>, lookup: EncodedValueLookup) {
        for (statement in list) {
            if (statement.operation() == Statement.Op.MULTIPLY) {
                val minMax = ValueExpressionVisitor.findMinMax(statement.value(), lookup)
                if (minMax.max > 1)
                    throw IllegalArgumentException("maximum of value '" + statement.value() + "' cannot be larger than 1, but was: " + minMax.max)
                else if (minMax.min < 0)
                    throw IllegalArgumentException("minimum of value '" + statement.value() + "' cannot be smaller than 0, but was: " + minMax.min)
            }
        }
    }

    /**
     * This method returns the smallest value possible in "min" and the smallest value that cannot be
     * exceeded by any edge in max.
     */
    @JvmStatic
    @JvmName("findMinMax")
    internal fun findMinMax(minMax: MinMax, statements: List<Statement>, lookup: EncodedValueLookup): MinMax {
        val groups = CustomModelParser.splitIntoGroup(statements)
        for (group in groups) findMinMaxForGroup(minMax, group, lookup)
        return minMax
    }

    private fun findMinMaxForGroup(minMax: MinMax, group: List<Statement>, lookup: EncodedValueLookup) {
        if (group.isEmpty() || IF != group[0].keyword())
            throw IllegalArgumentException("Every group must start with an if-statement")

        val minMaxGroup: MinMax
        val first = group[0]
        if (first.condition().trim() == "true") {
            if (first.isBlock()) {
                for (subGroup in CustomModelParser.splitIntoGroup(first.doBlock())) findMinMaxForGroup(minMax, subGroup, lookup)
                return
            } else {
                minMaxGroup = first.operation().apply(minMax, ValueExpressionVisitor.findMinMax(first.value(), lookup))
                if (minMaxGroup.max < 0)
                    throw IllegalArgumentException("statement resulted in negative value: " + first)
            }
        } else {
            minMaxGroup = MinMax(Double.MAX_VALUE, 0.0)
            var foundElse = false
            for (s in group) {
                if (s.keyword() == ELSE) foundElse = true
                val tmp: MinMax
                if (s.isBlock()) {
                    tmp = MinMax(minMax.min, minMax.max)
                    for (subGroup in CustomModelParser.splitIntoGroup(s.doBlock())) findMinMaxForGroup(tmp, subGroup, lookup)
                } else {
                    tmp = s.operation().apply(minMax, ValueExpressionVisitor.findMinMax(s.value(), lookup))
                    if (tmp.max < 0)
                        throw IllegalArgumentException("statement resulted in negative value: " + s)
                }
                minMaxGroup.min = Math.min(minMaxGroup.min, tmp.min)
                minMaxGroup.max = Math.max(minMaxGroup.max, tmp.max)
            }

            // if there is no 'else' statement it's like there is a 'neutral' branch that leaves the initial value as is
            if (!foundElse) {
                minMaxGroup.min = Math.min(minMaxGroup.min, minMax.min)
                minMaxGroup.max = Math.max(minMaxGroup.max, minMax.max)
            }
        }

        minMax.min = minMaxGroup.min
        minMax.max = minMaxGroup.max
    }
}
