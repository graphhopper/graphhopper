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

import java.util.HashMap;
import java.util.Map;

public class OMap {
    private final Map<String, Object> map;

    public OMap(int capacity) {
        map = new HashMap<>(capacity);
    }

    public OMap() {
        map = new HashMap<>();
    }

    public OMap(Map<String, Object> map) {
        this.map = map;
    }

    public boolean has(String key) {
        return map.containsKey(key);
    }

    public OMap put(String key, Object object) {
        map.put(key, object);
        return this;
    }

    public OMap putAll(OMap map) {
        map.putAll(map);
        return this;
    }

    public OMap remove(String key) {
        map.remove(key);
        return this;
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

    public String get(String key, String _default) {
        Object object = map.get(key);
        return object instanceof String ? (String) object : _default;
    }

    public Object getObject(String key, Object _default) {
        Object object = map.get(key);
        return object == null ? _default : object;
    }

    public Map<String, Object> toMap() {
        return new HashMap<>(map);
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public String toString() {
        return map.toString();
    }

    /**
     * This constructor parses the values and converts them into boolean, int, long or double.
     */
    public static OMap fromPMap(Map<String, String> input) {
        OMap map = new OMap();
        for (Map.Entry<String, String> entry : input.entrySet()) {
            String value = entry.getValue();
            if (Helper.isEmpty(value)) {
                map.put(entry.getKey(), value);
                continue;
            }
            if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                map.put(entry.getKey(), Boolean.parseBoolean(value));
                continue;
            }
            try {
                map.put(entry.getKey(), Integer.parseInt(value));
            } catch (NumberFormatException ex) {
                try {
                    map.put(entry.getKey(), Long.parseLong(value));
                } catch (NumberFormatException ex2) {
                    try {
                        map.put(entry.getKey(), Float.parseFloat(value));
                    } catch (NumberFormatException ex3) {
                        try {
                            map.put(entry.getKey(), Double.parseDouble(value));
                        } catch (NumberFormatException ex4) {
                            // give up and store as string
                            map.put(entry.getKey(), value);
                        }
                    }
                }
            }
        }

        return map;
    }
}
