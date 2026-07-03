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
package com.graphhopper.routing.subnetwork

import com.graphhopper.storage.DataAccess

/**
 * This class handles storage of subnetwork ids for every node. Useful to pick the correct set of
 * landmarks or fail fast for routing when two nodes are from different subnetworks.
 *
 * @author Peter Karich
 */
class SubnetworkStorage(private val da: DataAccess) {
    /**
     * Returns the subnetwork ID for the specified nodeId or 0 if non is associated e.g. because the
     * subnetwork is too small.
     */
    fun getSubnetwork(nodeId: Int): Int = da.getByte(nodeId.toLong()).toInt()

    /**
     * This method sets the subnetwork if of the specified nodeId. Default is 0 and means subnetwork
     * was too small to be useful to be stored.
     */
    fun setSubnetwork(nodeId: Int, subnetwork: Int) {
        if (subnetwork > 127)
            throw IllegalArgumentException("Number of subnetworks is currently limited to 127 but requested $subnetwork")

        da.setByte(nodeId.toLong(), subnetwork.toByte())
    }

    fun loadExisting(): Boolean = da.loadExisting()

    fun create(byteCount: Long): SubnetworkStorage {
        da.create(2000)
        da.ensureCapacity(byteCount)
        return this
    }

    fun flush() {
        da.flush()
    }

    fun close() {
        da.close()
    }

    val isClosed: Boolean
        get() = da.isClosed

    val capacity: Long
        get() = da.capacity
}
