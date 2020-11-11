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
package com.graphhopper.routing.weighting.custom;

import com.graphhopper.json.geo.JsonFeature;
import com.graphhopper.routing.util.CustomModel;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.Polygon;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometry;

final class GeoToValueEntry implements EdgeToValueEntry {
    static final String AREA_PREFIX = "area_";
    private final Polygon ghPolygon;
    private final double value, elseValue;

    private GeoToValueEntry(PreparedGeometry geometry, double value, double elseValue) {
        this.ghPolygon = new Polygon(geometry);
        this.value = value;
        this.elseValue = elseValue;
    }

    static Geometry pickGeometry(CustomModel customModel, String key) {
        String id = key.substring(AREA_PREFIX.length());
        JsonFeature feature = customModel.getAreas().get(id);
        if (feature == null)
            throw new IllegalArgumentException("Cannot find area " + id);
        if (feature.getGeometry() == null)
            throw new IllegalArgumentException("Cannot find coordinates of area " + id);
        return feature.getGeometry();
    }

    public static EdgeToValueEntry create(String name, PreparedGeometry preparedGeometry, Number value, double defaultValue,
                                          double minValue, double maxValue) {
        double number = value.doubleValue();
        if (number < minValue)
            throw new IllegalArgumentException(name + " cannot be smaller than " + minValue + ", was " + number);
        if (number > maxValue)
            throw new IllegalArgumentException(name + " cannot be bigger than " + maxValue + ", was " + number);

        return new GeoToValueEntry(preparedGeometry, number, defaultValue);
    }

    @Override
    public double getValue(EdgeIteratorState edgeState, boolean reverse) {
        BBox bbox = GHUtility.createBBox(edgeState);
        if (ghPolygon.getBounds().intersects(bbox)) {
            if (ghPolygon.intersects(edgeState.fetchWayGeometry(FetchMode.ALL).makeImmutable()))
                return value;
        }
        return elseValue;
    }

    @Override
    public String toString() {
        return ghPolygon.toString() + ": " + value + ", " + elseValue;
    }
}
