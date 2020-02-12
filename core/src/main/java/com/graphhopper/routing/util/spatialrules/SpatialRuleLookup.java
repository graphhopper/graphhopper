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

import java.util.Collections;
import java.util.List;

import org.locationtech.jts.geom.Envelope;

/**
 * SpatialRuleLookup defines a container that stores SpatialRules and can lookup
 * applicable rules depending on the location.
 *
 * @author Robin Boldt
 */
public interface SpatialRuleLookup {
    
    /**
     * Return applicable rules for this location.
     * <p>
     * If multiple rules with the same priority overlap for a location, the
     * implementation can decide which of them to return and in which order.
     * <p>
     * If the requested location is outside of the supported bounds or no
     * SpatialRule is registered at this location {@link SpatialRuleSet#EMPTY}
     * is returned.
     */
    SpatialRuleSet lookupRules(double lat, double lon);

    /**
     * @return the rules which are active for {@link #getBounds()}
     */
    List<SpatialRule> getRules();

    /**
     * @return the bounds of the SpatialRuleLookup
     */
    Envelope getBounds();

    SpatialRuleLookup EMPTY = new SpatialRuleLookup() {
        @Override
        public SpatialRuleSet lookupRules(double lat, double lon) {
            return SpatialRuleSet.EMPTY;
        }
        
        @Override
        public List<SpatialRule> getRules() {
            return Collections.emptyList();
        }

        @Override
        public Envelope getBounds() {
            return new Envelope(-180d, 180d, -90d, 90d);
        }
    };
}
