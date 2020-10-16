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

/**
 * The mountain biking difficulty of an edge for uphill riding.
 * See <a href="https://wiki.openstreetmap.org/wiki/Key:mtb:scale">Key:mtb:scale</a> for details.
 */
public enum MtbUphillScale {
    NONE("none"),
    MTB_UPHILL_0("0"),
    MTB_UPHILL_1("1"),
    MTB_UPHILL_2("2"),
    MTB_UPHILL_3("3"),
    MTB_UPHILL_4("4"),
    MTB_UPHILL_5("5");

    public static final String KEY = "mtb_uphill_scale";

    private final String name;

    MtbUphillScale(String name) {
        this.name = name;
    }

    /**
     * Finds the {@link MtbUphillScale} from a {@link String}. This method properly converts values such as '1+' or '2-',
     * which are possible values according to the OSM tag description, by truncating the '+' or '-'.
     */
    public static MtbUphillScale find(String name) {
        if (name == null)
            return NONE;
        try {
            if (name.length() == 1)
                return MtbUphillScale.valueOf("MTB_UPHILL_" + name);
            if (name.length() == 2 && (name.charAt(1) == '+' || name.charAt(1) == '-'))
                return MtbUphillScale.valueOf("MTB_UPHILL_" + name.charAt(0));
            return NONE;
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
