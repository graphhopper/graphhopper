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

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class AreaIndex<T extends AreaIndex.Area> {

    public interface Area {
        default List<Polygon> getBorders() {
            return Collections.emptyList();
        }

        default Polygon getBorder() {
            return null;
        }
    }

    private final GeometryFactory gf;
    private final STRtree index;

    private final PreparedGeometryFactory pgf;

    private final DistanceCalc dc;

    public AreaIndex(List<T> areas) {
        gf = new GeometryFactory();
        pgf = new PreparedGeometryFactory();
        dc = new DistanceCalcEarth();
        index = new STRtree();
        for (T area : areas) {
            for (Polygon border : area.getBorders())
                addBorder( area, border);
            if (area.getBorder() != null)
                addBorder( area, area.getBorder());
        }
        index.build();
    }

    private void addBorder( T area, Polygon border) {
        IndexedArea<T> indexedOsmArea = new IndexedArea<>(area, border);
        index.insert(border.getEnvelopeInternal(), indexedOsmArea);
    }

    public List<T> query(double lat, double lon) {
        BBox bbox = dc.createBBox(lon, lat, 10);
        Envelope searchEnv = new Envelope(bbox.minLon, bbox.maxLon, bbox.minLat, bbox.maxLat);
        @SuppressWarnings("unchecked")
        List<IndexedArea<T>> result = index.query(searchEnv);
        PreparedGeometry poly = pgf.create(gf.toGeometry(searchEnv));
        return result.stream()
                .filter(c -> c.intersects(poly))
                .map(c -> c.area)
                .collect(Collectors.toList());
    }

    private static class IndexedArea<T extends Area> {
        final T area;
        final Geometry geometry;

        IndexedArea(T area, Geometry geometry) {
            this.area = area;
            this.geometry = geometry;
        }

        boolean intersects(PreparedGeometry poly) {
            return poly.intersects(geometry);
        }

    }

}


