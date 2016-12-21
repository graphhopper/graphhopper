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

import java.util.ArrayList;
import java.util.List;

/**
 * Created by robin on 12.12.16.
 */
public class GeoJsonPolygon implements Geometry {

    private List<Polygon> polygons = new ArrayList<>();


    @Override
    public String getType() {
        return null;
    }

    @Override
    public boolean isPoint() {
        return false;
    }

    @Override
    public GHPoint asPoint() {
        return null;
    }

    @Override
    public boolean isPointList() {
        return false;
    }

    @Override
    public PointList asPointList() {
        return null;
    }

    @Override
    public boolean isPolygon() {
        return true;
    }

    @Override
    public GeoJsonPolygon asPolygon() {
        return this;
    }

    public List<Polygon> getPolygons() {
        return polygons;
    }

    public void addPolygon(Polygon polygon) {
        this.polygons.add(polygon);
    }
}
