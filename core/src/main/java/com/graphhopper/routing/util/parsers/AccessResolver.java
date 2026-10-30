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
package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;

import java.util.*;
import java.util.function.Function;

import static com.graphhopper.routing.util.parsers.OSMTemporalAccessParser.hasPermissiveTemporalRestriction;

/**
 * Resolves the access value of a way or barrier node for a mode: the first restriction key (most specific first)
 * with a recognized value decides, where a conditional of the key beats its plain value and an implied default
 * (highway type, barrier type) is used if the key is absent or its value unknown.
 */
public class AccessResolver {
    private static final Set<String> INTENDED = Set.of("yes", "designated", "official", "permissive");
    // quirk: locked barriers need destination for now
    public static final Set<String> ALLOWED = Set.of("yes", "designated", "official", "permissive", "destination");
    public static final Set<String> RESTRICTED = Set.of("no", "restricted", "military", "emergency",
            "private", "permit", "service", "delivery", "customers", "agricultural", "forestry");
    private static final Map<String, String> MOTORROAD_DEFAULTS = Map.of("foot", "no", "bicycle", "no");
    static final Map<String, Map<String, String>> HIGHWAY_TYPE_DEFAULTS;
    static final Map<String, Map<String, String>> BARRIER_TYPE_DEFAULTS;

    static {
        // https://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Access_restrictions
        Map<String, Map<String, String>> m = new HashMap<>();
        // For implied bicycle defaults we use "dismount" rather than "no" on highways where
        // pushing a bike is physically reasonable (pedestrian streets, footways, stairs, indoor
        // corridors, station platforms). The motorway/motorway_link keep "no" because bikes are
        // legally prohibited even on foot.
        m.put("motorway", Map.of("motor_vehicle", "designated", "foot", "no", "bicycle", "no"));
        m.put("motorway_link", Map.of("motor_vehicle", "designated", "foot", "no", "bicycle", "no"));
        m.put("steps", Map.of("motor_vehicle", "no", "bicycle", "dismount", "foot", "designated"));
        m.put("footway", Map.of("motor_vehicle", "no", "bicycle", "dismount", "foot", "designated"));
        m.put("cycleway", Map.of("motor_vehicle", "no", "bicycle", "designated", "foot", "no"));
        m.put("pedestrian", Map.of("motor_vehicle", "no", "bicycle", "dismount", "foot", "designated"));
        m.put("path", Map.of("motor_vehicle", "no", "foot", "yes", "bicycle", "yes"));
        m.put("bridleway", Map.of("motor_vehicle", "no", "foot", "yes", "bicycle", "yes"));
        m.put("busway", Map.of("access", "no", "bus", "designated"));
        m.put("construction", Map.of("access", "no"));
        m.put("proposed", Map.of("access", "no"));
        m.put("raceway", Map.of("access", "no"));
        m.put("corridor", Map.of("motor_vehicle", "no", "bicycle", "no", "foot", "yes"));
        m.put("platform", Map.of("motor_vehicle", "no", "bicycle", "dismount", "foot", "yes"));
        HIGHWAY_TYPE_DEFAULTS = Map.copyOf(m);

        // https://wiki.openstreetmap.org/wiki/Key:barrier
        Map<String, Map<String, String>> b = new HashMap<>();
        b.put("fence", Map.of("access", "no"));
        b.put("wall", Map.of("access", "no"));
        b.put("hedge", Map.of("access", "no"));
        b.put("retaining_wall", Map.of("access", "no"));
        b.put("city_wall", Map.of("access", "no"));
        b.put("ditch", Map.of("access", "no"));
        b.put("kerb", Map.of("vehicle", "yes", "foot", "yes"));
        b.put("cattle_grid", Map.of("motor_vehicle", "yes", "foot", "yes", "bicycle", "yes"));
        b.put("bollard", Map.of("motor_vehicle", "no", "foot", "yes", "bicycle", "yes"));
        b.put("block", Map.of("motor_vehicle", "no", "foot", "yes", "bicycle", "yes"));
        b.put("log", Map.of("motor_vehicle", "no", "foot", "yes", "bicycle", "yes"));
        b.put("chain", Map.of("motor_vehicle", "no", "foot", "yes", "bicycle", "yes"));
        b.put("jersey_barrier", Map.of("motor_vehicle", "no", "foot", "yes", "bicycle", "yes"));
        b.put("cycle_barrier", Map.of("motor_vehicle", "no", "foot", "yes", "bicycle", "yes"));
        b.put("motorcycle_barrier", Map.of("motor_vehicle", "no", "foot", "yes", "bicycle", "yes"));
        b.put("bus_trap", Map.of("motor_vehicle", "no", "bus", "yes", "foot", "yes", "bicycle", "yes"));
        b.put("sump_buster", Map.of("motor_vehicle", "no", "bus", "yes", "foot", "yes", "bicycle", "yes"));
        b.put("kissing_gate", Map.of("vehicle", "no", "foot", "yes"));
        b.put("stile", Map.of("vehicle", "no", "foot", "yes"));
        b.put("turnstile", Map.of("vehicle", "no", "foot", "yes"));
        BARRIER_TYPE_DEFAULTS = Map.copyOf(b);
    }

    /**
     * @param value    the resolved value, empty if no key decided
     * @param keyIndex the index of the deciding restriction key, -1 if none
     */
    public record Result(String value, int keyIndex) {
        static final Result NONE = new Result("", -1);
    }

    private final List<String> restrictionKeys;
    private final Set<String> allowed;
    private final Set<String> restricted;
    private final Function<ReaderWay, Map<String, String>> impliedDefaults;
    private final Map<String, Map<String, String>> barrierDefaults;
    private final VehicleConditions conditions;

    /**
     * @param impliedDefaults the access values implied by the way tags per restriction key, see osmDefaults
     * @param barrierDefaults the access values implied by a barrier node per barrier value and restriction key
     * @param conditions      evaluates key:conditional, null to ignore non-temporal conditionals
     */
    public AccessResolver(List<String> restrictionKeys, Set<String> allowed, Set<String> restricted,
                          Function<ReaderWay, Map<String, String>> impliedDefaults,
                          Map<String, Map<String, String>> barrierDefaults, VehicleConditions conditions) {
        this.restrictionKeys = restrictionKeys;
        this.allowed = allowed;
        this.restricted = restricted;
        this.impliedDefaults = impliedDefaults;
        this.barrierDefaults = barrierDefaults;
        this.conditions = conditions;
    }

    /**
     * The access values implied by the highway type and motorroad=yes
     */
    public static Map<String, String> osmDefaults(ReaderWay way) {
        String highwayValue = way.getTag("highway");
        Map<String, String> defaults = highwayValue == null ? Map.of() : HIGHWAY_TYPE_DEFAULTS.getOrDefault(highwayValue, Map.of());
        // special case motorroad=yes: it's not a highway=* value but a separate tag that implies foot=no, bicycle=no.
        if (way.hasTag("motorroad", "yes")) {
            Map<String, String> merged = new HashMap<>(defaults);
            MOTORROAD_DEFAULTS.forEach(merged::putIfAbsent);
            return merged;
        }
        return defaults;
    }

    public List<String> getRestrictionKeys() {
        return restrictionKeys;
    }

    public boolean isAllowed(String value) {
        return allowed.contains(value);
    }

    public boolean isRestricted(String value) {
        return restricted.contains(value);
    }

    public Result resolve(ReaderWay way) {
        Map<String, String> defaults = impliedDefaults.apply(way);
        for (int i = 0; i < restrictionKeys.size(); i++) {
            String key = restrictionKeys.get(i);
            // a conditional of the same key beats its plain value, e.g. hgv:conditional=no @ (weight > 7.5) and hgv=yes
            String conditional = conditions == null ? null : conditions.getConditionalValue(way, key);
            // none @ destination lifts a restriction like yes
            if ("none".equals(conditional)) conditional = "yes";
            if (conditional != null && (allowed.contains(conditional) || restricted.contains(conditional)))
                return new Result(conditional, i);
            String explicit = recognized(way.getTag(key));
            if (explicit != null)
                return new Result(explicit, i);
            // unknown value — fall through to implied default for same key, then keep looking
            String implied = defaults.get(key);
            if (implied != null)
                return new Result(implied, i);
        }
        return Result.NONE;
    }

    /**
     * @return true if the resolved value closes the way: restricted and not lifted by a permissive temporal
     * conditional of the deciding or a more specific key
     */
    public boolean isRestricted(ReaderWay way, Result result) {
        return restricted.contains(result.value) && !hasPermissiveTemporalRestriction(way, result.keyIndex, restrictionKeys, allowed);
    }

    /**
     * @return true if the barrier node with the given tags blocks the mode. Unlike for ways an unknown explicit
     * value hides the less specific explicit values, only the defaults are still considered.
     */
    public boolean isBarrier(Map<String, Object> nodeTags) {
        String barrierValue = nodeTags.containsKey("barrier") ? (String) nodeTags.get("barrier") : "";
        Map<String, String> defaults = barrierDefaults.getOrDefault(barrierValue, Map.of());
        String nodeValue = "";
        boolean unknown = false;
        for (String key : restrictionKeys) {
            String explicit = unknown ? null : (String) nodeTags.get(key);
            if (explicit != null) {
                if (allowed.contains(explicit) || restricted.contains(explicit)) {
                    nodeValue = explicit;
                    break;
                }
                unknown = true;
            }
            String implied = defaults.get(key);
            if (implied != null) {
                nodeValue = implied;
                break;
            }
        }
        return restricted.contains(nodeValue) || "yes".equals(nodeTags.get("locked")) && !INTENDED.contains(nodeValue);
    }

    /**
     * @return the allowed or restricted value of a tag like "yes;forestry" (allowed wins), null if unknown
     */
    private String recognized(String value) {
        if (value == null) return null;
        value = value.trim();
        if (allowed.contains(value)) return value;
        if (restricted.contains(value)) return value;
        if (value.indexOf(';') < 0) return null;
        String result = null;
        for (String raw : value.split(";")) {
            String v = raw.trim();
            if (allowed.contains(v)) return v;
            if (restricted.contains(v)) result = v;
        }
        return result;
    }
}
