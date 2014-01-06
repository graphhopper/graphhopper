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
package com.graphhopper.reader;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Base class for all OSM objects
 * <p/>
 * @author Nop
 */
public abstract class OSMElement
{
    public static final int NODE = 0;
    public static final int WAY = 1;
    public static final int RELATION = 2;
    protected final int type;
    protected final long id;
    protected Map<String, String> tags;
    protected Map<String, Object> iProperties;

    public OSMElement( long id, int type, XMLStreamReader parser )
    {
        this.type = type;
        this.id = id;
    }

    public OSMElement( long id, int type, Map<String, String> tags )
    {
        this(id, type);
        this.tags = tags;
    }

    protected OSMElement( long id, int type )
    {
        this.id = id;
        this.type = type;
    }

    public long getId()
    {
        return id;
    }

    public void copyTags( OSMElement input )
    {
        if (input.hasTags())
        {
            tags = new HashMap<String, String>();
            tags.putAll(input.getTags());
        }
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
                {
                    // create map only if needed
                    if (tags == null)
                    {
                        tags = new HashMap<String, String>();
                    }

                    tags.put(key, value);
                }
            }

            event = parser.nextTag();
        }
    }

    protected String tagsToString()
    {
        if (tags == null)
        {
            return "<empty>";
        }

        StringBuilder tagTxt = new StringBuilder();
        for (Map.Entry<String, String> entry : tags.entrySet())
        {
            tagTxt.append(entry.getKey());
            tagTxt.append("=");
            tagTxt.append(entry.getValue());
            tagTxt.append("\n");
        }
        return tagTxt.toString();
    }

    public Map<String, String> getTags()
    {
        return tags;
    }

    public void replaceTags( HashMap<String, String> newTags )
    {
        tags = newTags;
    }

    public boolean hasTags()
    {
        return tags != null && !tags.isEmpty();
    }

    public String getTag( String name )
    {
        if (tags == null)
            return null;

        return tags.get(name);
    }

    public void setTag( String name, String value )
    {
        if (tags == null)
            tags = new HashMap<String, String>();

        tags.put(name, value);
    }

    /**
     * Chaeck that the object has a given tag with a given value.
     */
    public boolean hasTag( String key, String value )
    {
        if (tags == null)
            return false;

        String val = tags.get(key);
        return value.equals(val);
    }

    /**
     * Check that a given tag has one of the specified values. If no values are given, just checks
     * for presence of the tag
     */
    public boolean hasTag( String key, String... values )
    {
        if (tags == null)
            return false;

        String osmValue = tags.get(key);
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
        if (tags == null)
            return false;

        String osmValue = tags.get(key);
        return osmValue != null && values.contains(osmValue);
    }

    /**
     * Check a number of tags in the given order for the any of the given values. Used to parse
     * hierarchical access restrictions
     */
    public boolean hasTag( String[] keyList, Set<String> values )
    {
        if (tags == null)
            return false;

        for (int i = 0; i < keyList.length; i++)
        {
            String osmValue = tags.get(keyList[i]);
            if (osmValue != null && values.contains(osmValue))
                return true;
        }
        return false;
    }

    public void setInternalTag( String key, Object value )
    {
        if (iProperties == null)
            iProperties = new HashMap<String, Object>();

        iProperties.put(key, value);
    }

    public boolean hasInternalTag( String key )
    {
        if (iProperties == null)
            return false;
        return iProperties.containsKey(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T getInternalTag( String key, T defaultValue )
    {
        if (iProperties == null)
            return defaultValue;
        T val = (T) iProperties.get(key);
        if (val == null)
            return defaultValue;
        return val;
    }

    public void removeTag( String name )
    {
        if (tags != null)
            tags.remove(name);
    }

    public void clearTags()
    {
        tags = null;
    }

    public int getType()
    {
        return type;
    }

    public boolean isType( int type )
    {
        return this.type == type;
    }
}
