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

import com.graphhopper.coll.primitive.IntArrayList
import com.graphhopper.coll.primitive.LongArrayList
import androidx.collection.MutableLongSet
import com.graphhopper.coll.primitive.IntCursor
import com.graphhopper.reader.ReaderElement
import com.graphhopper.reader.ReaderRelation
import com.graphhopper.reader.osm.RestrictionType.NO
import com.graphhopper.reader.osm.RestrictionType.ONLY
import com.graphhopper.routing.util.parsers.RestrictionSetter
import com.graphhopper.storage.BaseGraph
import com.graphhopper.util.EdgeExplorer
import org.slf4j.LoggerFactory
import java.util.function.LongFunction

object OSMRestrictionConverter {
    private val LOGGER = LoggerFactory.getLogger(OSMRestrictionConverter::class.java)
    private val EMPTY_LONG_ARRAY_LIST = LongArray(0)

    @JvmStatic
    fun isTurnRestriction(relation: ReaderRelation): Boolean =
        "restriction" == relation.getTag("type")

    @JvmStatic
    fun getRestrictedWayIds(relation: ReaderRelation): LongArray {
        if (!isTurnRestriction(relation))
            return EMPTY_LONG_ARRAY_LIST
        return relation.getMembers()
            .filter { it.type == ReaderElement.Type.WAY }
            .filter { "from" == it.role || "via" == it.role || "to" == it.role }
            .map { it.ref }
            .toLongArray()
    }

    @JvmStatic
    fun getViaNodeIfViaNodeRestriction(relation: ReaderRelation): Long =
        relation.getMembers()
            .filter { it.type == ReaderElement.Type.NODE }
            .filter { "via" == it.role }
            .firstOrNull()?.ref ?: -1

    /**
     * OSM restriction relations specify turn restrictions between OSM ways (of course). This method rebuilds the
     * topology of such a relation in the graph representation, where the turn restrictions are specified in terms of edge/node IDs instead
     * of OSM IDs.
     *
     * @throws OSMRestrictionException if the given relation is either not valid in some way and/or cannot be handled and
     *                                 shall be ignored
     */
    @JvmStatic
    @Throws(OSMRestrictionException::class)
    fun buildRestrictionTopologyForGraph(baseGraph: BaseGraph, relation: ReaderRelation, edgesByWay: LongFunction<Iterator<IntCursor>>): Triple<ReaderRelation, RestrictionTopology, RestrictionMembers> {
        if (!isTurnRestriction(relation))
            throw IllegalArgumentException("expected a turn restriction: " + relation.getTags())
        val restrictionMembers = extractMembers(relation)
        if (!membersExist(restrictionMembers, edgesByWay, relation))
            throw OSMRestrictionException.withoutWarning()
        // every OSM way might be split into *multiple* edges, so now we need to figure out which edges are the ones
        // that are actually part of the given relation
        val wayToEdgeConverter = WayToEdgeConverter(baseGraph, edgesByWay)
        if (restrictionMembers.isViaWay) {
            if (containsDuplicateWays(restrictionMembers))
                // For now let's ignore all via-way restrictions with duplicate from/to/via-members
                // until we find cases where this is too strict.
                throw OSMRestrictionException("contains duplicate from-/via-/to-members")
            val res = wayToEdgeConverter
                .convertForViaWays(restrictionMembers.fromWays, restrictionMembers.viaWays!!, restrictionMembers.toWays)
            return Triple(relation, RestrictionTopology.way(res.fromEdges, res.viaEdges, res.toEdges, res.nodes), restrictionMembers)
        } else {
            val viaNode = relation.getTag("graphhopper:via_node", -1)
            if (viaNode < 0)
                throw IllegalStateException("For some reason we did not set graphhopper:via_node for this relation: " + relation.id)
            val res = wayToEdgeConverter
                .convertForViaNode(restrictionMembers.fromWays, viaNode, restrictionMembers.toWays)
            return Triple(relation, RestrictionTopology.node(res.fromEdges, viaNode, res.toEdges), restrictionMembers)
        }
    }

    private fun containsDuplicateWays(restrictionMembers: RestrictionMembers): Boolean {
        val allWays = restrictionMembers.getAllWays()
        val uniqueWays = MutableLongSet(allWays.size())
        for (c in allWays)
            uniqueWays.add(c.value)
        return uniqueWays.size != allWays.size()
    }

    private fun membersExist(members: RestrictionMembers, edgesByWay: LongFunction<Iterator<IntCursor>>, relation: ReaderRelation): Boolean {
        for (c in members.getAllWays())
            if (!edgesByWay.apply(c.value).hasNext()) {
                // this happens for example at the map borders or when certain ways like footways are excluded
                LOGGER.debug("Restriction relation " + relation.id + " uses excluded way " + c.value + ". Relation ignored.")
                return false
            }
        return true
    }

    @JvmStatic
    @Throws(OSMRestrictionException::class)
    fun checkIfTopologyIsCompatibleWithRestriction(g: RestrictionTopology, restriction: String) {
        if (g.fromEdges.size() > 1 && "no_entry" != restriction)
            throw OSMRestrictionException("has multiple members with role 'from' even though it is not a 'no_entry' restriction")
        if (g.toEdges.size() > 1 && "no_exit" != restriction)
            throw OSMRestrictionException("has multiple members with role 'to' even though it is not a 'no_exit' restriction")
    }

    @JvmStatic
    @Throws(OSMRestrictionException::class)
    fun extractMembers(relation: ReaderRelation): RestrictionMembers {
        // we use -1 to indicate 'missing', which is fine because we exclude negative OSM IDs (see #2652)
        var viaOSMNode = -1L
        val fromWays = LongArrayList()
        val viaWays = LongArrayList()
        val toWays = LongArrayList()
        for (member in relation.getMembers()) {
            if ("from" == member.role) {
                if (member.type != ReaderElement.Type.WAY)
                    throw OSMRestrictionException("has a member with role 'from' and type '" + member.type + "', but it should be of type 'way'")
                fromWays.add(member.ref)
            } else if ("to" == member.role) {
                if (member.type != ReaderElement.Type.WAY)
                    throw OSMRestrictionException("has a member with role 'to' and type '" + member.type + "', but it should be of type 'way'")
                toWays.add(member.ref)
            } else if ("via" == member.role) {
                if (member.type == ReaderElement.Type.NODE) {
                    if (viaOSMNode >= 0)
                        throw OSMRestrictionException("has multiple members with role 'via' and type 'node', but multiple via-members are only allowed when they are of type: 'way'")
                    // note that we check for combined usage of via nodes and ways later on
                    viaOSMNode = member.ref
                } else if (member.type == ReaderElement.Type.WAY) {
                    // note that we check for combined usage of via nodes and ways later on
                    viaWays.add(member.ref)
                } else
                    throw OSMRestrictionException("has a member with role 'via' and" +
                            " type '" + member.type + "', but it should be of type 'node' or 'way'")
            } else if ("location_hint" == member.role) {
                // location_hint is deprecated and should no longer be used according to the wiki, but we do not warn
                // about it, or even ignore the relation in this case, because maybe not everyone is happy to remove it.
            } else if (member.role!!.trim().isEmpty())
                throw OSMRestrictionException("has a member with an empty role")
            else
                throw OSMRestrictionException("has a member with an unknown role '" + member.role + "'")
        }
        if (fromWays.isEmpty && toWays.isEmpty)
            throw OSMRestrictionException("has no member with role 'from' and 'to'")
        else if (fromWays.isEmpty)
            throw OSMRestrictionException("has no member with role 'from'")
        else if (toWays.isEmpty)
            throw OSMRestrictionException("has no member with role 'to'")

        if (fromWays.size() > 1 && toWays.size() > 1)
            throw OSMRestrictionException("has multiple members with role 'from' and 'to'")
        checkTags(fromWays, toWays, relation.getTags())
        if (viaOSMNode >= 0 && !viaWays.isEmpty)
            throw OSMRestrictionException("has members with role 'via' of type 'node' and 'way', but only one type is allowed")
        else if (viaOSMNode >= 0)
            return RestrictionMembers.viaNode(viaOSMNode, fromWays, toWays)
        else if (!viaWays.isEmpty)
            return RestrictionMembers.viaWay(fromWays, viaWays, toWays)
        else
            throw OSMRestrictionException("has no member with role 'via'")
    }

    @Throws(OSMRestrictionException::class)
    private fun checkTags(fromWays: LongArrayList, toWays: LongArrayList, tags: Map<String, Any>) {
        // the exact restriction value depends on the vehicle type, but we can already print a warning for certain
        // cases here, so later we do not print such warnings for every single vehicle.
        var hasNoEntry = false
        var hasNoExit = false
        for ((key, value) in tags) {
            if (key.startsWith("restriction")) {
                if (value != null && (value as String).startsWith("no_entry"))
                    hasNoEntry = true
                if (value != null && (value as String).startsWith("no_exit"))
                    hasNoExit = true
            }
        }
        if (fromWays.size() > 1 && !hasNoEntry)
            throw OSMRestrictionException("has multiple members with role 'from' even though it is not a 'no_entry' restriction")
        if (toWays.size() > 1 && !hasNoExit)
            throw OSMRestrictionException("has multiple members with role 'to' even though it is not a 'no_exit' restriction")
    }

    /**
     * Converts an OSM restriction to (multiple) single 'no' restrictions to be fed into [RestrictionSetter]
     */
    @JvmStatic
    fun buildRestrictionsForOSMRestriction(
        baseGraph: BaseGraph, topology: RestrictionTopology, type: RestrictionType?
    ): List<RestrictionSetter.Restriction> {
        val result = ArrayList<RestrictionSetter.Restriction>()
        if (type == NO) {
            if (topology.isViaWayRestriction) {
                for (fromEdge in topology.fromEdges)
                    for (toEdge in topology.toEdges) {
                        val edges = IntArrayList(topology.viaEdges!!.size() + 2)
                        edges.add(fromEdge.value)
                        edges.addAll(topology.viaEdges)
                        edges.add(toEdge.value)
                        result.add(RestrictionSetter.createViaEdgeRestriction(edges))
                    }
            } else {
                for (fromEdge in topology.fromEdges)
                    for (toEdge in topology.toEdges)
                        result.add(RestrictionSetter.createViaNodeRestriction(fromEdge.value, topology.viaNodes.get(0), toEdge.value))
            }
        } else if (type == ONLY) {
            if (topology.fromEdges.size() > 1 || topology.toEdges.size() > 1)
                throw IllegalArgumentException("'Only' restrictions with multiple from- or to- edges are not supported")
            if (topology.isViaWayRestriction)
                result.addAll(createRestrictionsForViaEdgeOnlyRestriction(baseGraph, topology))
            else
                result.addAll(createRestrictionsForViaNodeOnlyRestriction(baseGraph.createEdgeExplorer(),
                    topology.fromEdges.get(0), topology.viaNodes.get(0), topology.toEdges.get(0)))
        } else
            throw IllegalArgumentException("Unexpected restriction type: $type")
        return result
    }

    private fun collectEdges(r: RestrictionTopology): IntArrayList {
        val result = IntArrayList(r.viaEdges!!.size() + 2)
        result.add(r.fromEdges.get(0))
        result.addAll(r.viaEdges)
        result.add(r.toEdges.get(0))
        return result
    }

    private fun createRestrictionsForViaNodeOnlyRestriction(edgeExplorer: EdgeExplorer, fromEdge: Int, viaNode: Int, toEdge: Int): List<RestrictionSetter.Restriction> {
        val result = ArrayList<RestrictionSetter.Restriction>()
        val iter = edgeExplorer.setBaseNode(viaNode)
        while (iter.next()) {
            // deny all turns except the one to the to-edge, and (for performance reasons, see below)
            // except the u-turn back to the from-edge
            if (iter.edge != toEdge && iter.edge != fromEdge)
                result.add(RestrictionSetter.createViaNodeRestriction(fromEdge, viaNode, iter.edge))
        }
        return result
    }

    private fun createRestrictionsForViaEdgeOnlyRestriction(graph: BaseGraph, topology: RestrictionTopology): List<RestrictionSetter.Restriction> {
        // For via-way ONLY restrictions we have to turn from the from-edge onto the first via-edge,
        // continue with the next via-edge(s) and finally turn onto the to-edge. So we cannot branch
        // out anywhere. If we don't start with the from-edge the restriction does not apply at all.
        // c.f. https://github.com/valhalla/valhalla/discussions/4764
        if (topology.viaEdges!!.isEmpty)
            throw IllegalArgumentException("Via-edge restrictions must have at least one via-edge")
        val explorer = graph.createEdgeExplorer()
        val edges = collectEdges(topology)
        val result: MutableList<RestrictionSetter.Restriction> =
            ArrayList(createRestrictionsForViaNodeOnlyRestriction(explorer, edges.get(0), topology.viaNodes.get(0), edges.get(1)))
        for (i in 2 until edges.size()) {
            val iter = explorer.setBaseNode(topology.viaNodes.get(i - 1))
            while (iter.next()) {
                if (iter.edge != edges.get(i) &&
                    // We deny u-turns within via-way 'only' restrictions unconditionally (see below), so no need
                    // to restrict them here as well
                    iter.edge != edges.get(i - 1)
                ) {
                    val restriction = IntArrayList(i + 1)
                    for (j in 0 until i)
                        restriction.add(edges.get(j))
                    restriction.add(iter.edge)
                    if (restriction.size() == 3 && restriction.get(0) == restriction.get(restriction.size() - 1))
                        // To prevent an exception in RestrictionSetter we need to prevent unambiguous
                        // restrictions like a-b-a. Maybe we even need to exclude other cases as well,
                        // but so far they did not occur.
                        continue
                    result.add(RestrictionSetter.createViaEdgeRestriction(restriction))
                }
            }
        }
        // explicitly deny all u-turns along the via-way 'only' restriction
        // todo: currently disabled! we skip u-turn restrictions to improve reading performance,
        //       because so far they are ignored anyway! https://github.com/graphhopper/graphhopper/issues/2570
//        for (int i = 0; i < edges.size() - 1; i++) {
//            result.add(RestrictionSetter.createViaNodeRestriction(edges.get(i), topology.getViaNodes().get(i), edges.get(i)));
//        }
        return result
    }
}
