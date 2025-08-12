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

import com.graphhopper.routing.util.NoiseIndex;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.impl.PackedCoordinateSequence;

import java.util.Arrays;

public class OSMNoisyRoad implements NoiseIndex.Road {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
    final PackedCoordinateSequence.Float road;

    public OSMNoisyRoad(int numNodes) {
        float[] coords = new float[numNodes * 2];
        Arrays.fill(coords, Float.NaN);
        road = new PackedCoordinateSequence.Float(coords, 2, 0);
    }

    public void setCoordinate(int index, double lat, double lon) {
        // todonow: precision/double->float cast?
        road.setX(index, (float) lon);
        road.setY(index, (float) lat);
    }

    public boolean isValid() {
        float[] coords = road.getRawCoordinates();
        for (float f : coords)
            if (Float.isNaN(f))
                return false;
        return coords.length >= 2 ;
    }

    public LineString getRoad() {
        return GEOMETRY_FACTORY.createLineString(this.road);
    }
}
