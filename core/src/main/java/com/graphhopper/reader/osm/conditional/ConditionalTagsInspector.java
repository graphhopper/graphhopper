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
package com.graphhopper.reader.osm.conditional;

import com.graphhopper.reader.OSMWay;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Inspects the conditional tags of an OSMWay according to the given conditional tags.
 * <p>
 * @author Robin Boldt
 */
public class ConditionalTagsInspector
{
    private final Logger logger = LoggerFactory.getLogger(getClass());
    // enabling by default makes noise but could improve OSM data
    private boolean enabledLogs = true;
    private final List<String> tagsToCheck;
    private final Map<String, Object> valueMap;
    private final ConditionalParser permitParser, restrictiveParser;

    public ConditionalTagsInspector( Object value, List<String> tagsToCheck,
                                     Set<String> restrictiveValues, Set<String> permittedValues )
    {
        this(tagsToCheck, createDefaultMapping(value), restrictiveValues, permittedValues, true);
    }

    public ConditionalTagsInspector( List<String> tagsToCheck, Map<String, Object> valueMap,
                                     Set<String> restrictiveValues, Set<String> permittedValues, boolean enabledLogs )
    {
        this.valueMap = valueMap;
        this.tagsToCheck = new ArrayList<>(tagsToCheck.size());
        for (String tagToCheck : tagsToCheck)
        {
            this.tagsToCheck.add(tagToCheck + ":conditional");
        }

        this.enabledLogs = enabledLogs;

        // enable for debugging purposes only as this is too much
        boolean logUnsupportedFeatures = false;
        this.permitParser = new ConditionalParser(permittedValues, logUnsupportedFeatures);
        this.restrictiveParser = new ConditionalParser(restrictiveValues, logUnsupportedFeatures);
    }

    static Map<String, Object> createDefaultMapping( Object value )
    {
        // parse date range and value is the time
        Map<String, Object> map = new HashMap<String, Object>(1);
        map.put(DateRange.KEY, value);
        return map;
    }

    public boolean isRestrictedWayConditionallyPermitted( OSMWay way )
    {
        return applies(way, true);
    }

    public boolean isPermittedWayConditionallyRestricted( OSMWay way )
    {
        return applies(way, false);
    }

    protected boolean applies( OSMWay way, boolean checkPermissiveValues )
    {
        for (int index = 0; index < tagsToCheck.size(); index++)
        {
            String tagToCheck = tagsToCheck.get(index);
            String val = way.getTag(tagToCheck);
            if (val == null || val.isEmpty())
                continue;

            try
            {
                ValueRange valueRange;
                if (checkPermissiveValues)
                    valueRange = permitParser.getRange(val);
                else
                    valueRange = restrictiveParser.getRange(val);

                if (valueRange != null)
                {
                    Object value = valueMap.get(valueRange.getKey());
                    if (value != null && valueRange.isInRange(value))
                        return true;
                }
            } catch (Exception e)
            {
                if (enabledLogs)
                {
                    // log only if no date ala 21:00 as currently date and numbers do not support time precise restrictions
                    if (!val.contains(":"))
                        logger.warn(way.getId() + " - could not parse the conditional value:" + val + " of tag:" + tagToCheck + ". Exception:" + e.getMessage());
                }
            }
        }
        return false;
    }
}
