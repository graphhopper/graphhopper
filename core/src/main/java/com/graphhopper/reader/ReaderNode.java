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

import com.graphhopper.util.PointAccess;

/**
 * Represents a node received from the reader.
 * <p>
 *
 * @author Nop
 */
public class ReaderNode extends ReaderElement {
    private final double lat;
    private final double lon;

    public ReaderNode(long id, PointAccess pointAccess, int accessId) {
        super(id, NODE);

        this.lat = pointAccess.getLatitude(accessId);
        this.lon = pointAccess.getLongitude(accessId);
        if (pointAccess.is3D())
            setTag("ele", pointAccess.getElevation(accessId));
    }

    public ReaderNode(long id, double lat, double lon) {
        super(id, NODE);

        this.lat = lat;
        this.lon = lon;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

    public double getEle() {
        Object ele = getTags().get("ele");
        if (ele == null)
            return Double.NaN;
        return (Double) ele;
    }

    @Override
    public void setTag(String name, Object value) {
        if ("ele".equals(name)) {
            if (value == null)
                value = null;
            else if (value instanceof String) {
                String str = (String) value;
                str = str.trim().replaceAll("\\,", ".");
                if (str.isEmpty())
                    value = null;
                else
                    try {
                        value = Double.parseDouble(str);
                    } catch (NumberFormatException ex) {
                        return;
                    }
            } else
                // force cast
                value = ((Number) value).doubleValue();
        }
        super.setTag(name, value);
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
        if (!getTags().isEmpty()) {
            txt.append("\n");
            txt.append(tagsToString());
        }
        return txt.toString();
    }
}
