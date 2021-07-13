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

import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.index.strtree.STRtree;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CustomAreaIndex {
    private final GeometryFactory gf;
    private final STRtree index;

    public CustomAreaIndex(List<CustomArea> customAreas) {
        gf = new GeometryFactory();
        index = new STRtree();
        PreparedGeometryFactory pgf = new PreparedGeometryFactory();
        for (CustomArea customArea : customAreas) {
            for (Polygon border : customArea.getBorders()) {
                IndexedCustomArea indexedCustomArea = new IndexedCustomArea(customArea, pgf.create(border));
                index.insert(border.getEnvelopeInternal(), indexedCustomArea);
            }
        }
        index.build();
    }

    public List<CustomArea> query(double lat, double lon) {
        Envelope searchEnv = new Envelope(lon, lon, lat, lat);
        @SuppressWarnings("unchecked")
        List<IndexedCustomArea> result = index.query(searchEnv);
        Point point = gf.createPoint(new Coordinate(lon, lat));
        return result.stream()
                .filter(c -> c.covers(point))
                .map(c -> c.customArea)
                .collect(Collectors.toList());
    }

    private static class IndexedCustomArea {
        private static final GeometryFactory FAC = new GeometryFactory();
        private static final int GRID_SIZE = 10;
        private static final double COORD_EPSILON = 0.00001;

        final CustomArea customArea;
        final PreparedGeometry preparedGeometry;
        final List<Envelope> filledLines;

        IndexedCustomArea(CustomArea customArea, PreparedGeometry preparedGeometry) {
            this.customArea = customArea;
            this.preparedGeometry = preparedGeometry;
            this.filledLines = findFilledLines(preparedGeometry);
        }

        boolean covers(Point point) {
//             todo: this is clearly slower
//            return preparedGeometry.getGeometry().intersects(point);
            // optimization: do a pre check before calling preparedGeometry#intersects
//            Coordinate coord = point.getCoordinate();
//            for (Envelope line : filledLines) {
//                if (line.covers(coord)) {
//                    return true;
//                }
//            }
            return preparedGeometry.intersects(point);
        }

        private static List<Envelope> findFilledLines(PreparedGeometry prepGeom) {
            // todo: copied this from SpatialRuleContainer
            List<Envelope> lines = new ArrayList<>();

            Envelope bbox = prepGeom.getGeometry().getEnvelopeInternal();
            double tileWidth = bbox.getWidth() / GRID_SIZE;
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

}


