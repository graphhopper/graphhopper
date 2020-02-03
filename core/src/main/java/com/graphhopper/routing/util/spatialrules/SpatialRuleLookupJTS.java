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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.index.strtree.STRtree;

import com.graphhopper.util.shapes.GHPoint;

/**
 * @author Thomas Butz
 */
public class SpatialRuleLookupJTS implements SpatialRuleLookup {
    
    private final GeometryFactory geometryFactory = new GeometryFactory();
    private final List<SpatialRule> rules;
    private final Envelope maxBounds;
    private final STRtree index;
    
    public SpatialRuleLookupJTS(List<SpatialRule> spatialRules, Envelope maxBounds) {
        this.index = new STRtree();
        
        Map<Polygon, SpatialRuleContainer> containerMap = new HashMap<>();
        Set<String> ruleIDs = new HashSet<>();
        for (SpatialRule rule : spatialRules) {
            if (rule == null)
                throw new IllegalArgumentException("rule cannot be null");

            if (rule.equals(SpatialRule.EMPTY))
                throw new IllegalArgumentException("rule cannot be EMPTY");
            
            if (!ruleIDs.add(rule.getId()))
                throw new IllegalArgumentException("Duplicate rule ID: \"" + rule.getId() + "\"");
            
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
            }
        }

        index.build();

        this.rules = new ArrayList<>();
        rules.add(SpatialRule.EMPTY);
        rules.addAll(spatialRules);
        this.maxBounds = maxBounds;
    }

    @Override
    public SpatialRule lookupRule(double lat, double lon) {
        if (!maxBounds.covers(lon, lat)) {
            return SpatialRule.EMPTY;
        }

        Envelope searchEnv = new Envelope(lon, lon, lat, lat);
        
        @SuppressWarnings("unchecked")
        List<SpatialRuleContainer> containers = index.query(searchEnv);
        
        if (containers.isEmpty()) {
            return SpatialRule.EMPTY;
        }
        
        Point point = geometryFactory.createPoint(new Coordinate(lon, lat));
        List<SpatialRule> applicableRules = new ArrayList<>();
        for (SpatialRuleContainer container : containers) {
            if (container.containsProperly(point)) {
                applicableRules.addAll(container.getRules());
            }
        }
        
        if (applicableRules.isEmpty()) {
            return SpatialRule.EMPTY;
        }

        //TODO support multiple rules
        return applicableRules.get(0);
    }

    @Override
    public SpatialRule lookupRule(GHPoint point) {
        return lookupRule(point.lat, point.lon);
    }
    
    @Override
    public int getSpatialId(SpatialRule rule) {
        return rules.indexOf(rule);
    }
    
    @Override
    public SpatialRule getSpatialRule(int spatialId) {
        return rules.get(spatialId);
    }

    @Override
    public int size() {
        return rules.size();
    }

    @Override
    public Envelope getBounds() {
        return maxBounds;
    }
}
