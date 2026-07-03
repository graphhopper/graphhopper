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

import com.graphhopper.coll.primitive.IntArrayList

open class ArrayEdgeIntAccess(private val intsPerEdge: Int) : EdgeIntAccess {
    private val arr = IntArrayList()

    override fun getInt(edgeId: Int, index: Int): Int {
        val arrIndex = edgeId * intsPerEdge + index
        return if (arrIndex >= arr.size()) 0 else arr.get(arrIndex)
    }

    override fun setInt(edgeId: Int, index: Int, value: Int) {
        val arrIndex = edgeId * intsPerEdge + index
        if (arrIndex >= arr.size())
            arr.resize(arrIndex + 1)
        arr.set(arrIndex, value)
    }

    companion object {
        /**
         * Ensures that the underlying storage has enough integers reserved for the specified bytes.
         */
        @JvmStatic
        fun createFromBytes(bytes: Int): ArrayEdgeIntAccess = ArrayEdgeIntAccess(Math.ceil(bytes / 4.0).toInt())
    }
}
