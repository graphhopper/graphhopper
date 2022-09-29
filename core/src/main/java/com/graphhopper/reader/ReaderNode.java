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
package com.graphhopper.reader;

import java.util.Map;

/**
 * Represents a node received from the reader.
 * <p>
 *
 * @author Nop
 */
public class ReaderNode extends ReaderElement {
    private final double lat;
    private final double lon;

    public ReaderNode(long id, double lat, double lon) {
        super(id, Type.NODE);
        this.lat = lat;
        this.lon = lon;
    }

    public ReaderNode(long id, double lat, double lon, Map<String, Object> tags) {
        super(id, Type.NODE, tags);
        this.lat = lat;
        this.lon = lon;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

    @Override
    public String toString() {
        StringBuilder txt = new StringBuilder();
        txt.append("Node: ");
        txt.append(getId());
        txt.append(" lat=");
        txt.append(getLat());
        txt.append(" lon=");
        txt.append(getLon());
        if (hasTags()) {
            txt.append("\n");
            txt.append(tagsToString());
        }
        return txt.toString();
    }
}
