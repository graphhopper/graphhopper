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

import java.util.*;

import org.locationtech.jts.geom.*;
import org.locationtech.jts.index.strtree.STRtree;

/**
 * @author Thomas Butz
 */
public class SpatialRuleLookupJTS implements SpatialRuleLookup {
    
    private static final Comparator<SpatialRule> RULE_COMP = new Comparator<SpatialRule>() {

        @Override
        public int compare(SpatialRule o1, SpatialRule o2) {
            int comp = Integer.compare(o1.getPriority(), o2.getPriority());
            if (comp != 0) {
                return comp;
            }
            
            return o1.getId().compareTo(o2.getId());
        }
    };
    
    private final GeometryFactory geometryFactory = new GeometryFactory();
    private final List<SpatialRule> rules;
    private final Envelope maxBounds;
    private final STRtree index;
    
    public SpatialRuleLookupJTS(List<SpatialRule> spatialRules, Envelope maxBounds) {
        this.index = new STRtree();
        
        Map<Polygon, SpatialRuleContainer> containerMap = new HashMap<>();
        List<SpatialRule> registeredRules = new ArrayList<>();
        Set<String> ruleIDs = new HashSet<>();
        for (SpatialRule rule : spatialRules) {
            if (rule == null)
                throw new IllegalArgumentException("rule cannot be null");
            
            if (!ruleIDs.add(rule.getId()))
                throw new IllegalArgumentException("Duplicate rule ID: \"" + rule.getId() + "\"");
            
            boolean registered = false;
            for (Polygon border : rule.getBorders()) {
                Envelope borderEnvelope = border.getEnvelopeInternal();
                if (!maxBounds.intersects(borderEnvelope)) {
                    continue;
                }
                
                SpatialRuleContainer container = containerMap.get(border);
                if (container == null) {
                    container = new SpatialRuleContainer(border);
                    containerMap.put(border, container);
                    index.insert(borderEnvelope, container);
                }
                container.addRule(rule);
                registered = true;
            }
            
            if (registered) {
                registeredRules.add(rule);
            }
        }

        index.build();

        this.rules = Collections.unmodifiableList(registeredRules);
        this.maxBounds = maxBounds;
    }

    @Override
    public SpatialRuleSet lookupRules(double lat, double lon) {
        if (!maxBounds.covers(lon, lat)) {
            return SpatialRuleSet.EMPTY;
        }

        Envelope searchEnv = new Envelope(lon, lon, lat, lat);
        
        @SuppressWarnings("unchecked")
        List<SpatialRuleContainer> containers = index.query(searchEnv);
        
        if (containers.isEmpty()) {
            return SpatialRuleSet.EMPTY;
        }
        
        Point point = geometryFactory.createPoint(new Coordinate(lon, lat));
        List<SpatialRule> applicableRules = new ArrayList<>();
        for (SpatialRuleContainer container : containers) {
            if (container.containsProperly(point)) {
                applicableRules.addAll(container.getRules());
            }
        }
        
        if (applicableRules.isEmpty()) {
            return SpatialRuleSet.EMPTY;
        }
        
        Collections.sort(applicableRules, RULE_COMP);
        
        int spatialId = rules.indexOf(applicableRules.get(applicableRules.size() - 1)) + 1;
        
        return new SpatialRuleSet(applicableRules, spatialId);
    }
    
    @Override
    public List<SpatialRule> getRules() {
        return rules;
    }

    @Override
    public Envelope getBounds() {
        return maxBounds;
    }
}
