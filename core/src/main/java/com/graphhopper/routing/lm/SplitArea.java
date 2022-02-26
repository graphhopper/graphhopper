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

package com.graphhopper.routing.lm;

import com.graphhopper.routing.util.AreaIndex;
import com.graphhopper.util.JsonFeature;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.util.PolygonExtracter;

import java.util.ArrayList;
import java.util.List;

public class SplitArea implements AreaIndex.Area {
    private final List<Polygon> borders;

    public static SplitArea fromJsonFeature(JsonFeature j) {
        List<Polygon> borders = new ArrayList<>();
        for (int i = 0; i < j.getGeometry().getNumGeometries(); i++) {
            Geometry geometry = j.getGeometry().getGeometryN(i);
            if (geometry instanceof Polygon)
                PolygonExtracter.getPolygons(geometry, borders);
            else
                throw new IllegalArgumentException("GeoJson features used to create split areas must be of type 'Polygon', but was: " + geometry.getClass().getSimpleName());
        }
        return new SplitArea(borders);
    }

    public SplitArea(List<Polygon> borders) {
        this.borders = borders;
    }

    @Override
    public List<Polygon> getBorders() {
        return borders;
    }
}
