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

/**
 * Wrapper to read a PointList easily from GeoJSON (type=LineString)
 *
 * @author Peter Karich
 */
public class LineString extends PointList implements Geometry {
    public LineString(int size, boolean is3D) {
        super(size, is3D);
    }

    @Override
    public String getType() {
        return "LineString";
    }

    @Override
    public boolean isPoint() {
        return false;
    }

    @Override
    public GHPoint asPoint() {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public boolean isPointList() {
        return true;
    }

    @Override
    public PointList asPointList() {
        return this;
    }

    @Override
    public boolean isPolygon() {
        return false;
    }

    @Override
    public GeoJsonPolygon asPolygon() {
        throw new UnsupportedOperationException("Not supported");
    }
}
