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

import com.graphhopper.util.PMap;

import static com.graphhopper.util.Helper.toLowerCase;

/**
 * @author Peter Karich
 */
public class HintsMap extends PMap {
    public HintsMap() {
    }

    public HintsMap(int capacity) {
        super(capacity);
    }

    /**
     * Convenient constructor if only one parameter is provided
     */
    public HintsMap(String weighting) {
        super(5);
        setWeighting(weighting);
    }

    public HintsMap(PMap map) {
        super(map);
    }

    public HintsMap(HintsMap map) {
        super(map.toMap());
    }

    @Override
    public HintsMap putObject(String key, Object object) {
        super.putObject(key, object);
        return this;
    }

    @Override
    @Deprecated
    public HintsMap put(String key, String string) {
        super.put(key, string);
        return this;
    }

    public String getWeighting() {
        return toLowerCase(super.getString("weighting", ""));
    }

    public HintsMap setWeighting(String w) {
        if (w != null)
            super.putObject("weighting", w);
        return this;
    }

    public String getVehicle() {
        return toLowerCase(super.getString("vehicle", ""));
    }

    public HintsMap setVehicle(String v) {
        if (v != null)
            super.putObject("vehicle", v);
        return this;
    }
}
