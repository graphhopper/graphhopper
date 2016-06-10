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
package com.graphhopper.reader;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Base class for all OSM objects
 * <p>
 * @author Nop
 * @author Peter
 */
public abstract class OSMElement
{
    public static final int NODE = 0;
    public static final int WAY = 1;
    public static final int RELATION = 2;
    public static final int FILEHEADER = 3;
    private final int type;
    private final long id;
    private final Map<String, Object> properties = new HashMap<String, Object>(5);

    protected OSMElement( long id, int type )
    {
        this.id = id;
        this.type = type;
    }

    public long getId()
    {
        return id;
    }

    protected void readTags( XMLStreamReader parser ) throws XMLStreamException
    {
        int event = parser.getEventType();
        while (event != XMLStreamConstants.END_DOCUMENT && parser.getLocalName().equals("tag"))
        {
            if (event == XMLStreamConstants.START_ELEMENT)
            {
                // read tag
                String key = parser.getAttributeValue(null, "k");
                String value = parser.getAttributeValue(null, "v");
                // ignore tags with empty values
                if (value != null && value.length() > 0)
                    setTag(key, value);
            }

            event = parser.nextTag();
        }
    }

    protected String tagsToString()
    {
        if (properties.isEmpty())
            return "<empty>";

        StringBuilder tagTxt = new StringBuilder();
        for (Map.Entry<String, Object> entry : properties.entrySet())
        {
            tagTxt.append(entry.getKey());
            tagTxt.append("=");
            tagTxt.append(entry.getValue());
            tagTxt.append("\n");
        }
        return tagTxt.toString();
    }

    protected Map<String, Object> getTags()
    {
        return properties;
    }

    public void setTags( Map<String, String> newTags )
    {
        properties.clear();
        if (newTags != null)
            for (Entry<String, String> e : newTags.entrySet())
            {
                setTag(e.getKey(), e.getValue());
            }
    }

    public boolean hasTags()
    {
        return !properties.isEmpty();
    }

    public String getTag( String name )
    {
        return (String) properties.get(name);
    }

    @SuppressWarnings("unchecked")
    public <T> T getTag( String key, T defaultValue )
    {
        T val = (T) properties.get(key);
        if (val == null)
            return defaultValue;
        return val;
    }

    public void setTag( String name, Object value )
    {
        properties.put(name, value);
    }

    /**
     * Check that the object has a given tag with a given value.
     */
    public boolean hasTag( String key, Object value )
    {
        return value.equals(getTag(key, ""));
    }

    /**
     * Check that a given tag has one of the specified values. If no values are given, just checks
     * for presence of the tag
     */
    public boolean hasTag( String key, String... values )
    {
        Object osmValue = properties.get(key);
        if (osmValue == null)
            return false;

        // tag present, no values given: success
        if (values.length == 0)
            return true;

        for (String val : values)
        {
            if (val.equals(osmValue))
                return true;
        }
        return false;
    }

    /**
     * Check that a given tag has one of the specified values.
     */
    public final boolean hasTag( String key, Set<String> values )
    {
        return values.contains(getTag(key, ""));
    }

    /**
     * Check a number of tags in the given order for the any of the given values. Used to parse
     * hierarchical access restrictions
     */
    public boolean hasTag( List<String> keyList, Set<String> values )
    {
        for (String key : keyList)
        {
            if (values.contains(getTag(key, "")))
                return true;
        }
        return false;
    }

    /**
     * Returns the first existing tag of the specified list where the order is important.
     */
    public String getFirstPriorityTag( List<String> restrictions )
    {
        for (String str : restrictions)
        {
            if (hasTag(str))
                return getTag(str);
        }
        return "";
    }

    public void removeTag( String name )
    {
        properties.remove(name);
    }

    public void clearTags()
    {
        properties.clear();
    }

    public int getType()
    {
        return type;
    }

    public boolean isType( int type )
    {
        return this.type == type;
    }

    @Override
    public String toString()
    {
        return properties.toString();
    }
}
