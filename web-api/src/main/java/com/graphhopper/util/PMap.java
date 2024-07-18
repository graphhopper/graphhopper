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
package com.graphhopper.util;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A properties map (String to Object) with convenient methods to access the content.
 * <p>
 *
 * @author Peter Karich
 */
public class PMap {
    private final LinkedHashMap<String, Object> map;

    public PMap() {
        this(5);
    }

    public PMap(int capacity) {
        this.map = new LinkedHashMap<>(capacity);
    }

    public PMap(Map<String, Object> map) {
        this.map = new LinkedHashMap<>(map);
    }

    public PMap(PMap map) {
        this.map = new LinkedHashMap<>(map.map);
    }

    public PMap(String propertiesString) {
        this.map = new LinkedHashMap<>();

        for (String s : propertiesString.split("\\|")) {
            s = s.trim();
            int index = s.indexOf("=");
            if (index < 0)
                continue;

            putObject(Helper.camelCaseToUnderScore(s.substring(0, index)), Helper.toObject(s.substring(index + 1)));
        }
    }

    /**
     * Reads a PMap from a string array consisting of key=value pairs
     */
    public static PMap read(String[] args) {
        PMap map = new PMap();
        for (String arg : args) {
            int index = arg.indexOf("=");
            if (index <= 0) {
                continue;
            }

            String key = arg.substring(0, index);
            if (key.startsWith("-")) {
                key = key.substring(1);
            }

            if (key.startsWith("-")) {
                key = key.substring(1);
            }

            String value = arg.substring(index + 1);
            Object old = map.map.put(Helper.camelCaseToUnderScore(key), Helper.toObject(value));
            if (old != null)
                throw new IllegalArgumentException("Pair '" + Helper.camelCaseToUnderScore(key) + "'='" + value + "' not possible to " +
                        "add to the PMap-object as the key already exists with '" + old + "'");
        }
        return map;
    }

    public PMap putAll(PMap map) {
        this.map.putAll(map.map);
        return this;
    }

    public Object remove(String key) {
        return map.remove(key);
    }

    public boolean has(String key) {
        return map.containsKey(key);
    }

    public boolean getBool(String key, boolean _default) {
        Object object = map.get(key);
        return object instanceof Boolean ? (Boolean) object : _default;
    }

    public int getInt(String key, int _default) {
        Object object = map.get(key);
        return object instanceof Number ? ((Number) object).intValue() : _default;
    }

    public long getLong(String key, long _default) {
        Object object = map.get(key);
        return object instanceof Number ? ((Number) object).longValue() : _default;
    }

    public float getFloat(String key, float _default) {
        Object object = map.get(key);
        return object instanceof Number ? ((Number) object).floatValue() : _default;
    }

    public double getDouble(String key, double _default) {
        Object object = map.get(key);
        return object instanceof Number ? ((Number) object).doubleValue() : _default;
    }

    public String getString(String key, String _default) {
        Object object = map.get(key);
        return object instanceof String ? (String) object : _default;
    }

    public <T> T getObject(String key, T _default) {
        Object object = map.get(key);
        return object == null ? _default : (T) object;
    }

    public PMap putObject(String key, Object object) {
        map.put(key, object);
        return this;
    }

    public static Set<String> toSet(String value) {
        return Arrays.stream(value.split(";")).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    /**
     * This method copies the underlying structure into a new Map object
     */
    public Map<String, Object> toMap() {
        return new LinkedHashMap<>(map);
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public String toString() {
        return map.toString();
    }
}
