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

import com.graphhopper.routing.ev.RoadAccess;
import com.graphhopper.routing.ev.RoadClass;

/**
 * Contains all rules which are applicable for a certain position.
 * 
 * @author Thomas Butz
 */
public class SpatialRuleSet {
    public static final SpatialRuleSet EMPTY = new SpatialRuleSet(Collections.<SpatialRule>emptyList(), 0);
    
    private final List<SpatialRule> rules;
    private final int spatialId;

    /**
     * @param rules     a List of rules, ordered according to how they are to be applied
     * @param spatialId the index of the rule with the highest priority
     */
    public SpatialRuleSet(List<SpatialRule> rules, int spatialId) {
        this.rules = Collections.unmodifiableList(rules);
        this.spatialId = spatialId;
    }
    
    /**
     * Return the max speed for a certain road class.
     *
     * @param roadClass       The highway type, e.g. {@link RoadClass#MOTORWAY}
     * @param transport       The mode of transportation
     * @param currentMaxSpeed The current max speed value or {@link Double#NaN} if no value has been set yet
     * @return the maximum speed value to be used
     */
    public double getMaxSpeed(RoadClass roadClass, TransportationMode transport, double currentMaxSpeed) {
        double value = currentMaxSpeed;
        for (SpatialRule rule : rules) {
            value = rule.getMaxSpeed(roadClass, transport, value);
        }
        return value;
    }

    /**
     * Returns the {@link RoadAccess} for a certain highway type and transportation mode.
     *
     * @param roadClass          The highway type, e.g. {@link RoadClass#MOTORWAY}
     * @param transport          The mode of transportation
     * @param currentRoadAccess  The current road access value (default: {@link RoadAccess#YES})
     * @return the type of access to be used
     */
    public RoadAccess getAccess(RoadClass roadClass, TransportationMode transport, RoadAccess currentRoadAccess) {
        RoadAccess value = currentRoadAccess;
        for (SpatialRule rule : rules) {
            value = rule.getAccess(roadClass, transport, value);
        }
        return value;
    }
    
    /**
     * @return the rules in this set
     */
    public List<SpatialRule> getRules() {
        return rules;
    }

    /**
     * @return the id of the rule with the highest priority or
     *         <i>0</i> if the set doesn't contain any rules
     */
    public int getSpatialId() {
        return spatialId;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("SpatialRuleSet [rules=");
        builder.append(rules);
        builder.append("]");
        return builder.toString();
    }
}
