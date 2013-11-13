/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

/**
 * Stores command line options in a map. The capitalization of the key is ignored.
 * <p/>
 * @author Peter Karich
 */
public class CmdArgs
{
    private final Map<String, String> map;

    public CmdArgs()
    {
        this(new LinkedHashMap<String, String>(5));
    }

    public CmdArgs( Map<String, String> map )
    {
        this.map = map;
    }

    public CmdArgs put( String key, String str )
    {
        map.put(key.toLowerCase(), str);
        return this;
    }

    public long getLong( String key, long _default )
    {
        String str = get(key);
        if (!Helper.isEmpty(str))
        {
            try
            {
                return Long.parseLong(str);
            } catch (Exception ex)
            {
            }
        }
        return _default;
    }

    public int getInt( String key, int _default )
    {
        String str = get(key);
        if (!Helper.isEmpty(str))
        {
            try
            {
                return Integer.parseInt(str);
            } catch (Exception ex)
            {
            }
        }
        return _default;
    }

    public boolean getBool( String key, boolean _default )
    {
        String str = get(key);
        if (!Helper.isEmpty(str))
        {
            try
            {
                return Boolean.parseBoolean(str);
            } catch (Exception ex)
            {
            }
        }
        return _default;
    }

    public double getDouble( String key, double _default )
    {
        String str = get(key);
        if (!Helper.isEmpty(str))
        {
            try
            {
                return Double.parseDouble(str);
            } catch (Exception ex)
            {
            }
        }
        return _default;
    }

    public String get( String key, String _default )
    {
        String str = get(key);
        if (Helper.isEmpty(str))
        {
            return _default;
        }
        return str;
    }

    String get( String key )
    {
        if (Helper.isEmpty(key))
        {
            return "";
        }
        String val = map.get(key.toLowerCase());
        if (val == null)
        {
            return "";
        }
        return val;
    }

    /**
     * @param fileStr the file name of config.properties
     * @param systemProperty the property name of the configuration. E.g. -Dgraphhopper.config
     */
    public static CmdArgs readFromConfig( String fileStr, String systemProperty ) throws IOException
    {
        if (systemProperty.startsWith("-D"))
            systemProperty = systemProperty.substring(2);

        String configLocation = System.getProperty(systemProperty);
        if (Helper.isEmpty(configLocation))
            configLocation = fileStr;

        Map<String, String> map = new LinkedHashMap<String, String>();
        Helper.loadProperties(map, new InputStreamReader(new FileInputStream(
                new File(configLocation).getAbsoluteFile()), "UTF-8"));
        CmdArgs args = new CmdArgs();
        args.merge(map);

        // overwrite with system settings
        Properties props = System.getProperties();
        for (Entry<Object, Object> e : props.entrySet())
        {
            String k = ((String) e.getKey());
            String v = ((String) e.getValue());
            if (k.startsWith("graphhopper."))
            {
                k = k.substring("graphhopper.".length());
                args.put(k, v);
            }
        }
        return args;
    }

    public static CmdArgs read( String[] args )
    {
        Map<String, String> map = new LinkedHashMap<String, String>();
        for (String arg : args)
        {
            int index = arg.indexOf("=");
            if (index <= 0)
            {
                continue;
            }

            String key = arg.substring(0, index);
            if (key.startsWith("-"))
            {
                key = key.substring(1);
            }

            if (key.startsWith("-"))
            {
                key = key.substring(1);
            }

            String value = arg.substring(index + 1);
            map.put(key.toLowerCase(), value);
        }

        return new CmdArgs(map);
    }

    public CmdArgs merge( CmdArgs read )
    {
        return merge(read.map);
    }

    CmdArgs merge( Map<String, String> map )
    {
        for (Entry<String, String> e : map.entrySet())
        {
            if (Helper.isEmpty(e.getKey()))
            {
                continue;
            }
            this.map.put(e.getKey().toLowerCase(), e.getValue());
        }
        return this;
    }

    public boolean has( String key )
    {
        return map.containsKey(key);
    }

    @Override
    public String toString()
    {
        return map.toString();
    }
}
