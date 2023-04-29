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

package com.graphhopper.reader.osm;

import com.graphhopper.routing.util.AreaIndex;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.impl.PackedCoordinateSequence;

import java.util.Arrays;
import java.util.Map;

public class OSMArea implements AreaIndex.Area {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
    final PackedCoordinateSequence.Float border;
    private final Map<String, Object> tags;
    private double area = -1;

    public OSMArea(int numNodes, Map<String, Object> tags) {
        float[] coords = new float[numNodes * 2];
        Arrays.fill(coords, Float.NaN);
        border = new PackedCoordinateSequence.Float(coords, 2, 0);
        this.tags = tags;
    }

    public void setCoordinate(int index, double lat, double lon) {
        if (area >= 0)
            throw new IllegalStateException("Cannot modify coordinates after getBorder() was called");
        // todonow: precision/double->float cast?
        border.setX(index, (float) lon);
        border.setY(index, (float) lat);
    }

    public boolean isValid() {
        float[] coords = border.getRawCoordinates();
        for (float f : coords)
            if (Float.isNaN(f))
                return false;
        // todonow: print warnings for non-closed geometries and missing coordinates?
        return coords.length >= 8 && coords[0] == coords[coords.length - 2] && coords[1] == coords[coords.length - 1];
    }

    public Map<String, Object> getTags() {
        return tags;
    }

    public double getArea() {
        return area;
    }

    @Override
    public Polygon getBorder() {
        Polygon border = GEOMETRY_FACTORY.createPolygon(this.border);
        area = border.getArea();
        return border;
    }
}
