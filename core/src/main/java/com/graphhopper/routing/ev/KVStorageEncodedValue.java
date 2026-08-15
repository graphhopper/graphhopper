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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.graphhopper.util.Parameters;

import java.util.Map;

/**
 * An EncodedValue that represents a key-value tag stored in KVStorage. Unlike other EncodedValues,
 * this does not use any bits in the edge flags. Instead, it holds a pre-resolved key index for fast
 * KVStorage lookups, avoiding the HashMap lookup on every edge access.
 * <p>
 * The {@link #getName()} returns the sanitized field name (e.g. "kv_cycleway_left") while
 * {@link #getRawTagName()} returns the original OSM key (e.g. "cycleway:left").
 */
public class KVStorageEncodedValue implements EncodedValue {
    @JsonIgnore
    private final String name;
    private final String rawTagName;
    private int keyIndex = -1;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public KVStorageEncodedValue(@JsonProperty("raw_tag_name") String rawTagName) {
        this.rawTagName = rawTagName;
        this.name = toFieldName(rawTagName);
    }

    /**
     * The OSMReader always stores these OSM keys under a different KVStorage key, so point to them instead
     * of storing them twice. destination and destination:ref are stored per direction, i.e. tag('destination')
     * returns the value for the direction of travel.
     */
    public static final Map<String, String> ALIASES = Map.of(
            "name", Parameters.Details.STREET_NAME,
            "ref", Parameters.Details.STREET_REF,
            "destination", Parameters.Details.STREET_DESTINATION,
            "destination:ref", Parameters.Details.STREET_DESTINATION_REF);

    /** @return the KVStorage key for the given OSM key, e.g. 'name' -> 'street_name' */
    public static String resolveAlias(String osmKey) {
        return ALIASES.getOrDefault(osmKey, osmKey);
    }

    /** @return the OSM key for this EncodedValue, e.g. 'name' for the KVStorage key 'street_name' */
    public String getOSMKey() {
        for (Map.Entry<String, String> e : ALIASES.entrySet())
            if (e.getValue().equals(rawTagName)) return e.getKey();
        return rawTagName;
    }

    /** @return true if the OSMReader stores this key anyway, so it must not be listed in stored_tags */
    public static boolean isStoredAutomatically(String osmKey) {
        return ALIASES.containsKey(osmKey) || ALIASES.containsValue(osmKey);
    }

    /**
     * Sanitizes an OSM key (e.g. "cycleway:left") to a valid Java identifier (e.g. "kv_cycleway_left").
     */
    public static String toFieldName(String osmKey) {
        StringBuilder sb = new StringBuilder("kv_");
        for (int i = 0; i < osmKey.length(); i++) {
            char c = osmKey.charAt(i);
            if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_')
                sb.append(c);
            else if (c == ':')
                sb.append('_');
            else
                // otherwise e.g. 'XY:ab' would silently become 'kv____ab'
                throw new IllegalArgumentException("Illegal character '" + c + "' in tag key: " + osmKey);
        }
        return sb.toString();
    }

    @Override
    public int init(InitializerConfig init) {
        // no bits needed in edge flags
        return 0;
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * @return the original OSM key (e.g. "cycleway:left") used for KVStorage operations
     */
    public String getRawTagName() {
        return rawTagName;
    }

    @Override
    public boolean isStoreTwoDirections() {
        return true;
    }

    public int getKeyIndex() {
        return keyIndex;
    }

    public void setKeyIndex(int keyIndex) {
        this.keyIndex = keyIndex;
    }
}
