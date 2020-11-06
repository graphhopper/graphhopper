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
 * The mountain biking difficulty of an edge.
 *
 * @see <a href="https://wiki.openstreetmap.org/wiki/Key:mtb:scale">Key:mtb:scale</a> for details on OSM mountain biking difficulties.
 * @see <a href=""http://www.singletrail-skala.de/>Single Trail Scale</a>
 */
public enum MtbRating {
    /**
     * No or unknown mountain biking difficulty.
     */
    NONE("none"),

    /**
     * Corresponds to OSM mtb:scale=0, or S0 on the single trail scale.
     */
    LEVEL_1("0"),

    /**
     * Corresponds to OSM mtb:scale=1, or S1 on the single trail scale.
     */
    LEVEL_2("1"),

    /**
     * Corresponds to OSM mtb:scale=2, or S2 on the single trail scale.
     */
    LEVEL_3("2"),

    /**
     * Corresponds to OSM mtb:scale=3, or S3 on the single trail scale.
     */
    LEVEL_4("3"),

    /**
     * Corresponds to OSM mtb:scale=4 or S4 on the single trail scale.
     */
    LEVEL_5("4"),

    /**
     * Corresponds to OSM mtb:scale=5 or S5 on the single trail scale.
     */
    LEVEL_6("5"),

    /**
     * Corresponds to OSM mtb:scale=6. There is no rating for this on the single trail scale.
     */
    LEVEL_7("6");

    public static final String KEY = "mtb_rating";

    private final String osmScale;

    MtbRating(String osmScale) {
        this.osmScale = osmScale;
    }

    /**
     * Finds the {@link MtbRating} from a {@link String} as it is found in the OSM mtb:scale tag.
     * This method properly converts values such as '1+' or '2-', which are possible values according to the OSM
     * tag description, by truncating the '+' or '-'.
     */
    public static MtbRating find(String osmScale) {
        if (osmScale == null)
            return NONE;
        final String roundedScale;
        if (osmScale.length() == 1)
            roundedScale = osmScale;
        else if (osmScale.length() == 2 && (osmScale.charAt(1) == '+' || osmScale.charAt(1) == '-'))
            roundedScale = osmScale.substring(0,1);
        else
            return NONE;
        for (MtbRating rating : MtbRating.values()) {
            if (rating.osmScale.equals(roundedScale))
                return rating;
        }
        return NONE;
    }
}
