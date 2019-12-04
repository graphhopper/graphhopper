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

import com.graphhopper.routing.profiles.Country;
import com.graphhopper.routing.profiles.Toll;
import com.graphhopper.routing.util.spatialrules.DefaultSpatialRule;

/**
 * Defines the default rules for Swiss roads
 *
 * @author Thomas Butz
 */
public class SwitzerlandSpatialRule extends DefaultSpatialRule {

    @Override
    public double getMaxSpeed(String highwayTag, double _default) {
        // As defined in: https://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Maxspeed#Motorcar
        switch (highwayTag) {
            case "motorway":
                return 120;
            case "trunk":
                return 100;
            case "primary":
            case "secondary":
            case "tertiary":
            case "unclassified":
                return 80;
            case "residential":
                return 50;
            case "living_street":
                return 20;
            default:
                return super.getMaxSpeed(highwayTag, _default);
        }
    }
    
    @Override
    public Toll getToll(String highwayTag, Toll _default) {
        if ("motorway".equals(highwayTag) || "trunk".equals(highwayTag)) {
            return Toll.ALL;
        }
        
        return Toll.HGV;
    }
    
    @Override
    public String getId() {
        return Country.CHE.toString();
    }
}
