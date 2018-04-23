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
package com.graphhopper.routing.util.spatialrules;

import com.graphhopper.util.shapes.Polygon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Robin Boldt
 */
public abstract class AbstractSpatialRule implements SpatialRule {

    protected List<Polygon> polygons = Collections.EMPTY_LIST;

    public List<Polygon> getBorders() {
        return polygons;
    }

    public SpatialRule setBorders(List<Polygon> polygons) {
        this.polygons = polygons;
        return this;
    }

    public SpatialRule addBorder(Polygon polygon) {
        if (polygons.isEmpty())
            polygons = new ArrayList<>();
        this.polygons.add(polygon);
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SpatialRule) {
            if (((SpatialRule) obj).getId().equals(this.getId()))
                return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return getId().hashCode();
    }

    @Override
    public String toString() {
        return getId();
    }
}
