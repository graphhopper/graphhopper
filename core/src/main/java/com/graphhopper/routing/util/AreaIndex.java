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

    public AreaIndex(List<T> areas) {
        gf = new GeometryFactory();
        index = new STRtree();
        PreparedGeometryFactory pgf = new PreparedGeometryFactory();
        for (T area : areas) {
            for (Polygon border : area.getBorders())
                addBorder(pgf, area, border);
            if (area.getBorder() != null)
                addBorder(pgf, area, area.getBorder());
        }
        index.build();
    }

    private void addBorder(PreparedGeometryFactory pgf, T area, Polygon border) {
        IndexedCustomArea<T> indexedCustomArea = new IndexedCustomArea<>(area, pgf.create(border));
        index.insert(border.getEnvelopeInternal(), indexedCustomArea);
    }

    public List<T> query(double lat, double lon) {
        Envelope searchEnv = new Envelope(lon, lon, lat, lat);
        @SuppressWarnings("unchecked")
        List<IndexedCustomArea<T>> result = index.query(searchEnv);
        Point point = gf.createPoint(new Coordinate(lon, lat));
        return result.stream()
                .filter(c -> c.intersects(point))
                .map(c -> c.area)
                .collect(Collectors.toList());
    }

    private static class IndexedCustomArea<T extends Area> {
        final T area;
        final PreparedGeometry preparedGeometry;

        IndexedCustomArea(T area, PreparedGeometry preparedGeometry) {
            this.area = area;
            this.preparedGeometry = preparedGeometry;
        }

        boolean intersects(Point point) {
            return preparedGeometry.intersects(point);
        }
    }

}


