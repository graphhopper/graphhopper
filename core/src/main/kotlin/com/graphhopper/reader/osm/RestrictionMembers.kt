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

package com.graphhopper.reader.osm

import com.carrotsearch.hppc.LongArrayList

class RestrictionMembers private constructor(
    val isViaWay: Boolean,
    val viaOSMNode: Long,
    val fromWays: LongArrayList,
    val viaWays: LongArrayList?,
    val toWays: LongArrayList
) {
    fun getAllWays(): LongArrayList {
        val result = LongArrayList(fromWays.size() + toWays.size() + (if (isViaWay) viaWays!!.size() else 0))
        result.addAll(fromWays)
        if (isViaWay) result.addAll(viaWays!!)
        result.addAll(toWays)
        return result
    }

    companion object {
        @JvmStatic
        fun viaNode(viaOSMNode: Long, fromWays: LongArrayList, toWays: LongArrayList): RestrictionMembers =
            RestrictionMembers(false, viaOSMNode, fromWays, null, toWays)

        @JvmStatic
        fun viaWay(fromWays: LongArrayList, viaWays: LongArrayList, toWays: LongArrayList): RestrictionMembers =
            RestrictionMembers(true, -1, fromWays, viaWays, toWays)
    }
}
