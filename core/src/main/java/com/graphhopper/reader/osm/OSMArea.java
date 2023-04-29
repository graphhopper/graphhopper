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
import org.locationtech.jts.geom.Polygon;

import java.util.Map;

public class OSMArea implements AreaIndex.Area {
    private final Map<String, Object> tags;
    private final Polygon border;
    private final double area;

    public OSMArea(Map<String, Object> tags, Polygon border) {
        this.tags = tags;
        this.border = border;
        this.area = border.getArea();
    }

    public Map<String, Object> getTags() {
        return tags;
    }

    public double getArea() {
        return area;
    }

    @Override
    public Polygon getBorder() {
        return border;
    }
}
