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

/**
 * @author Peter Karich
 */
public class HintsMap extends PMap {
    public HintsMap() {
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
    public HintsMap put(String key, Object str) {
        super.put(key, str);
        return this;
    }

    public String getWeighting() {
        return super.get("weighting", "").toLowerCase();
    }

    public HintsMap setWeighting(String w) {
        if (w != null)
            super.put("weighting", w);
        return this;
    }

    public String getVehicle() {
        return super.get("vehicle", "").toLowerCase();
    }

    public HintsMap setVehicle(String v) {
        if (v != null)
            super.put("vehicle", v);
        return this;
    }
}
