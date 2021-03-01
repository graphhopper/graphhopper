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
package com.graphhopper.util;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;

import java.util.Map;

/**
 * This class defines a properties where a geometry is associated. Typically read from GeoJSON but also from in-memory is possible.
 *
 * @author Peter Karich
 */
public class JsonFeature {
    private String id;
    private String type = "Feature";
    private Envelope bbox;
    private Geometry geometry;
    private Map<String, Object> properties;

    public JsonFeature() {
    }

    public JsonFeature(String id, String type, Envelope bbox, Geometry geometry, Map<String, Object> properties) {
        this.id = id;
        this.type = type;
        this.bbox = bbox;
        this.geometry = geometry;
        this.properties = properties;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public Envelope getBBox() {
        return bbox;
    }

    public Geometry getGeometry() {
        return geometry;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public Object getProperty(String key) {
        return properties.get(key);
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setBBox(Envelope bbox) {
        this.bbox = bbox;
    }

    public void setGeometry(Geometry geometry) {
        this.geometry = geometry;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    @Override
    public String toString() {
        return "id:" + getId();
    }
}
