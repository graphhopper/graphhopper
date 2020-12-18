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
package com.graphhopper.routing.util.area;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.locationtech.jts.geom.Envelope;

import com.graphhopper.config.CustomArea;
import com.graphhopper.routing.util.spatialrules.SpatialRule;

/**
 * CustomAreaLookup defines a container that stores {@link CustomArea CustomAreas} and their related
 * {@link SpatialRule SpatialRules} and can lookup applicable ones depending on the location.
 *
 * @author Robin Boldt
 * @author Thomas Butz
 */
public interface CustomAreaLookup {

    /**
     * Returns all areas this location belongs to.
     * <p>
     * If the requested location is outside of the supported bounds or no CustomArea is registered
     * at this location an empty List is returned.
     */
    LookupResult lookup(double lat, double lon);

    /**
     * @return the registered areas
     */
    List<CustomArea> getAreas();

    /**
     * @return the registered rules
     */
    List<SpatialRule> getRules();

    /**
     * @return maps the name of each encoded value retrieved from
     *         {@link CustomArea#getEncodedValue()} to the expected number of entries retrieved from
     *         {@link CustomArea#getEncodedValueLimit()}
     */
    Map<String, Integer> getEncodedValueMap();

    /**
     * @return the bounds of the SpatialRuleLookup
     */
    Envelope getBounds();

    CustomAreaLookup EMPTY = new CustomAreaLookup() {
        @Override
        public LookupResult lookup(double lat, double lon) {
            return LookupResult.EMPTY;
        }
        
        @Override
        public List<CustomArea> getAreas() {
            return Collections.emptyList();
        }
        
        @Override
        public List<SpatialRule> getRules() {
            return Collections.emptyList();
        }
        
        @Override
        public Map<String, Integer> getEncodedValueMap() {
            return Collections.emptyMap();
        }

        @Override
        public Envelope getBounds() {
            return new Envelope(-180d, 180d, -90d, 90d);
        }
    };
}
