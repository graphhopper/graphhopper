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

import java.util.*;

import org.locationtech.jts.geom.*;
import org.locationtech.jts.index.strtree.STRtree;

import com.graphhopper.config.CustomArea;
import com.graphhopper.routing.util.spatialrules.SpatialRule;
import com.graphhopper.routing.util.spatialrules.SpatialRuleSet;

/**
 * @author Thomas Butz
 */
public class CustomAreaLookupJTS implements CustomAreaLookup {

    private static final Comparator<SpatialRule> SPATIAL_RULE_ID_COMP = Comparator
                    .comparingInt(SpatialRule::getPriority)
                    .thenComparing(SpatialRule::getId);
    private final GeometryFactory geometryFactory = new GeometryFactory();
    
    private final List<CustomArea> areas;
    private final List<SpatialRule> rules;
    private final Envelope maxBounds;
    private final STRtree index;
    

    public CustomAreaLookupJTS(List<CustomArea> areas) {
        this(areas, Collections.emptyList());
    }
    
    public CustomAreaLookupJTS(List<CustomArea> areas, List<SpatialRule> spatialRules) {
        this.areas = Collections.unmodifiableList(new ArrayList<>(areas));
        this.rules = Collections.unmodifiableList(new ArrayList<>(spatialRules));
        this.index = new STRtree();
        this.maxBounds = new Envelope();
        
        Map<String, SpatialRule> ruleMap = new HashMap<>();
        for (SpatialRule rule : spatialRules) {
            ruleMap.put(rule.getId(), rule);
        }
        
        Set<String> areaIDs = new HashSet<>();
        for (CustomArea area : areas) {
            if (area == null)
                throw new IllegalArgumentException("Custom area cannot be null");
            
            if (!areaIDs.add(area.getId()))
                throw new IllegalArgumentException("Duplicate area ID: \"" + area.getId() + "\"");
            
            SpatialRule rule = ruleMap.get(area.getId());
            for (Polygon border : area.getBorders()) {
                Envelope borderEnvelope = border.getEnvelopeInternal();
                
                CustomAreaContainer container = new CustomAreaContainer(border, area, rule);
                index.insert(borderEnvelope, container);
                maxBounds.expandToInclude(borderEnvelope);
            }
        }

        index.build();
    }
    
    @Override
    public LookupResult lookup(double lat, double lon) {
        if (!maxBounds.covers(lon, lat)) {
            return LookupResult.EMPTY;
        }

        Envelope searchEnv = new Envelope(lon, lon, lat, lat);
        
        @SuppressWarnings("unchecked")
        List<CustomAreaContainer> containers = index.query(searchEnv);
        
        if (containers.isEmpty()) {
            return LookupResult.EMPTY;
        }
        
        Point point = geometryFactory.createPoint(new Coordinate(lon, lat));
        List<CustomArea>  applicableAreas = new ArrayList<>();
        List<SpatialRule> applicableRules = new ArrayList<>();
        for (CustomAreaContainer container : containers) {
            if (container.covers(point)) {
                applicableAreas.add(container.getCustomArea());
                if (container.getSpatialRule() != null) {
                    applicableRules.add(container.getSpatialRule());
                }
            }
        }
        
        SpatialRuleSet ruleSet;
        if (applicableRules.isEmpty()) {
            ruleSet = SpatialRuleSet.EMPTY;
        } else {
            Collections.sort(applicableRules, SPATIAL_RULE_ID_COMP);
            ruleSet = new SpatialRuleSet(applicableRules);
        }
        
        return new LookupResult(applicableAreas, ruleSet);
    }
    
    @Override
    public List<CustomArea> getAreas() {
        return areas;
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
