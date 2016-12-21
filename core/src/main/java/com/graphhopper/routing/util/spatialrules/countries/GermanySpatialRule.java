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
package com.graphhopper.routing.util.spatialrules.countries;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.util.spatialrules.AccessValue;
import com.graphhopper.routing.util.spatialrules.SpatialRule;

/**
 * Defines the default rules for German roads
 *
 * @author Robin Boldt
 */
public class GermanySpatialRule extends DefaultSpatialRule {

    public int getMaxSpeed(ReaderWay readerWay, String transportationMode) {
        String highwayTag = readerWay.getTag("highway", "");

        // As defined in: https://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Maxspeed#Motorcar
        switch (highwayTag) {
            case "motorway":
                return Integer.MAX_VALUE;
            case "trunk":
                return Integer.MAX_VALUE;
            case "residential":
                return 100;
            case "living_street":
                return 4;
            default:
                return super.getMaxSpeed(readerWay, transportationMode);
        }
    }

    public AccessValue isAccessible(ReaderWay readerWay, String transportationMode) {
        if (readerWay.hasTag("highway", "track"))
            return AccessValue.EVENTUALLY_ACCESSIBLE;

        return super.isAccessible(readerWay, transportationMode);
    }

    public String getCountryIsoA3Name() {
        return "DEU";
    }

}
