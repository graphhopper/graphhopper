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
 * The hiking difficulty of an edge.
 *
 * @see  <a href="https://wiki.openstreetmap.org/wiki/Key:sac_scale">Key:sac_scale</a> for details on OSM hiking difficulties.
 */
public enum HikingRating {

    /**
     * No or unknown hiking difficulty.
     */
    NONE("none"),

    /**
     * Normal hiking. Corresponds to OSM sac_scale=hiking.
     */
    LEVEL_1("hiking"),

    /**
     * Mountain hiking. Corresponds to OSM sac_scale=mountain_hiking.
     */
    LEVEL_2("mountain_hiking"),

    /**
     * Demanding mountain hiking. Corresponds to OSM sac_scale=demanding_mountain_hiking.
     */
    LEVEL_3("demanding_mountain_hiking"),

    /**
     * Alpine hiking. Corresponds to OSM sac_scale=alpine_hiking.
     */
    LEVEL_4("alpine_hiking"),

    /**
     * Demanding alpine hiking. Corresponds to OSM sac_scale=demanding_alpine_hiking.
     */
    LEVEL_5("demanding_alpine_hiking"),

    /**
     * Difficult alpine hiking. Corresponds to OSM sac_scale=difficult_alpine_hiking.
     */
    LEVEL_6("difficult_alpine_hiking");

    public static final String KEY = "hiking_rating";

    private final String osmScale;

    HikingRating(String osmScale) {
        this.osmScale = osmScale;
    }

    /**
     * Finds the {@link HikingRating} from a {@link String} as it is found in the OSM sac_scale tag.
     */
    public static HikingRating find(String osmScale) {
        if (osmScale == null)
            return NONE;
        for (HikingRating rating : HikingRating.values()) {
            if (rating.osmScale.equals(osmScale))
                return rating;
        }
        return NONE;
    }
}
