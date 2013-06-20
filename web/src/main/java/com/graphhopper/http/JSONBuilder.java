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
package com.graphhopper.http;

import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;

/**
 * @author Peter Karich
 */
public class JSONBuilder
{
    private String lastObjectName;
    private JSONBuilder parent;
    private Map<String, Object> map;

    public JSONBuilder()
    {
        map = new HashMap<String, Object>(5);
    }

    public JSONBuilder setParent( JSONBuilder p )
    {
        parent = p;
        return this;
    }

    public JSONBuilder startObject( String entry )
    {
        lastObjectName = entry;
        return new JSONBuilder().setParent(this);
    }

    public JSONBuilder endObject()
    {
        if (parent == null)
        {
            throw new IllegalStateException("object not opened?");
        }

        parent.map.put(parent.lastObjectName, map);
        parent.lastObjectName = null;
        return parent;
    }

    public JSONBuilder object( String key, Object val )
    {
        map.put(key, val);
        return this;
    }

    public JSONObject build()
    {
        if (parent != null || lastObjectName != null)
        {
            throw new IllegalStateException("json with name " + lastObjectName + " not closed");
        }

        return new JSONObject(map);
    }
}
