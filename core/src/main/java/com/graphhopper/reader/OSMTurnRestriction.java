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
 *
 * @author Karl Hübner
 */
public class OSMTurnRestriction {
    private final ReaderRelation relation;

    public OSMTurnRestriction(ReaderRelation relation) {
        this.relation = relation;
    }

    public long getOsmIdFrom() {
        return relation.findMember(ReaderElement.WAY, "from");
    }

    public long getViaOsmNodeId() {
        return relation.findMember(ReaderElement.NODE, "via");
    }

    public long getOsmIdTo() {
        return relation.findMember(ReaderElement.WAY, "to");
    }

    public Type getRestrictionType() {
        if (relation.hasTag("restriction")) {
            return OSMTurnRestriction.Type.getRestrictionType(relation.getTag("restriction"));
        }
        for (String key : relation.getKeysWithPrefix("restriction:")) {
            return OSMTurnRestriction.Type.getRestrictionType(relation.getTag(key));
        }
        throw new RuntimeException();
    }

    public List<String> getVehicleTypesRestricted() {
        List<String> vehicleTypesRestricted = new ArrayList<>();
        if (relation.hasTag("restriction")) {
            vehicleTypesRestricted.add(""); // *all*
        }
        for (String key : relation.getKeysWithPrefix("restriction:")) {
            String keyWithoutRestrictionPrefix = key.replace("restriction:", "").trim();
            vehicleTypesRestricted.add(keyWithoutRestrictionPrefix);
        }
        return vehicleTypesRestricted;
    }

    public List<String> getVehicleTypesExcept() {
        List<String> vehicleTypesExcept = new ArrayList<>();
        if (relation.hasTag("except")) {
            String tagExcept = relation.getTag("except");
            if (!Helper.isEmpty(tagExcept)) {
                List<String> vehicleTypes = new ArrayList<>(Arrays.asList(tagExcept.split(";")));
                for (String vehicleType : vehicleTypes)
                    vehicleTypesExcept.add(vehicleType.trim());
            }
        }
        return vehicleTypesExcept;
    }

    public boolean isVehicleTypeConcernedByTurnRestriction(Collection<String> vehicleTypes) {
        List<String> vehicleTypesExcept = getVehicleTypesExcept();
        if (!Collections.disjoint(vehicleTypes, vehicleTypesExcept)) {
            return false;
        }
        List<String> vehicleTypesRestricted = getVehicleTypesRestricted();
        return vehicleTypesRestricted.contains("") || (!Collections.disjoint(vehicleTypes, vehicleTypesRestricted));
    }

    @Override
    public String toString() {
        return "*-(" + getOsmIdFrom() + ")->" + getViaOsmNodeId() + "-(" + getOsmIdTo() + ")->*";
    }

    public ReaderRelation getReaderRelation() {
        return relation;
    }

    public enum Type {
        NOT, ONLY;

        public static Type getRestrictionType(String tag) {
            if (tag.startsWith("no"))
                return NOT;
            else if (tag.startsWith("only"))
                return ONLY;
            return null;
        }
    }

}
