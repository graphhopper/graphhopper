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

import com.graphhopper.util.Helper;

import java.util.*;

/**
 * Helper object which gives node cost entries for a given OSM-relation of type "restriction"
 * <p>
 *
 * @author Karl Hübner
 */
public class OSMTurnRelation {
    private final long fromOsmWayId;
    private final long viaOsmNodeId;
    private final long toOsmWayId;
    private final Type restriction;
    private final String vehicleTypeRestricted;
    private final List<String> vehicleTypesExcept;

    public OSMTurnRelation(long fromWayID, long viaNodeID, long toWayID, Type restrictionType, String vehicleTypeRestricted,  List<String> vehicleTypesExcept) {
        this.fromOsmWayId = fromWayID;
        this.viaOsmNodeId = viaNodeID;
        this.toOsmWayId = toWayID;
        this.restriction = restrictionType;
        this.vehicleTypeRestricted = vehicleTypeRestricted;
        this.vehicleTypesExcept = vehicleTypesExcept;
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

    public List<String> getVehicleTypesExcept() {
        return vehicleTypesExcept;
    }

    /**
     * From a vehiclesTypes list, when the
     * @param vehicleTypes
     * @return
     */
    public boolean isVehicleTypeConcernedByTurnRestriction(List<String> vehicleTypes) {
        if (Helper.isEmpty(vehicleTypeRestricted) && ((vehicleTypesExcept == null) || ((vehicleTypesExcept != null) && (vehicleTypesExcept.isEmpty())))) {
            return true;
        }
        for (String vehicleType : vehicleTypes) {
            if (!Helper.isEmpty(vehicleTypeRestricted)) {
                if (vehicleTypeRestricted.equals(vehicleType)) {
                    return true;
                }
            }
            if (vehicleTypesExcept != null) {
                if (vehicleTypesExcept.contains(vehicleType)) {
                    return false;
                }
            }
        }
        if (!Helper.isEmpty(vehicleTypeRestricted)) {
            return false;
        }
        return true;
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

    /**
     * Helper class to processing purposes only
     */
    public static class TurnCostTableEntry {
        public int edgeFrom;
        public int nodeVia;
        public int edgeTo;
        public long flags;

        /**
         * @return an unique id (edgeFrom, edgeTo) to avoid duplicate entries if multiple encoders
         * are involved.
         */
        public long getItemId() {
            return ((long) edgeFrom) << 32 | ((long) edgeTo);
        }

        @Override
        public String toString() {
            return "*-(" + edgeFrom + ")->" + nodeVia + "-(" + edgeTo + ")->*";
        }
    }

}
