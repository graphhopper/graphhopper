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

package com.graphhopper.routing.util.countryrules;

import com.graphhopper.routing.ev.NewCountry;
import com.graphhopper.routing.ev.RoadAccess;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.util.TransportationMode;

public interface CountryRule {
    // todo: improve signature, make more generic

    /**
     * todo: so far simply copied these javadocs from SpatialRule
     * Return the max speed for a certain road class.
     *
     * @param roadClass       The highway type, e.g. {@link RoadClass#MOTORWAY}
     * @param transport       The mode of transportation
     * @param currentMaxSpeed The current max speed value or {@link Double#NaN} if no value has been set yet
     * @return the maximum speed value to be used
     */
    default double getMaxSpeed(RoadClass roadClass, TransportationMode transport, double currentMaxSpeed) {
        return currentMaxSpeed;
    }

    // todo: improve signature, make more generic

    /**
     * todo: so far simply copied these javadocs from SpatialRule
     * Returns the {@link RoadAccess} for a certain highway type and transportation mode.
     *
     * @param roadClass         The highway type, e.g. {@link RoadClass#MOTORWAY}
     * @param transport         The mode of transportation
     * @param currentRoadAccess The current road access value (default: {@link RoadAccess#YES})
     * @return the type of access to be used
     */
    default RoadAccess getAccess(RoadClass roadClass, TransportationMode transport, RoadAccess currentRoadAccess) {
        return currentRoadAccess;
    }

    static CountryRule getCountryRule(NewCountry country) {
        switch (country) {
            case DEU:
                return GermanyCountryRule.RULE;
            case AUT:
                return AustriaCountryRule.RULE;
            default:
                return null;
        }
    }
}
