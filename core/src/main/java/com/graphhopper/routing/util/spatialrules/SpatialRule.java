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

import java.util.List;

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
     * Returns the {@link AccessValue} for a certain highway type and transportation mode. If nothing is defined,
     * _default will be returned.
     *
     * @param highwayTag         The highway type, e.g. primary, secondary
     * @param transportationMode The mode of transportation
     * @param _default           The default AccessValue
     */
    AccessValue getAccessValue(String highwayTag, TransportationMode transportationMode, AccessValue _default);

    /**
     * Returns the borders in which the SpatialRule is valid
     */
    List<Polygon> getBorders();

    /**
     * Set the borders in which the SpatialRule is valid
     */
    SpatialRule setBorders(List<Polygon> borders);

    /**
     * Add a polygon to the borders in which the SpatialRule is valid
     */
    SpatialRule addBorder(Polygon polygon);

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
        public AccessValue getAccessValue(String highwayTag, TransportationMode transportationMode, AccessValue _default) {
            return AccessValue.ACCESSIBLE;
        }

        @Override
        public String getId() {
            return "";
        }

        @Override
        public List<Polygon> getBorders() {
            throw new IllegalArgumentException("Empty rule does not have borders");
        }

        @Override
        public SpatialRule setBorders(List<Polygon> borders) {
            throw new IllegalArgumentException("Empty rule cannot have borders");
        }

        @Override
        public SpatialRule addBorder(Polygon polygon) {
            throw new IllegalArgumentException("Empty rule cannot have borders");
        }

        @Override
        public String toString() {
            return "SpatialRule.EMPTY";
        }
    };
}
