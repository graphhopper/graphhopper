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

import java.util.*;

/**
 * Helper object which gives node cost entries for a given OSM-relation of type "restriction"
 *
 * @author Karl Hübner
 */
public class OSMTurnRestriction {
    private final long id;
    private final long fromOsmWayId;
    private final ArrayList<Long> viaOSMIds;
    private final long toOsmWayId;
    private final RestrictionType restriction;
    private final ViaType viaType;
    // vehicleTypeRestricted contains the dedicated vehicle type
    // example: restriction:bus = no_left_turn => vehicleTypeRestricted = "bus";
    private String vehicleTypeRestricted;
    private List<String> vehicleTypesExcept;

    public OSMTurnRestriction(long id, long fromWayID, ArrayList<Long> viaOSMIds, long toWayID, RestrictionType restrictionType, ViaType viaType) {
        this.id = id;
        this.fromOsmWayId = fromWayID;
        this.viaOSMIds = viaOSMIds;
        this.toOsmWayId = toWayID;
        this.restriction = restrictionType;
        this.viaType = viaType;
        this.vehicleTypeRestricted = "";
        this.vehicleTypesExcept = new ArrayList<>();
    }

    public OSMTurnRestriction(long id, long fromWayID, long viaId, long toWayID, RestrictionType restrictionType, ViaType viaType) {
        this(id, fromWayID, new ArrayList<>(Arrays.asList(viaId)), toWayID, restrictionType, viaType);
    }

    public long getId() {
        return id;
    }

    public long getOsmIdFrom() {
        return fromOsmWayId;
    }

    public long getOsmIdTo() {
        return toOsmWayId;
    }

    public ArrayList<Long> getViaOSMIds() {
        return viaOSMIds;
    }

    public ArrayList<Long> getWays() {
        ArrayList<Long> ways = new ArrayList<>();
        ways.add(fromOsmWayId);
        if (viaType == ViaType.WAY || viaType == ViaType.MULTI_WAY) {
            ways.addAll(viaOSMIds);
        }
        ways.add(toOsmWayId);
        return ways;
    }

    public RestrictionType getRestriction() {
        return restriction;
    }

    public ViaType getViaType() {
        return viaType;
    }

    public String getVehicleTypeRestricted() {
        return vehicleTypeRestricted;
    }

    public void setVehicleTypeRestricted(String vehicleTypeRestricted) {
        this.vehicleTypeRestricted = vehicleTypeRestricted;
    }

    public List<String> getVehicleTypesExcept() {
        return vehicleTypesExcept;
    }

    public void setVehicleTypesExcept(List<String> vehicleTypesExcept) {
        this.vehicleTypesExcept = vehicleTypesExcept;
    }

    /**
     * For a conditional turn restriction, test each vehicle type to verify if it is concerned.
     * For a normal turn restriction (non conditional), the restriction is necessary considered.
     */
    public boolean isVehicleTypeConcernedByTurnRestriction(Collection<String> vehicleTypes) {
        // if the restriction explicitly does not apply for one of the vehicles we do not accept it
        if (!Collections.disjoint(vehicleTypes, vehicleTypesExcept)) {
            return false;
        }
        return vehicleTypeRestricted.isEmpty() || vehicleTypes.contains(vehicleTypeRestricted);
    }

    @Override
    public String toString() {
        return "*-(" + fromOsmWayId + ")->" + viaOSMIds + "-(" + toOsmWayId + ")->*";
    }

    public enum RestrictionType {
        UNSUPPORTED, NOT, ONLY;

        private static final Map<String, RestrictionType> tags = new HashMap<>();

        static {
            tags.put("no_left_turn", NOT);
            tags.put("no_right_turn", NOT);
            tags.put("no_straight_on", NOT);
            tags.put("no_u_turn", NOT);
            tags.put("no_entry", NOT);
            tags.put("only_right_turn", ONLY);
            tags.put("only_left_turn", ONLY);
            tags.put("only_straight_on", ONLY);
        }

        public static RestrictionType get(String tag) {
            RestrictionType result = null;
            if (tag != null) {
                result = tags.get(tag);
            }
            return (result != null) ? result : UNSUPPORTED;
        }
    }

    public enum ViaType {
        UNSUPPORTED, NODE, WAY, MULTI_WAY;
    }
}
