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

import com.graphhopper.routing.ev.DecimalEncodedValue;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;

/**
 * Parses the OSM restriction tags for given vehicle types / transportation modes.
 */
public class RestrictionTagParser {
    private final List<String> vehicleTypes;
    private final DecimalEncodedValue turnCostEnc;

    public RestrictionTagParser(List<String> vehicleTypes, DecimalEncodedValue turnCostEnc) {
        this.vehicleTypes = vehicleTypes;
        this.turnCostEnc = turnCostEnc;
    }

    public List<String> getVehicleTypes() {
        return vehicleTypes;
    }

    public DecimalEncodedValue getTurnCostEnc() {
        return turnCostEnc;
    }

    public Result parseRestrictionTags(Map<String, Object> tags) throws OSMRestrictionException {
        String restriction = (String) tags.get("restriction");
        List<String> limitedRestrictions = tags.entrySet().stream()
                .filter(t -> t.getKey().startsWith("restriction:"))
                // restriction:bicycle=give_way seems quite common in France, but since it isn't a 'strict' turn
                // restriction we ignore it here.
                .filter(e -> !"give_way".equals(e.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        boolean hasGiveWay = tags.values().stream().anyMatch("give_way"::equals);
        List<String> exceptVehicles = tags.containsKey("except")
                // todo: there are also some occurrences of except=resident(s), destination or delivery
                ? Arrays.stream(((String) tags.get("except")).split(";")).map(String::trim).collect(Collectors.toList())
                : emptyList();
        if (restriction != null) {
            // the 'restriction' tag limits the turns for all vehicleTypes, unless this is modified by the 'except' tag
            if (!limitedRestrictions.isEmpty())
                // note that there is no warning if there is a restriction tag and restriction:*=give_way
                throw new OSMRestrictionException("has a 'restriction' tag, but also 'restriction:' tags");
            if (!Collections.disjoint(vehicleTypes, exceptVehicles))
                return null;
            return buildResult(restriction);
        } else {
            // if there is no 'restriction' tag there still might be 'restriction:xyz' tags that only affect certain vehicleTypes
            if (limitedRestrictions.isEmpty())
                if (!hasGiveWay)
                    throw new OSMRestrictionException("neither has a 'restriction' nor 'restriction:' tags");
                else
                    // ignore, but no warning if there is only restriction:*=give_way
                    throw OSMRestrictionException.withoutWarning();
            if (!exceptVehicles.isEmpty() && limitedRestrictions.stream().noneMatch(r -> r.startsWith("restriction:conditional")))
                throw new OSMRestrictionException("has an 'except', but no 'restriction' or 'restriction:conditional' tag");
            Set<String> restrictions = limitedRestrictions.stream()
                    // We do not consider the restriction[:<transportation_mode>]:conditional tag so far
                    .filter(r -> !r.contains("conditional"))
                    .filter(r -> vehicleTypes.contains(r.replace("restriction:", "").trim()))
                    .map(r -> (String) tags.get(r))
                    .collect(Collectors.toSet());
            if (restrictions.size() > 1)
                throw new OSMRestrictionException("contains multiple different restriction values: '" + restrictions + "'");
            else if (restrictions.isEmpty())
                return null;
            else
                return buildResult(restrictions.iterator().next());
        }
    }

    private static Result buildResult(String restriction) throws OSMRestrictionException {
        return new Result(parseRestrictionValue(restriction), restriction);
    }

    private static RestrictionType parseRestrictionValue(String restriction) throws OSMRestrictionException {
        switch (restriction) {
            case "no_left_turn":
            case "no_right_turn":
            case "no_straight_on":
            case "no_u_turn":
            case "no_entry":
            case "no_exit":
                return RestrictionType.NO;
            case "only_left_turn":
            case "only_right_turn":
            case "only_straight_on":
            case "only_u_turn":
                return RestrictionType.ONLY;
            case "no_right_turn_on_red":
            case "no_left_turn_on_red":
                throw OSMRestrictionException.withoutWarning();
            default:
                throw new OSMRestrictionException("uses unknown restriction value: '" + restriction + "'");
        }
    }

    public static class Result {
        private final RestrictionType restrictionType;
        private final String restriction;

        public Result(RestrictionType restrictionType, String restriction) {
            this.restrictionType = restrictionType;
            this.restriction = restriction;
        }

        public RestrictionType getRestrictionType() {
            return restrictionType;
        }

        public String getRestriction() {
            return restriction;
        }
    }
}
