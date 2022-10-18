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
import com.carrotsearch.hppc.cursors.LongCursor;
import com.graphhopper.reader.ReaderElement;
import com.graphhopper.reader.ReaderRelation;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;

public class OSMRestrictionRelationParser {
    static List<OSMTurnRestriction> createTurnRestrictions(ReaderRelation relation, Consumer<String> warningConsumer) {
        // see here: https://wiki.openstreetmap.org/wiki/Relation:restriction
        if (!"restriction".equals(relation.getTag("type")))
            return emptyList();

        String restriction = relation.getTag("restriction");
        List<String> limitedRestrictions = relation.getTags().entrySet().stream()
                .filter(t -> t.getKey().startsWith("restriction:"))
                // restriction:bicycle=give_way seems quite common in France, but since it isn't a 'strict' turn
                // restriction we ignore it here. We also want to prevent warnings about combined
                // restriction+restriction: tags in this case (see below).
                .filter(e -> !"give_way".equals(e.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        List<String> exceptVehicles = relation.hasTag("except")
                // todo: there are also some occurrences of except=resident(s), destination or delivery
                ? Arrays.stream(relation.getTag("except").split(";")).map(String::trim).collect(Collectors.toList())
                : emptyList();
        if (restriction != null) {
            // the 'restriction' tag limits the turns for all vehicles, unless this is modified by the 'except' tag
            if (!limitedRestrictions.isEmpty()) {
                warningConsumer.accept("Restriction relation " + relation.getId() + " has a 'restriction' tag, but also 'restriction:' tags: " + relation.getTags() + ". Relation ignored.");
                return emptyList();
            }
            return createTurnRestrictions(relation, restriction, warningConsumer).stream()
                    .peek(r -> r.setVehicleTypesExcept(exceptVehicles))
                    .collect(Collectors.toList());
        } else {
            // if there is no 'restriction' tag there still might be 'restriction:xyz' tags that only affect certain vehicles
            if (limitedRestrictions.isEmpty()) {
                warningConsumer.accept("Restriction relation " + relation.getId() + " neither has a 'restriction' nor 'restriction:' tags: " + relation.getTags() + ". Relation ignored.");
                return emptyList();
            }
            if (!exceptVehicles.isEmpty() && limitedRestrictions.stream().noneMatch(r -> r.startsWith("restriction:conditional"))) {
                warningConsumer.accept("Restriction relation " + relation.getId() + " has an 'except', but no 'restriction' or 'restriction:conditional' tag: " + relation.getTags() + ". Relation ignored.");
                return emptyList();
            }
            return limitedRestrictions.stream()
                    // We do not consider the restriction[:<transportation_mode>]:conditional tag so far
                    .filter(r -> !r.contains("conditional"))
                    .flatMap(r -> {
                        String vehicle = r.replace("restriction:", "").trim();
                        return createTurnRestrictions(relation, relation.getTag(r), warningConsumer).stream()
                                .peek(rb -> rb.setVehicleTypeRestricted(vehicle));
                    })
                    .collect(Collectors.toList());
        }
    }

    private static List<OSMTurnRestriction> createTurnRestrictions(ReaderRelation relation, String restriction, Consumer<String> warningConsumer) {
        OSMTurnRestriction.RestrictionType type = parseRestrictionValue(restriction);

        if (type == OSMTurnRestriction.RestrictionType.UNSUPPORTED)
            return Collections.emptyList();

        LongArrayList fromWays = new LongArrayList(1);
        LongArrayList toWays = new LongArrayList(1);
        // we use -1 to indicate 'missing', which is fine because we exclude negative OSM IDs (see #2652)
        long viaNodeID = -1;
        for (ReaderRelation.Member member : relation.getMembers()) {
            if ("from".equals(member.getRole())) {
                if (member.getType() != ReaderElement.Type.WAY) {
                    warningConsumer.accept("Restriction relation " + relation.getId() + " has a member with role 'from'" +
                            " and type '" + member.getType() + "', but it should be of type 'way'" + relation.getTags() + ". Relation ignored.");
                    return emptyList();
                }
                if (!fromWays.isEmpty() && !"no_entry".equals(restriction)) {
                    warningConsumer.accept("Restriction relation " + relation.getId() + " has multiple members with role" +
                            " 'from' even though it is not a 'no_entry' restriction: " + relation.getTags() + ". Relation ignored.");
                    return emptyList();
                }
                fromWays.add(member.getRef());
            } else if ("to".equals(member.getRole())) {
                if (member.getType() != ReaderElement.Type.WAY) {
                    warningConsumer.accept("Restriction relation " + relation.getId() + " has a member with role 'to' and" +
                            " type '" + member.getType() + "', but it should be of type 'way': " + relation.getTags() + ". Relation ignored.");
                    return emptyList();
                }
                if (!toWays.isEmpty() && !"no_exit".equals(restriction)) {
                    warningConsumer.accept("Restriction relation " + relation.getId() + " has multiple members with role " +
                            "'to' even though it is not a 'no_exit' restriction: " + relation.getTags() + ". Relation ignored.");
                    return emptyList();
                }
                toWays.add(member.getRef());
            } else if ("via".equals(member.getRole())) {
                if (viaNodeID >= 0)
                    // so far we only support restriction relations with a single via member
                    return emptyList();
                if (member.getType() != ReaderElement.Type.NODE) {
                    // so far we only support via members that are nodes
                    return emptyList();
                }
                viaNodeID = member.getRef();
            } else if ("location_hint".equals(member.getRole())) {
                // location_hint is deprecated and should no longer be used according to the wiki, but we do not warn
                // about it, or even ignore the relation in this case, because maybe not everyone is happy to remove it.
            } else {
                warningConsumer.accept("Restriction relation " + relation.getId() + " has a member with an unknown role '"
                        + member.getRole() + "': " + relation.getTags() + ". Relation ignored.");
                return emptyList();
            }
        }

        if (viaNodeID < 0) {
            warningConsumer.accept("Restriction relation " + relation.getId() + " has no member with role 'via': " + relation.getTags() + ". Relation ignored");
            return emptyList();
        }
        if (fromWays.size() < 1 || toWays.size() < 1) {
            warningConsumer.accept("Restriction relation " + relation.getId() + " is missing a member with role 'from' or 'to': " + relation.getTags() + ". Relation ignored");
            return emptyList();
        }
        List<OSMTurnRestriction> res = new ArrayList<>(fromWays.size() * toWays.size());
        for (LongCursor from : fromWays)
            for (LongCursor to : toWays)
                res.add(new OSMTurnRestriction(from.value, viaNodeID, to.value, type));
        return res;
    }

    private static OSMTurnRestriction.RestrictionType parseRestrictionValue(String restrictionType) {
        switch (restrictionType) {
            case "no_left_turn":
            case "no_right_turn":
            case "no_straight_on":
            case "no_u_turn":
            case "no_entry":
            case "no_exit":
                return OSMTurnRestriction.RestrictionType.NOT;
            case "only_left_turn":
            case "only_right_turn":
            case "only_straight_on":
            case "only_u_turn":
                return OSMTurnRestriction.RestrictionType.ONLY;
            default:
                return OSMTurnRestriction.RestrictionType.UNSUPPORTED;
        }
    }
}
