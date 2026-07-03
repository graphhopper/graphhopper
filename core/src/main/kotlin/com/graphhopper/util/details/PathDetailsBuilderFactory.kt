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
package com.graphhopper.util.details

import com.graphhopper.routing.Path
import com.graphhopper.routing.ev.BooleanEncodedValue
import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.routing.ev.EncodedValue
import com.graphhopper.routing.ev.EncodedValueLookup
import com.graphhopper.routing.ev.EnumEncodedValue
import com.graphhopper.routing.ev.IntEncodedValue
import com.graphhopper.routing.ev.Orientation
import com.graphhopper.routing.ev.StringEncodedValue
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.storage.Graph
import com.graphhopper.util.Parameters.Details.AVERAGE_SPEED
import com.graphhopper.util.Parameters.Details.CHANGE_ANGLE
import com.graphhopper.util.Parameters.Details.DISTANCE
import com.graphhopper.util.Parameters.Details.EDGE_ID
import com.graphhopper.util.Parameters.Details.EDGE_KEY
import com.graphhopper.util.Parameters.Details.INTERSECTION
import com.graphhopper.util.Parameters.Details.LEG_DISTANCE
import com.graphhopper.util.Parameters.Details.LEG_TIME
import com.graphhopper.util.Parameters.Details.LEG_WEIGHT
import com.graphhopper.util.Parameters.Details.MOTORWAY_JUNCTION
import com.graphhopper.util.Parameters.Details.STREET_DESTINATION
import com.graphhopper.util.Parameters.Details.STREET_NAME
import com.graphhopper.util.Parameters.Details.STREET_REF
import com.graphhopper.util.Parameters.Details.TIME
import com.graphhopper.util.Parameters.Details.WEIGHT

/**
 * Generates a list of PathDetailsBuilder from a List of PathDetail names
 *
 * @author Robin Boldt
 */
open class PathDetailsBuilderFactory {

    open fun createPathDetailsBuilders(requestedPathDetails: List<String>, path: Path, evl: EncodedValueLookup,
                                       weighting: Weighting, graph: Graph): List<PathDetailsBuilder> {
        val builders = ArrayList<PathDetailsBuilder>()

        if (requestedPathDetails.contains(LEG_TIME))
            builders.add(ConstantDetailsBuilder(LEG_TIME, path.getTime()))
        if (requestedPathDetails.contains(LEG_DISTANCE))
            builders.add(ConstantDetailsBuilder(LEG_DISTANCE, path.getDistance()))
        if (requestedPathDetails.contains(LEG_WEIGHT))
            builders.add(ConstantDetailsBuilder(LEG_WEIGHT, path.getWeight()))
        if (requestedPathDetails.contains(CHANGE_ANGLE))
            builders.add(ChangeAngleDetails(evl.getDecimalEncodedValue(Orientation.KEY)))

        for (key in requestedPathDetails) {
            if (key.endsWith("_conditional"))
                builders.add(KVStringDetails(key))
        }

        if (requestedPathDetails.contains(MOTORWAY_JUNCTION))
            builders.add(KVStringDetails(MOTORWAY_JUNCTION))
        if (requestedPathDetails.contains(STREET_NAME))
            builders.add(KVStringDetails(STREET_NAME))
        if (requestedPathDetails.contains(STREET_REF))
            builders.add(KVStringDetails(STREET_REF))
        if (requestedPathDetails.contains(STREET_DESTINATION))
            builders.add(KVStringDetails(STREET_DESTINATION))

        if (requestedPathDetails.contains(AVERAGE_SPEED))
            builders.add(AverageSpeedDetails(weighting))

        if (requestedPathDetails.contains(EDGE_ID))
            builders.add(EdgeIdDetails())

        if (requestedPathDetails.contains(EDGE_KEY))
            builders.add(EdgeKeyDetails())

        if (requestedPathDetails.contains(TIME))
            builders.add(TimeDetails(weighting))

        if (requestedPathDetails.contains(WEIGHT))
            builders.add(WeightDetails(weighting))

        if (requestedPathDetails.contains(DISTANCE))
            builders.add(DistanceDetails())

        if (requestedPathDetails.contains(INTERSECTION))
            builders.add(IntersectionDetails(graph, weighting))

        for (pathDetail in requestedPathDetails) {
            if (!evl.hasEncodedValue(pathDetail))
                continue // path details like "time" won't be found

            val ev = evl.getEncodedValue(pathDetail, EncodedValue::class.java)
            when (ev) {
                is DecimalEncodedValue -> builders.add(DecimalDetails(pathDetail, ev))
                is BooleanEncodedValue -> builders.add(BooleanDetails(pathDetail, ev))
                is EnumEncodedValue<*> -> builders.add(EnumDetails(pathDetail, ev))
                is StringEncodedValue -> builders.add(StringDetails(pathDetail, ev))
                is IntEncodedValue -> builders.add(IntDetails(pathDetail, ev))
                else -> throw IllegalArgumentException("unknown EncodedValue class " + ev.javaClass.name)
            }
        }

        if (requestedPathDetails.size > builders.size) {
            val clonedArr = ArrayList(requestedPathDetails) // avoid changing request parameter
            for (pdb in builders) clonedArr.remove(pdb.name)
            throw IllegalArgumentException("Cannot find the path details: $clonedArr")
        } else if (requestedPathDetails.size < builders.size)
            throw IllegalStateException("It should not happen that there are more path details added $builders than requested $requestedPathDetails")

        return builders
    }
}
