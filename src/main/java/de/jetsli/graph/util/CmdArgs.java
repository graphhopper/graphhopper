/*
 *  Copyright 2012 Peter Karich info@jetsli.de
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package de.jetsli.graph.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Peter Karich
 */
public class CmdArgs {

    private final Map<String, String> map;

    public CmdArgs() {
        this(new LinkedHashMap<String, String>(5));
    }

    public CmdArgs(Map<String, String> map) {
        this.map = map;
    }

    public CmdArgs put(String key, String str) {
        map.put(key, str);
        return this;
    }

    public long getLong(String key, long _default) {
        String str = map.get(key);
        if (!Helper.isEmpty(str)) {
            try {
                return Long.parseLong(str);
            } catch (Exception ex) {
            }
        }
        return _default;
    }

    public int getInt(String key, int _default) {
        String str = map.get(key);
        if (!Helper.isEmpty(str)) {
            try {
                return Integer.parseInt(str);
            } catch (Exception ex) {
            }
        }
        return _default;
    }

    public boolean getBool(String key, boolean _default) {
        String str = map.get(key);
        if (!Helper.isEmpty(str)) {
            try {
                return Boolean.parseBoolean(str);
            } catch (Exception ex) {
            }
        }
        return _default;
    }

    public double getDouble(String key, double _default) {
        String str = map.get(key);
        if (!Helper.isEmpty(str)) {
            try {
                return Double.parseDouble(str);
            } catch (Exception ex) {
            }
        }
        return _default;
    }

    public String get(String key, String _default) {
        String str = map.get(key);
        if (Helper.isEmpty(str))
            return _default;
        return str;
    }
}
