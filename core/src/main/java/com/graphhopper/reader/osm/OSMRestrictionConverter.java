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

package com.graphhopper.reader.osm;

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.LongArrayList;
import com.carrotsearch.hppc.LongHashSet;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.carrotsearch.hppc.cursors.LongCursor;
import com.graphhopper.reader.ReaderElement;
import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.routing.util.parsers.RestrictionSetter;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;

import static com.graphhopper.reader.osm.RestrictionType.NO;
import static com.graphhopper.reader.osm.RestrictionType.ONLY;

public class OSMRestrictionConverter {
    private static final Logger LOGGER = LoggerFactory.getLogger(OSMRestrictionConverter.class);
    private static final long[] EMPTY_LONG_ARRAY_LIST = new long[0];

    public static boolean isTurnRestriction(ReaderRelation relation) {
        return "restriction".equals(relation.getTag("type"));
    }

    public static long[] getRestrictedWayIds(ReaderRelation relation) {
        if (!isTurnRestriction(relation))
            return EMPTY_LONG_ARRAY_LIST;
        return relation.getMembers().stream()
                .filter(m -> m.getType() == ReaderElement.Type.WAY)
                .filter(m -> "from".equals(m.getRole()) || "via".equals(m.getRole()) || "to".equals(m.getRole()))
                .mapToLong(ReaderRelation.Member::getRef)
                .toArray();
    }

    public static long getViaNodeIfViaNodeRestriction(ReaderRelation relation) {
        return relation.getMembers().stream()
                .filter(m -> m.getType().equals(ReaderElement.Type.NODE))
                .filter(m -> "via".equals(m.getRole()))
                .mapToLong(ReaderRelation.Member::getRef)
                .findFirst()
                .orElse(-1);
    }

    /**
     * OSM restriction relations specify turn restrictions between OSM ways (of course). This method rebuilds the
     * topology of such a relation in the graph representation, where the turn restrictions are specified in terms of edge/node IDs instead
     * of OSM IDs.
     *
     * @throws OSMRestrictionException if the given relation is either not valid in some way and/or cannot be handled and
     *                                 shall be ignored
     */
    public static Triple<ReaderRelation, RestrictionTopology, RestrictionMembers> buildRestrictionTopologyForGraph(BaseGraph baseGraph, ReaderRelation relation, LongFunction<Iterator<IntCursor>> edgesByWay) throws OSMRestrictionException {
        if (!isTurnRestriction(relation))
            throw new IllegalArgumentException("expected a turn restriction: " + relation.getTags());
        RestrictionMembers restrictionMembers = extractMembers(relation);
        if (!membersExist(restrictionMembers, edgesByWay, relation))
            throw OSMRestrictionException.withoutWarning();
        // every OSM way might be split into *multiple* edges, so now we need to figure out which edges are the ones
        // that are actually part of the given relation
        WayToEdgeConverter wayToEdgeConverter = new WayToEdgeConverter(baseGraph, edgesByWay);
        if (restrictionMembers.isViaWay()) {
            if (containsDuplicateWays(restrictionMembers))
                // For now let's ignore all via-way restrictions with duplicate from/to/via-members
                // until we find cases where this is too strict.
                throw new OSMRestrictionException("contains duplicate from-/via-/to-members");
            WayToEdgeConverter.EdgeResult res = wayToEdgeConverter
                    .convertForViaWays(restrictionMembers.getFromWays(), restrictionMembers.getViaWays(), restrictionMembers.getToWays());
            return new Triple<>(relation, RestrictionTopology.way(res.getFromEdges(), res.getViaEdges(), res.getToEdges(), res.getNodes()), restrictionMembers);
        } else {
            int viaNode = relation.getTag("graphhopper:via_node", -1);
            if (viaNode < 0)
                throw new IllegalStateException("For some reason we did not set graphhopper:via_node for this relation: " + relation.getId());
            WayToEdgeConverter.NodeResult res = wayToEdgeConverter
                    .convertForViaNode(restrictionMembers.getFromWays(), viaNode, restrictionMembers.getToWays());
            return new Triple<>(relation, RestrictionTopology.node(res.getFromEdges(), viaNode, res.getToEdges()), restrictionMembers);
        }
    }

    private static boolean containsDuplicateWays(RestrictionMembers restrictionMembers) {
        LongArrayList allWays = restrictionMembers.getAllWays();
        LongHashSet uniqueWays = new LongHashSet(allWays);
        return uniqueWays.size() != allWays.size();
    }

    private static boolean membersExist(RestrictionMembers members, LongFunction<Iterator<IntCursor>> edgesByWay, ReaderRelation relation) {
        for (LongCursor c : members.getAllWays())
            if (!edgesByWay.apply(c.value).hasNext()) {
                // this happens for example at the map borders or when certain ways like footways are excluded
                LOGGER.debug("Restriction relation " + relation.getId() + " uses excluded way " + c.value + ". Relation ignored.");
                return false;
            }
        return true;
    }

    public static void checkIfTopologyIsCompatibleWithRestriction(RestrictionTopology g, String restriction) throws OSMRestrictionException {
        if (g.getFromEdges().size() > 1 && !"no_entry".equals(restriction))
            throw new OSMRestrictionException("has multiple members with role 'from' even though it is not a 'no_entry' restriction");
        if (g.getToEdges().size() > 1 && !"no_exit".equals(restriction))
            throw new OSMRestrictionException("has multiple members with role 'to' even though it is not a 'no_exit' restriction");
    }

    public static RestrictionMembers extractMembers(ReaderRelation relation) throws OSMRestrictionException {
        // we use -1 to indicate 'missing', which is fine because we exclude negative OSM IDs (see #2652)
        long viaOSMNode = -1;
        LongArrayList fromWays = new LongArrayList();
        LongArrayList viaWays = new LongArrayList();
        LongArrayList toWays = new LongArrayList();
        for (ReaderRelation.Member member : relation.getMembers()) {
            if ("from".equals(member.getRole())) {
                if (member.getType() != ReaderElement.Type.WAY)
                    throw new OSMRestrictionException("has a member with role 'from' and type '" + member.getType() + "', but it should be of type 'way'");
                fromWays.add(member.getRef());
            } else if ("to".equals(member.getRole())) {
                if (member.getType() != ReaderElement.Type.WAY)
                    throw new OSMRestrictionException("has a member with role 'to' and type '" + member.getType() + "', but it should be of type 'way'");
                toWays.add(member.getRef());
            } else if ("via".equals(member.getRole())) {
                if (member.getType() == ReaderElement.Type.NODE) {
                    if (viaOSMNode >= 0)
                        throw new OSMRestrictionException("has multiple members with role 'via' and type 'node', but multiple via-members are only allowed when they are of type: 'way'");
                    // note that we check for combined usage of via nodes and ways later on
                    viaOSMNode = member.getRef();
                } else if (member.getType() == ReaderElement.Type.WAY) {
                    // note that we check for combined usage of via nodes and ways later on
                    viaWays.add(member.getRef());
                } else
                    throw new OSMRestrictionException("has a member with role 'via' and" +
                            " type '" + member.getType() + "', but it should be of type 'node' or 'way'");
            } else if ("location_hint".equals(member.getRole())) {
                // location_hint is deprecated and should no longer be used according to the wiki, but we do not warn
                // about it, or even ignore the relation in this case, because maybe not everyone is happy to remove it.
            } else if (member.getRole().trim().isEmpty())
                throw new OSMRestrictionException("has a member with an empty role");
            else
                throw new OSMRestrictionException("has a member with an unknown role '" + member.getRole() + "'");
        }
        if (fromWays.isEmpty() && toWays.isEmpty())
            throw new OSMRestrictionException("has no member with role 'from' and 'to'");
        else if (fromWays.isEmpty())
            throw new OSMRestrictionException("has no member with role 'from'");
        else if (toWays.isEmpty())
            throw new OSMRestrictionException("has no member with role 'to'");

        if (fromWays.size() > 1 && toWays.size() > 1)
            throw new OSMRestrictionException("has multiple members with role 'from' and 'to'");
        checkTags(fromWays, toWays, relation.getTags());
        if (viaOSMNode >= 0 && !viaWays.isEmpty())
            throw new OSMRestrictionException("has members with role 'via' of type 'node' and 'way', but only one type is allowed");
        else if (viaOSMNode >= 0)
            return RestrictionMembers.viaNode(viaOSMNode, fromWays, toWays);
        else if (!viaWays.isEmpty())
            return RestrictionMembers.viaWay(fromWays, viaWays, toWays);
        else
            throw new OSMRestrictionException("has no member with role 'via'");
    }

    private static void checkTags(LongArrayList fromWays, LongArrayList toWays, Map<String, Object> tags) throws OSMRestrictionException {
        // the exact restriction value depends on the vehicle type, but we can already print a warning for certain
        // cases here, so later we do not print such warnings for every single vehicle.
        boolean hasNoEntry = false;
        boolean hasNoExit = false;
        for (Map.Entry<String, Object> e : tags.entrySet()) {
            if (e.getKey().startsWith("restriction")) {
                if (e.getValue() != null && ((String) e.getValue()).startsWith("no_entry"))
                    hasNoEntry = true;
                if (e.getValue() != null && ((String) e.getValue()).startsWith("no_exit"))
                    hasNoExit = true;
            }
        }
        if (fromWays.size() > 1 && !hasNoEntry)
            throw new OSMRestrictionException("has multiple members with role 'from' even though it is not a 'no_entry' restriction");
        if (toWays.size() > 1 && !hasNoExit)
            throw new OSMRestrictionException("has multiple members with role 'to' even though it is not a 'no_exit' restriction");
    }

    /**
     * Converts an OSM restriction to (multiple) single 'no' restrictions to be fed into {@link RestrictionSetter}
     */
    public static List<RestrictionSetter.Restriction> buildRestrictionsForOSMRestriction(
            BaseGraph baseGraph, RestrictionTopology topology, RestrictionType type) {
        List<RestrictionSetter.Restriction> result = new ArrayList<>();
        if (type == NO) {
            if (topology.isViaWayRestriction()) {
                if (topology.getFromEdges().size() > 1 || topology.getToEdges().size() > 1)
                    throw new IllegalArgumentException("Via-way restrictions with multiple from- or to- edges are not supported");
                result.add(RestrictionSetter.createViaEdgeRestriction(collectEdges(topology)));
            } else {
                for (IntCursor fromEdge : topology.getFromEdges())
                    for (IntCursor toEdge : topology.getToEdges())
                        result.add(RestrictionSetter.createViaNodeRestriction(fromEdge.value, topology.getViaNodes().get(0), toEdge.value));
            }
        } else if (type == ONLY) {
            if (topology.getFromEdges().size() > 1 || topology.getToEdges().size() > 1)
                throw new IllegalArgumentException("'Only' restrictions with multiple from- or to- edges are not supported");
            if (topology.isViaWayRestriction())
                result.addAll(createRestrictionsForViaEdgeOnlyRestriction(baseGraph, topology));
            else
                result.addAll(createRestrictionsForViaNodeOnlyRestriction(baseGraph.createEdgeExplorer(),
                        topology.getFromEdges().get(0), topology.getViaNodes().get(0), topology.getToEdges().get(0)));
        } else
            throw new IllegalArgumentException("Unexpected restriction type: " + type);
        return result;
    }

    private static IntArrayList collectEdges(RestrictionTopology r) {
        IntArrayList result = new IntArrayList(r.getViaEdges().size() + 2);
        result.add(r.getFromEdges().get(0));
        r.getViaEdges().iterator().forEachRemaining(c -> result.add(c.value));
        result.add(r.getToEdges().get(0));
        return result;
    }

    private static List<RestrictionSetter.Restriction> createRestrictionsForViaNodeOnlyRestriction(EdgeExplorer edgeExplorer, int fromEdge, int viaNode, int toEdge) {
        List<RestrictionSetter.Restriction> result = new ArrayList<>();
        EdgeIterator iter = edgeExplorer.setBaseNode(viaNode);
        while (iter.next()) {
            // deny all turns except the one to the to-edge, and (for performance reasons, see below)
            // except the u-turn back to the from-edge
            if (iter.getEdge() != toEdge && iter.getEdge() != fromEdge)
                result.add(RestrictionSetter.createViaNodeRestriction(fromEdge, viaNode, iter.getEdge()));
        }
        return result;
    }

    private static List<RestrictionSetter.Restriction> createRestrictionsForViaEdgeOnlyRestriction(BaseGraph graph, RestrictionTopology topology) {
        // For via-way ONLY restrictions we have to turn from the from-edge onto the first via-edge,
        // continue with the next via-edge(s) and finally turn onto the to-edge. So we cannot branch
        // out anywhere. If we don't start with the from-edge the restriction does not apply at all.
        // c.f. https://github.com/valhalla/valhalla/discussions/4764
        if (topology.getViaEdges().isEmpty())
            throw new IllegalArgumentException("Via-edge restrictions must have at least one via-edge");
        final EdgeExplorer explorer = graph.createEdgeExplorer();
        IntArrayList edges = collectEdges(topology);
        List<RestrictionSetter.Restriction> result =
                createRestrictionsForViaNodeOnlyRestriction(explorer, edges.get(0), topology.getViaNodes().get(0), edges.get(1));
        for (int i = 2; i < edges.size(); i++) {
            EdgeIterator iter = explorer.setBaseNode(topology.getViaNodes().get(i - 1));
            while (iter.next()) {
                if (iter.getEdge() != edges.get(i) &&
                        // We deny u-turns within via-way 'only' restrictions unconditionally (see below), so no need
                        // to restrict them here as well
                        iter.getEdge() != edges.get(i - 1)
                ) {
                    IntArrayList restriction = new IntArrayList(i + 1);
                    for (int j = 0; j < i; j++)
                        restriction.add(edges.get(j));
                    restriction.add(iter.getEdge());
                    if (restriction.size() == 3 && restriction.get(0) == restriction.get(restriction.size() - 1))
                        // To prevent an exception in RestrictionSetter we need to prevent unambiguous
                        // restrictions like a-b-a. Maybe we even need to exclude other cases as well,
                        // but so far they did not occur.
                        continue;
                    result.add(RestrictionSetter.createViaEdgeRestriction(restriction));
                }
            }
        }
        // explicitly deny all u-turns along the via-way 'only' restriction
        // todo: currently disabled! we skip u-turn restrictions to improve reading performance,
        //       because so far they are ignored anyway! https://github.com/graphhopper/graphhopper/issues/2570
//        for (int i = 0; i < edges.size() - 1; i++) {
//            result.add(RestrictionSetter.createViaNodeRestriction(edges.get(i), topology.getViaNodes().get(i), edges.get(i)));
//        }
        return result;
    }
}
