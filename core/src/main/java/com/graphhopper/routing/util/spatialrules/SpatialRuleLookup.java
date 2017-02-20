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

import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;

/**
 * SpatialRuleLookup defines a container that stores SpatialRules and can lookup
 * a SpatialRule rule depending on the location.
 *
 * @author Robin Boldt
 */
public interface SpatialRuleLookup {

    /**
     * Return an applicable rule for this location.
     * If there is more than one rule for the location, the implementation can decide which rule to return.
     * For example this might be the first occurring or the most relevant, see the implementation JavaDoc.
     * <p>
     * If the requested location is outside of the supported bounds or no SpatialRule is registered at this location
     * {@link SpatialRule#EMPTY} is returned.
     */
    SpatialRule lookupRule(double lat, double lon);

    /**
     * See {@link #lookupRule(double, double)} for details.
     */
    SpatialRule lookupRule(GHPoint point);

    /**
     * Add a rule to the lookup.
     */
    void addRule(SpatialRule rule);

    /**
     * This method returns an identification number from 0 to size (exclusive) for the specified rule.
     * The id is fix for a given set of SpatialRules. When the set of rules is modified, the id might change.
     */
    int getSpatialId(SpatialRule rule);

    /**
     * Returns the outer bounds of the spatial lookup.
     */
    BBox getBounds();

    /**
     * @return the number of rules added to this lookup.
     */
    int size();
}
