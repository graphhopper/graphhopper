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
package com.graphhopper.reader;

import java.util.*;

/**
 * Helper object which gives node cost entries for a given OSM-relation of type "restriction"
 *
 * @author Karl Hübner
 */
public class OSMTurnRelation {
    private final long fromOsmWayId;
    private final long viaOsmNodeId;
    private final long toOsmWayId;
    private final Type restriction;
    // vehicleTypeRestricted contains the dedicated vehicle type
    // example: restriction:bus = no_left_turn => vehicleTypeRestricted = "bus";
    private String vehicleTypeRestricted;
    private List<String> vehicleTypesExcept;

    public OSMTurnRelation(long fromWayID, long viaNodeID, long toWayID, Type restrictionType) {
        this.fromOsmWayId = fromWayID;
        this.viaOsmNodeId = viaNodeID;
        this.toOsmWayId = toWayID;
        this.restriction = restrictionType;
        this.vehicleTypeRestricted = "";
        this.vehicleTypesExcept = new ArrayList<>();
    }

    public long getOsmIdFrom() {
        return fromOsmWayId;
    }

    public long getOsmIdTo() {
        return toOsmWayId;
    }

    public long getViaOsmNodeId() {
        return viaOsmNodeId;
    }

    public Type getRestriction() {
        return restriction;
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
        return "*-(" + fromOsmWayId + ")->" + viaOsmNodeId + "-(" + toOsmWayId + ")->*";
    }

    public enum Type {
        UNSUPPORTED, NOT, ONLY;

        private static final Map<String, Type> tags = new HashMap<>();

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

        public static Type getRestrictionType(String tag) {
            Type result = null;
            if (tag != null) {
                result = tags.get(tag);
            }
            return (result != null) ? result : UNSUPPORTED;
        }
    }

}
