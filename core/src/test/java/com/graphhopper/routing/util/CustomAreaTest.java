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
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CustomAreaTest {

    /**
     * A GeoJSON feature is not required to carry a "properties" member. Custom area files without
     * properties must still load (GraphHopper#readCustomAreas passes such features here), and
     * OSMReader skips areas with null properties when looking for country subdivisions.
     * See docs/pinned-behavior.md.
     */
    @Test
    public void fromJsonFeatureWithoutProperties() {
        GeometryFactory gf = new GeometryFactory();
        Polygon border = gf.createPolygon(new Coordinate[]{
                new Coordinate(0, 0), new Coordinate(1, 0), new Coordinate(1, 1), new Coordinate(0, 0)});
        JsonFeature feature = new JsonFeature();
        feature.setGeometry(border);

        CustomArea customArea = CustomArea.fromJsonFeature(feature);
        assertNull(customArea.getProperties());
        assertEquals(List.of(border), customArea.getBorders());
    }
}
