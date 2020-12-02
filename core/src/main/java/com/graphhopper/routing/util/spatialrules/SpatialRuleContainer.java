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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
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
    private static final GeometryFactory FAC = new GeometryFactory();
    private static final int GRID_SIZE = 10;
    private static final double COORD_EPSILON = 0.00001;

    private final PreparedGeometry preparedPolygon;
    private final Set<SpatialRule> rules = new LinkedHashSet<>();
    private final List<Envelope> filledLines;
    
    public SpatialRuleContainer(Polygon polygon) {
        this(PREP_GEOM_FACTORY.create(polygon));
    }
    
    private SpatialRuleContainer(PreparedGeometry preparedPolygon) {
        this.preparedPolygon = preparedPolygon;
        this.filledLines = findFilledLines(preparedPolygon);
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

    public boolean covers(Point point) {
        Coordinate coord = point.getCoordinate();
        
        for (Envelope line : filledLines) {
            if (line.covers(coord)) {
                return true;
            }
        }
        
        return preparedPolygon.intersects(point);
    }

    public SpatialRuleContainer copy() {
        SpatialRuleContainer container = new SpatialRuleContainer(this.preparedPolygon);
        container.rules.addAll(this.rules);
        return container;
    }
    
    private static List<Envelope> findFilledLines(PreparedGeometry prepGeom) {
        List<Envelope> lines = new ArrayList<>();
        
        Envelope bbox = prepGeom.getGeometry().getEnvelopeInternal();
        double tileWidth  = bbox.getWidth()  / GRID_SIZE;
        double tileHeight = bbox.getHeight() / GRID_SIZE;

        Envelope tile = new Envelope();
        Envelope line;
        for (int row = 0; row < GRID_SIZE; row++) {
            line = null;
            for (int column = 0; column < GRID_SIZE; column++) {
                double minX = bbox.getMinX() + (column * tileWidth);
                double minY = bbox.getMinY() + (row * tileHeight);
                tile.init(minX, minX + tileWidth, minY, minY + tileHeight);
                
                if (prepGeom.covers(FAC.toGeometry(tile))) {
                    if (line != null && Math.abs(line.getMaxX() - tile.getMinX()) < COORD_EPSILON) {
                        line.expandToInclude(tile);
                    } else {
                        line = new Envelope(tile);
                        lines.add(line);
                    }
                }
            }
        }
        
        return lines;
    }
}
