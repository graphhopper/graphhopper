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

import com.carrotsearch.hppc.LongArrayList;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.carrotsearch.hppc.cursors.LongCursor;
import com.graphhopper.reader.ReaderElement;
import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.storage.BaseGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.function.LongFunction;

public class RestrictionConverter {
    private static final Logger LOGGER = LoggerFactory.getLogger(RestrictionConverter.class);
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
     * OSM restriction relations specify turn restrictions between OSM ways (of course). This method converts such a
     * relation into a 'graph' representation, where the turn restrictions are specified in terms of edge/node IDs instead
     * of OSM IDs.
     *
     * @throws OSMRestrictionException if the given relation is either not valid in some way and/or cannot be handled and
     *                                 shall be ignored
     */
    public static Triple<ReaderRelation, GraphRestriction, RestrictionMembers> convert(ReaderRelation relation, BaseGraph baseGraph, LongFunction<Iterator<IntCursor>> edgesByWay) throws OSMRestrictionException {
        if (!isTurnRestriction(relation))
            throw new IllegalArgumentException("expected a turn restriction: " + relation.getTags());
        RestrictionMembers restrictionMembers = extractMembers(relation);
        if (!membersExist(restrictionMembers, edgesByWay, relation))
            throw OSMRestrictionException.withoutWarning();
        // every OSM way might be split into *multiple* edges, so now we need to figure out which edges are the ones
        // that are actually part of the given relation
        WayToEdgeConverter wayToEdgeConverter = new WayToEdgeConverter(baseGraph, edgesByWay);
        if (restrictionMembers.isViaWay()) {
            WayToEdgeConverter.EdgeResult res = wayToEdgeConverter
                    .convertForViaWays(restrictionMembers.getFromWays(), restrictionMembers.getViaWays(), restrictionMembers.getToWays());
            return new Triple<>(relation, GraphRestriction.way(res.getFromEdges(), res.getViaEdges(), res.getToEdges(), res.getNodes()), restrictionMembers);
        } else {
            int viaNode = relation.getTag("graphhopper:via_node", -1);
            if (viaNode < 0)
                throw new IllegalStateException("For some reason we did not set graphhopper:via_node for this relation: " + relation.getId());
            WayToEdgeConverter.NodeResult res = wayToEdgeConverter
                    .convertForViaNode(restrictionMembers.getFromWays(), viaNode, restrictionMembers.getToWays());
            return new Triple<>(relation, GraphRestriction.node(res.getFromEdges(), viaNode, res.getToEdges()), restrictionMembers);
        }
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

    public static void checkIfCompatibleWithRestriction(GraphRestriction g, String restriction) throws OSMRestrictionException {
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
}
