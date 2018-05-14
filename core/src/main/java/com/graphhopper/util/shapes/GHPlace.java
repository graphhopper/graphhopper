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
package com.graphhopper.util.shapes;

import com.graphhopper.util.Helper;

/**
 * Specifies a place by its coordinates or name
 * <p>
 *
 * @author Peter Karich
 */
public class GHPlace extends GHPoint {
    private String name = "";

    public GHPlace() {
    }

    public GHPlace(String name) {
        setName(name);
    }

    public GHPlace(double lat, double lon) {
        super(lat, lon);
    }

    public void setValue(String t) {
        setName(t);
    }

    public String getName() {
        return name;
    }

    public GHPlace setName(String name) {
        this.name = name;
        return this;
    }

    public boolean isValidName() {
        return !Helper.isEmpty(name);
    }

    @Override
    public String toString() {
        String str = "";
        if (isValidName())
            str += name;

        if (isValid())
            str += " " + lat + ", " + lon;

        return str.trim();
    }
}
