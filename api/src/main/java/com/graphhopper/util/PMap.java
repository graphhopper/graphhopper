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

/**
 * A properties map (String to String) with convenient accessors
 * <p>
 *
 * @author Peter Karich
 */
public class PMap {
    private final Map<String, String> map;

    public PMap() {
        this(5);
    }

    public PMap(int capacity) {
        this(new HashMap<>(capacity));
    }

    public PMap(Map<String, String> map) {
        this.map = new HashMap<>(map);
    }

    public PMap(PMap map) {
        this.map = new HashMap<>(map.map);
    }

    public PMap(String propertiesString) {
        // five chosen as arbitrary initial capacity
        this.map = new HashMap<>(5);

        for (String s : propertiesString.split("\\|")) {
            s = s.trim();
            int index = s.indexOf("=");
            if (index < 0)
                continue;

            put(s.substring(0, index), s.substring(index + 1));
        }
    }

    public PMap put(PMap map) {
        this.map.putAll(map.map);
        return this;
    }

    public PMap put(String key, Object str) {
        if (str == null)
            throw new NullPointerException("Value cannot be null. Use remove instead.");

        // store in under_score
        map.put(Helper.camelCaseToUnderScore(key), str.toString());
        return this;
    }

    public PMap remove(String key) {
        // query accepts camelCase and under_score
        map.remove(Helper.camelCaseToUnderScore(key));
        return this;
    }

    public boolean has(String key) {
        // query accepts camelCase and under_score
        return map.containsKey(Helper.camelCaseToUnderScore(key));
    }

    public long getLong(String key, long _default) {
        Long longOrNull = getLongOrNull(key);
        return longOrNull != null ? longOrNull : _default;
    }

    public Long getLongOrNull(String key) {
        String str = getStringOrNull(key);
        if (str == null) {
            return null;
        }
        try {
            return Long.parseLong(str);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public int getInt(String key, int _default) {
        Integer intOrNull = getIntOrNull(key);
        return intOrNull != null ? intOrNull : _default;
    }

    public Integer getIntOrNull(String key) {
        String str = getStringOrNull(key);
        if (str == null) {
            return null;
        }
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public boolean getBool(String key, boolean _default) {
        Boolean boolOrNull = getBoolOrNull(key);
        return boolOrNull != null ? boolOrNull : _default;
    }

    public Boolean getBoolOrNull(String key) {
        String str = getStringOrNull(key);
        if (str == null) {
            return null;
        }
        try {
            return Boolean.parseBoolean(str);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public float getFloat(String key, float _default) {
        Float floatOrNull = getFloatOrNull(key);
        return floatOrNull != null ? floatOrNull : _default;
    }

    public Float getFloatOrNull(String key) {
        String str = getStringOrNull(key);
        if (str == null) {
            return null;
        }
        try {
            return Float.parseFloat(str);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public double getDouble(String key, double _default) {
        Double doubleOrNull = getDoubleOrNull(key);
        return doubleOrNull != null ? doubleOrNull : _default;
    }

    public Double getDoubleOrNull(String key) {
        String str = getStringOrNull(key);
        if (str == null) {
            return null;
        }
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public String get(String key, String _default) {
        String str = getStringOrNull(key);
        return str != null ? str : _default;
    }

    public String getStringOrNull(String key) {
        if (Helper.isEmpty(key)) {
            return null;
        }
        // query accepts camelCase and under_score
        return map.get(Helper.camelCaseToUnderScore(key));
    }

    /**
     * This method copies the underlying structure into a new Map object
     */
    public Map<String, String> toMap() {
        return new HashMap<>(map);
    }

    private Map<String, String> getMap() {
        return map;
    }

    public PMap merge(PMap read) {
        return merge(read.getMap());
    }

    PMap merge(Map<String, String> map) {
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (Helper.isEmpty(e.getKey()))
                continue;

            put(e.getKey(), e.getValue());
        }
        return this;
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public String toString() {
        return getMap().toString();
    }
}
