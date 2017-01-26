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
import com.graphhopper.routing.util.spatialrules.AbstractSpatialRule;
import com.graphhopper.routing.util.spatialrules.AccessValue;

/**
 * Default Implementation for the SpatialRule that contains the current default Values
 */
public class DefaultSpatialRule extends AbstractSpatialRule {

    @Override
    public double getMaxSpeed(ReaderWay readerWay, String transportationMode, double _default) {
        String highwayTag = readerWay.getTag("highway", "");

        // We tried to estimate reasonable values: https://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Maxspeed#Motorcar
        // We did not always used the highest value available, but we used a high value
        switch (highwayTag) {
            case "motorway":
                return 130;
            case "trunk":
                return 130;
            case "primary":
                return 100;
            case "secondary":
                return 100;
            case "tertiary":
                return 100;
            case "unclassified":
                return 100;
            case "residential":
                return 90;
            case "living_street":
                return 20;
            default:
                return _default;
        }
    }

    @Override
    public AccessValue isAccessible(ReaderWay readerWay, String transportationMode, AccessValue _default) {
        String highwayTag = readerWay.getTag("highway", "");

        // As defined in: https://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Access-Restriction
        // We tried to find generally forbidden tags
        switch (highwayTag) {
            case "path":
            case "bridleway":
            case "cycleway":
            case "footway":
            case "pedestrian":
                return AccessValue.NOT_ACCESSIBLE;
            default:
                return _default;
        }
    }

    @Override
    public String getUniqueName() {
        throw new UnsupportedOperationException("No country code for the DefaultSpatialRule");
    }
}
