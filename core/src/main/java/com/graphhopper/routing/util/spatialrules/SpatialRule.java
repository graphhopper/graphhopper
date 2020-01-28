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
     * Return the max speed for a certain highway type. If there is no max speed defined, _default will be returned.
     *
     * @param highway  The highway type, e.g. primary, secondary
     * @param _default The default max speed
     * @return max speed
     */
    double getMaxSpeed(String highway, double _default);

    /**
     * Returns the {@link RoadAccess} for a certain highway type and transportation mode. If nothing is defined,
     * _default will be returned.
     *
     * @param highwayTag         The highway type, e.g. primary, secondary
     * @param transportationMode The mode of transportation
     * @param _default           The default AccessValue
     */
    RoadAccess getAccess(String highwayTag, TransportationMode transportationMode, RoadAccess _default);

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
        public double getMaxSpeed(String highwayTag, double _default) {
            return _default;
        }

        @Override
        public RoadAccess getAccess(String highwayTag, TransportationMode transportationMode, RoadAccess _default) {
            return _default;
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
