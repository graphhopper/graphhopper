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
 * The horseback riding difficulty of an edge.
 *
 * @see  <a href="https://wiki.openstreetmap.org/wiki/Key:horse_scale">Key:horse_scale</a> for details on horseback riding difficulties.
 */
public enum HorseRating {

    /**
     * No or unknown horseback riding difficulty.
     */
    NONE("none"),

    /**
     * Common horseback riding. Corresponds to OSM horse_scale=common.
     */
    LEVEL_1("common"),

    /**
     * Demanding horseback riding. Corresponds to OSM horse_scale=demanding.
     */
    LEVEL_2("demanding"),

    /**
     * Difficult horseback riding. Corresponds to OSM horse_scale=difficult.
     */
    LEVEL_3("difficult"),

    /**
     * Critically difficult horseback riding. Corresponds to OSM horse_scale=critical.
     */
    LEVEL_4("critical"),

    /**
     * Dangerously difficult horseback riding. Corresponds to OSM horse_scale=dangerous.
     */
    LEVEL_5("dangerous"),

    /**
     * Impossible for horseback riding. Corresponds to OSM horse_scale=impossible.
     */
    LEVEL_6("impossible");

    public static final String KEY = "horse_rating";

    private final String osmScale;

    HorseRating(String osmScale) {
        this.osmScale = osmScale;
    }

    /**
     * Finds the {@link HorseRating} from a {@link String} as it is found in the OSM horse_scale tag.
     */
    public static HorseRating find(String osmScale) {
        if (osmScale == null)
            return NONE;
        for (HorseRating rating : HorseRating.values()) {
            if (rating.osmScale.equals(osmScale))
                return rating;
        }
        return NONE;
    }
}
