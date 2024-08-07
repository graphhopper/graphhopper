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

package com.graphhopper.routing.util;

import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.shapes.BBox;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.index.strtree.STRtree;

import java.util.List;

public class NoiseIndex<T extends NoiseIndex.Road> {
    GeometryFactory gf;
    private final STRtree index;
    private final DistanceCalc dc;

    public interface Road {
        default LineString getRoad() { return null; }
    }

    public NoiseIndex(List<T> roads) {
        gf = new GeometryFactory();
        index = new STRtree();
        dc = new DistanceCalcEarth();
        PreparedGeometryFactory pgf = new PreparedGeometryFactory();
        for (T road : roads) {
            LineString lineString = road.getRoad();
            IndexedNoisyRoad<T> indexedNoisyRoad = new IndexedNoisyRoad<>(road, pgf.create(lineString));
            index.insert(lineString.getEnvelopeInternal(), indexedNoisyRoad);
        }
        index.build();
    }

    public boolean query(double lat, double lon)    {
        if (Double.isNaN(lat) || Double.isNaN(lon))
            return false;
        BBox bbox = dc.createBBox(lat, lon, 15);
        Envelope searchEnv = new Envelope(bbox.minLon, bbox.maxLon, bbox.minLat, bbox.maxLat);
        List<IndexedNoisyRoad<T>> result = index.query(searchEnv);

        if (!result.isEmpty()) {
            Coordinate[] coordinates = new Coordinate[]{
                    new Coordinate(bbox.minLon, bbox.minLat),
                    new Coordinate(bbox.minLon, bbox.maxLat),
                    new Coordinate(bbox.maxLon, bbox.maxLat),
                    new Coordinate(bbox.maxLon, bbox.minLat),
                    new Coordinate(bbox.minLon, bbox.minLat) // Closing the loop
            };
            LinearRing linearRing = gf.createLinearRing(coordinates);
            Polygon rectangle = gf.createPolygon(linearRing, null);
            return !result.stream()
                    .filter(c -> c.intersects(rectangle))
                    .map(c -> c.road)
                    .toList().isEmpty();
        } else {
            return false;
        }
    }

    private static class IndexedNoisyRoad<T extends Road> {
        final T road;
        final PreparedGeometry preparedGeometry;

        IndexedNoisyRoad(T road, PreparedGeometry preparedGeometry) {
            this.road = road;
            this.preparedGeometry = preparedGeometry;
        }

        boolean intersects(Geometry geometry)  {
            return preparedGeometry.intersects(geometry);
        }

    }
}
