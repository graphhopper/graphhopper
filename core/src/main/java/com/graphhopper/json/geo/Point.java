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
package com.graphhopper.json.geo;

import com.graphhopper.routing.util.spatialrules.Polygon;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;
import com.graphhopper.util.shapes.GHPoint3D;

/**
 * Wrapper to read a GHPoint3D easily from GeoJSON (type=Point)
 *
 * @author Peter Karich
 */
public class Point extends GHPoint3D implements Geometry {
    public Point(double lat, double lon) {
        super(lat, lon, Double.NaN);
    }

    public Point(double lat, double lon, double ele) {
        super(lat, lon, ele);
    }

    @Override
    public String toString() {
        return lat + ", " + lon;
    }

    @Override
    public boolean isPoint() {
        return true;
    }

    @Override
    public GHPoint asPoint() {
        return this;
    }

    @Override
    public boolean isPointList() {
        return false;
    }

    @Override
    public PointList asPointList() {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public boolean isPolygon() {
        return false;
    }

    @Override
    public GeoJsonPolygon asPolygon() {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public String getType() {
        return "Point";
    }
}
