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
package com.graphhopper.routing.util.parsers

import com.graphhopper.reader.ReaderNode
import com.graphhopper.reader.ReaderWay
import com.graphhopper.routing.ev.BooleanEncodedValue
import com.graphhopper.routing.ev.EdgeIntAccess
import com.graphhopper.storage.IntsRef

abstract class AbstractAccessParser protected constructor(
    // order is important
    @JvmField protected val accessEnc: BooleanEncodedValue,
    @JvmField protected val restrictionKeys: List<String>
) : TagParser {

    @JvmField
    protected val restrictedValues: MutableSet<String> = HashSet(5)

    @JvmField
    protected val allowedValues: MutableSet<String> = HashSet(INTENDED)

    // http://wiki.openstreetmap.org/wiki/Mapfeatures#Barrier
    @JvmField
    protected val barriers: MutableSet<String> = HashSet(5)

    init {
        allowedValues.add("destination")

        restrictedValues.add("no")
        restrictedValues.add("restricted")
        restrictedValues.add("military")
        restrictedValues.add("emergency")
        restrictedValues.add("unknown")

        restrictedValues.add("private")
        restrictedValues.add("service")
        restrictedValues.add("permit")
    }

    protected fun blockPrivate(blockPrivate: Boolean) {
        if (!blockPrivate) {
            if (!restrictedValues.remove("private") || !restrictedValues.remove("permit") || !restrictedValues.remove("service"))
                throw IllegalStateException("no 'private', 'permit' or 'service' value found in restrictedValues")
            allowedValues.add("private")
            allowedValues.add("permit")
            allowedValues.add("service")
        }
    }

    protected fun handleBarrierEdge(edgeId: Int, edgeIntAccess: EdgeIntAccess, nodeTags: Map<String, Any>) {
        // for now we just create a dummy reader node, because our encoders do not make use of the coordinates anyway
        val readerNode = ReaderNode(0, 0.0, 0.0, nodeTags)
        // block access for barriers
        if (isBarrier(readerNode)) {
            accessEnc.setBool(false, edgeId, edgeIntAccess, false)
            accessEnc.setBool(true, edgeId, edgeIntAccess, false)
        }
    }

    override fun handleWayTags(edgeId: Int, edgeIntAccess: EdgeIntAccess, way: ReaderWay, relationFlags: IntsRef?) {
        handleWayTags(edgeId, edgeIntAccess, way)
    }

    abstract fun handleWayTags(edgeId: Int, edgeIntAccess: EdgeIntAccess, way: ReaderWay)

    /**
     * @return true if the given OSM node blocks access for the specified restrictions, false otherwise
     */
    open fun isBarrier(node: ReaderNode): Boolean {
        // note that this method will be only called for certain nodes as defined by OSMReader!
        val firstValue = node.getFirstValue(restrictionKeys)

        return if (restrictedValues.contains(firstValue))
            true
        else if (node.hasTag("locked", "yes") && !allowedValues.contains(firstValue))
            true
        else if (allowedValues.contains(firstValue))
            false
        else
            node.hasTag("barrier", barriers)
    }

    fun getAccessEnc(): BooleanEncodedValue = accessEnc

    fun getRestrictionKeys(): List<String> = restrictionKeys

    fun getName(): String = accessEnc.name

    override fun toString(): String = getName()

    companion object {
        @JvmField
        internal val ONEWAYS: Collection<String> = listOf("yes", "true", "1", "-1")

        @JvmField
        internal val INTENDED: Collection<String> = listOf("yes", "designated", "official", "permissive")
    }
}
