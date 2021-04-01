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
package com.graphhopper.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.locationtech.jts.geom.Polygon;

import com.graphhopper.routing.util.spatialrules.SpatialRule;

/**
 * Defines a named area which can be encoded in the graph and be targeted with a {@link SpatialRule}
 * 
 * @author Thomas Butz
 */
public class CustomArea {
    public static final String CUSTOM_EV_SUFFIX = "_area";

    private final String id;
    private final List<Polygon> borders;
    private final String encodedValue;
    
    public CustomArea(String id, List<Polygon> borders, String encodedValuePrefix) {
        this.id = id;
        this.borders = Collections.unmodifiableList(new ArrayList<>(borders));
        if (encodedValuePrefix != null && !encodedValuePrefix.isEmpty()) {
            this.encodedValue = key(encodedValuePrefix);
        } else {
            this.encodedValue = "";
        }
    }
    
    public CustomArea(String id, List<Polygon> borders) {
        this(id, borders, "");
    }
    
    public static String key(String str) {
        return str + CUSTOM_EV_SUFFIX;
    }
    
    public String getId() {
        return id;
    }

    public List<Polygon> getBorders() {
        return borders;
    }
    
    public String getEncodedValue() {
        return encodedValue;
    }

    @Override
    public String toString() {
        return getId();
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof CustomArea)) {
            return false;
        }
        CustomArea other = (CustomArea) obj;
        return Objects.equals(id, other.id);
    }
    
    
}
