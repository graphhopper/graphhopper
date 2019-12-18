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

import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;

/**
 * This class contains a collection of SpatialRule which are valid for a certain Polygon.
 *
 * @author Robin Boldt
 * @author Thomas Butz
 */
class SpatialRuleContainer {
    private static final PreparedGeometryFactory PREP_GEOM_FACTORY = new PreparedGeometryFactory();

    private final PreparedGeometry preparedPolygon;
    private final Set<SpatialRule> rules = new LinkedHashSet<>();

    
    public SpatialRuleContainer(Polygon polygon) {
        this(PREP_GEOM_FACTORY.create(polygon));
    }
    
    private SpatialRuleContainer(PreparedGeometry preparedPolygon) {
        this.preparedPolygon = preparedPolygon;
    }

    public void addRule(SpatialRule spatialRule) {
        rules.add(spatialRule);
    }

    /**
     * Returns a list of all spatial rules including the EMPTY one.
     */
    public Collection<SpatialRule> getRules() {
        return rules;
    }

    public int size() {
        return this.rules.size();
    }

    public boolean containsProperly(Point point) {
        return preparedPolygon.containsProperly(point);
    }

    public SpatialRuleContainer copy() {
        SpatialRuleContainer container = new SpatialRuleContainer(this.preparedPolygon);
        container.rules.addAll(this.rules);
        return container;
    }
}
