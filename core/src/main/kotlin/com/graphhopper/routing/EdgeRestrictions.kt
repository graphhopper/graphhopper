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

package com.graphhopper.routing

import com.graphhopper.coll.primitive.IntArrayList
import com.graphhopper.util.EdgeIterator

class EdgeRestrictions {
    private var sourceOutEdge = EdgeIterator.ANY_EDGE
    private var targetInEdge = EdgeIterator.ANY_EDGE
    private val unfavoredEdges: IntArrayList = IntArrayList.from()

    fun getSourceOutEdge(): Int = sourceOutEdge

    fun setSourceOutEdge(sourceOutEdge: Int) {
        this.sourceOutEdge = sourceOutEdge
    }

    fun getTargetInEdge(): Int = targetInEdge

    fun setTargetInEdge(targetInEdge: Int) {
        this.targetInEdge = targetInEdge
    }

    fun getUnfavoredEdges(): IntArrayList = unfavoredEdges
}
