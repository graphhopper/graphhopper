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

import com.graphhopper.util.JsonFeature;
import org.locationtech.jts.geom.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CustomArea implements AreaIndex.Area {
    private final Map<String, Object> properties;
    private final List<Polygon> borders;
    private final double area;

    public static CustomArea fromJsonFeature(JsonFeature j) {
        List<Polygon> borders = new ArrayList<>();
        for (int i = 0; i < j.getGeometry().getNumGeometries(); i++) {
            Geometry geometry = j.getGeometry().getGeometryN(i);
            if (geometry instanceof Polygon) {
                borders.add((Polygon) geometry);
            } else {
                throw new IllegalArgumentException("Custom area features must be of type 'Polygon', but was: " + geometry.getClass().getSimpleName());
            }
        }
        return new CustomArea(j.getProperties(), borders);
    }

    public CustomArea(Map<String, Object> properties, List<Polygon> borders) {
        this.properties = properties;
        this.borders = borders;
        this.area = borders.stream().map(Polygon::getArea).reduce(0d, Double::sum);
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    @Override
    public List<Polygon> getBorders() {
        return borders;
    }

    public double getArea() {
        return area;
    }
}
