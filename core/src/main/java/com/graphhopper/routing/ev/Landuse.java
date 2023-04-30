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

package com.graphhopper.routing.ev;

import com.graphhopper.util.Helper;

public enum Landuse {
    OTHER("other"), FARMLAND("farmland"), RESIDENTIAL("residential"),
    GRASS("grass"), FOREST("forest"), MEADOW("meadow"), ORCHARD("orchard"), FARMYARD("farmyard"),
    INDUSTRIAL("industrial"), VINEYARD("vineyard"), CEMETERY("cemetery"), COMMERCIAL("commercial"), ALLOTMENTS("allotments"),
    RETAIL("retail"), BASIN("basin"), RESERVOIR("reservoir"), CONSTRUCTION("construction"), QUARRY("quarry");

    public static final String KEY = "landuse";

    private final String name;

    Landuse(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    public static Landuse find(String name) {
        if (name == null)
            return OTHER;
        try {
            return Landuse.valueOf(Helper.toUpperCase(name));
        } catch (IllegalArgumentException ex) {
            return OTHER;
        }
    }
}
