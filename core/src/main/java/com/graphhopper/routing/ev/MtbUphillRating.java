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
 *
 * @see  <a href="https://wiki.openstreetmap.org/wiki/Key:mtb:scale">Key:mtb:scale</a> for details on OSM mountain biking difficulties.
 */
public enum MtbUphillRating {
    /**
     * No or unknown mountain biking difficulty.
     */
    NONE("none"),

    /**
     * Corresponds to OSM mtb:scale:uphill=0.
     */
    LEVEL_1("0"),

    /**
     * Corresponds to OSM mtb:scale:uphill=1.
     */
    LEVEL_2("1"),

    /**
     * Corresponds to OSM mtb:scale:uphill=2.
     */
    LEVEL_3("2"),

    /**
     * Corresponds to OSM mtb:scale:uphill=3.
     */
    LEVEL_4("3"),

    /**
     * Corresponds to OSM mtb:scale:uphill=4.
     */
    LEVEL_5("4"),

    /**
     * Corresponds to OSM mtb:scale:uphill=5.
     */
    LEVEL_6("5");

    public static final String KEY = "mtb_uphill_rating";

    private final String osmScale;

    MtbUphillRating(String osmScale) {
        this.osmScale = osmScale;
    }

    /**
     * Finds the {@link MtbUphillRating} from a {@link String} as it is found in the OSM mtb:scale:uphill tag.
     * This method properly converts values such as '1+' or '2-', which are possible values according to the OSM
     * tag description, by truncating the '+' or '-'.
     */
    public static MtbUphillRating find(String osmScale) {
        if (osmScale == null)
            return NONE;
        final String roundedScale;
        if (osmScale.length() == 1)
            roundedScale = osmScale;
        else if (osmScale.length() == 2 && (osmScale.charAt(1) == '+' || osmScale.charAt(1) == '-'))
            roundedScale = osmScale.substring(0,1);
        else
            return NONE;
        for (MtbUphillRating rating : MtbUphillRating.values()) {
            if (rating.osmScale.equals(roundedScale))
                return rating;
        }
        return NONE;
    }
}
