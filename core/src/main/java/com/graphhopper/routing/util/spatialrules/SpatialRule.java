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
package com.graphhopper.routing.util.spatialrules;

import com.graphhopper.routing.profiles.RoadAccess;
import com.graphhopper.routing.profiles.RoadClass;

import java.util.List;
import java.util.Objects;

import org.locationtech.jts.geom.Polygon;

/**
 * Defines rules that are valid for a certain region, e.g. a country.
 * A rule defines access, max-speed, etc. dependent on this region.
 * <p>
 * Every SpatialRule has a set of borders. The rules are valid inside of these borders.
 *
 * @author Robin Boldt
 */
public interface SpatialRule {

    /**
     * Return the max speed for a certain road class and transportation mode.
     *
     * @param roadClass       The highway type, e.g. {@link RoadClass#MOTORWAY}
     * @param transport       The mode of transportation
     * @param currentMaxSpeed The current max speed value or -1 if no value has been set yet
     * @return the maximum speed value to be used
     */
    double getMaxSpeed(RoadClass roadClass, TransportationMode transport, double currentMaxSpeed);
    
    /**
     * Returns the {@link RoadAccess} for a certain highway type and transportation transport.
     *
     * @param roadClass          The highway type, e.g. {@link RoadClass#MOTORWAY}
     * @param transport          The mode of transportation
     * @param currentRoadAccess  The current road access value (default: {@link RoadAccess#YES})
     * @return the type of access to be used
     */
    RoadAccess getAccess(RoadClass roadClass, TransportationMode transport, RoadAccess currentRoadAccess);

    /**
     * Returns the borders in which the SpatialRule is valid
     */
    List<Polygon> getBorders();

    /**
     * Returns the id for this rule, e.g. the ISO name of the country. The id has to be unique.
     */
    String getId();


    SpatialRule EMPTY = new SpatialRule() {
        @Override
        public double getMaxSpeed(RoadClass roadClass, TransportationMode transport, double currentMaxSpeed) {
            return currentMaxSpeed;
        }

        @Override
        public RoadAccess getAccess(RoadClass roadClass, TransportationMode transport, RoadAccess currentRoadAccess) {
            return currentRoadAccess;
        }

        // should we use Country.DEFAULT here?
        @Override
        public String getId() {
            return "SpatialRule.EMPTY";
        }

        @Override
        public List<Polygon> getBorders() {
            throw new IllegalArgumentException("Empty rule does not have borders");
        }

        @Override
        public String toString() {
            return "SpatialRule.EMPTY";
        }
        
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof SpatialRule)) {
                return false;
            }
            return Objects.equals(getId(), ((SpatialRule) obj).getId());
        }
        
        @Override
        public int hashCode() {
            return getId().hashCode();
        }
    };
}
