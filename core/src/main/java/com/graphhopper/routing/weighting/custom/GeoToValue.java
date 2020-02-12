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
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometry;

public final class GeoToValue implements ConfigMapEntry {
    public static String key(String postfix) {
        return "area_" + postfix;
    }

    private final PreparedGeometry geometry;
    private final double value, elseValue;

    public GeoToValue(PreparedGeometry geometry, double value, double elseValue) {
        this.geometry = geometry;
        this.value = value;
        this.elseValue = elseValue;
    }

    public static Geometry pickGeo(CustomModel customModel, String id) {
        JsonFeature feature = customModel.getAreas().get(id);
        if (feature == null)
            throw new IllegalArgumentException("Cannot find area " + id);
        return feature.getGeometry();
    }

    @Override
    public double getValue(EdgeIteratorState iter, boolean reverse) {
        // TODO NOW PERFORMANCE: Do it like in BlockArea but here we have no NodeAccess!?
        return geometry.intersects(iter.fetchWayGeometry(3).toLineString(false)) ? value : elseValue;
    }

    @Override
    public String toString() {
        return geometry.toString() + ": " + value + ", " + elseValue;
    }
}
