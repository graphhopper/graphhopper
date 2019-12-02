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
package com.graphhopper.routing.profiles;

/**
 * Store valid values of the OSM tag mtb:scale=*. See
 * https://wiki.openstreetmap.org/wiki/Key:mtb:scale for details.
 * 
 * Values are stored as unsigned integers because the OSM key uses unsigned integers only.
 * 
 * @author Michael Reichert
 *
 */
public class MtbScale {
    public static final String KEY = "mtb_scale";

    /**
     * Return a encoder for unsigned integers from 0 (no scale or the invalid tag value "0") to
     * 7 (although the scale ends at "6" according to
     * https://wiki.openstreetmap.org/wiki/Key:mtb:scale).
     */
    public static UnsignedIntEncodedValue create() {
        return new UnsignedIntEncodedValue(KEY, 3, false);
    }
}