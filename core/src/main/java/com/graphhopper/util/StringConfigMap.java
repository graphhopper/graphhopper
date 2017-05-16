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
 * A String,String ConfigMap e.g. read from URL parameters, which also converts keys from camelCase to under_score.
 *
 * @author Peter Karich
 */
public class StringConfigMap extends ConfigMap {

    public StringConfigMap() {
        super(5);
    }

    public StringConfigMap(int capacity) {
        super(capacity);
    }

    public StringConfigMap(Map<String, String> map) {
        super(map.size());
        putAll(map);
    }

    public StringConfigMap(StringConfigMap map) {
        super(map.map);
    }

    /**
     * This method creates a StringConfigMap out of a properties string ala a=value1|b=value2|c=value3|...
     */
    public static StringConfigMap create(String propertiesString) {
        // five chosen as arbitrary initial capacity
        StringConfigMap map = new StringConfigMap(5);

        for (String s : propertiesString.split("\\|")) {
            s = s.trim();
            int index = s.indexOf("=");
            if (index < 0)
                continue;

            map.put(s.substring(0, index), s.substring(index + 1));
        }
        return map;
    }

    /**
     * This method sets the associated object of the key and calls obj.toString to be used as the String-value.
     */
    @Override
    public StringConfigMap put(String key, Object obj) {
        put(map, key, obj);
        return this;
    }

    @SuppressWarnings("unchecked")
    private static void put(Map map, String key, Object obj) {
        if (obj == null)
            throw new NullPointerException("Value cannot be null. Use remove(key) instead.");

        // store in under_score
        map.put(Helper.camelCaseToUnderScore(key), obj.toString());
    }

    @Override
    public StringConfigMap remove(String key) {
        // query accepts camelCase and under_score
        map.remove(Helper.camelCaseToUnderScore(key));
        return this;
    }

    @Override
    public boolean has(String key) {
        // query accepts camelCase and under_score
        return map.containsKey(Helper.camelCaseToUnderScore(key));
    }

    @Override
    public long getLong(String key, long _default) {
        String str = get(key);
        if (!Helper.isEmpty(str)) {
            try {
                return Long.parseLong(str);
            } catch (Exception ex) {
            }
        }
        return _default;
    }

    @Override
    public int getInt(String key, int _default) {
        String str = get(key);
        if (!Helper.isEmpty(str)) {
            try {
                return Integer.parseInt(str);
            } catch (Exception ex) {
            }
        }
        return _default;
    }

    @Override
    public boolean getBool(String key, boolean _default) {
        String str = get(key);
        if (!Helper.isEmpty(str)) {
            try {
                return Boolean.parseBoolean(str);
            } catch (Exception ex) {
            }
        }
        return _default;
    }

    @Override
    public double getDouble(String key, double _default) {
        String str = get(key);
        if (!Helper.isEmpty(str)) {
            try {
                return Double.parseDouble(str);
            } catch (Exception ex) {
            }
        }
        return _default;
    }

    public String get(String key, String _default) {
        String str = get(key);
        if (Helper.isEmpty(str))
            return _default;

        return str;
    }

    String get(String key) {
        if (Helper.isEmpty(key))
            return "";

        // query accepts camelCase and under_score
        return super.get(Helper.camelCaseToUnderScore(key), "");
    }

    /**
     * This method copies the underlying structure into a new Map object
     */
    public Map<String, String> toMap() {
        Map<String, String> tmpMap = new HashMap<>(map.size());
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            put(tmpMap, entry.getKey(), entry.getValue());
        }
        return tmpMap;
    }

    public StringConfigMap merge(StringConfigMap read) {
        return putAll(read.toMap());
    }

    StringConfigMap putAll(Map<String, String> map) {
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (Helper.isEmpty(e.getKey()))
                continue;

            put(e.getKey(), e.getValue());
        }
        return this;
    }
}
