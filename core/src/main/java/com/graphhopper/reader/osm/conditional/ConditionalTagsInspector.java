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
package com.graphhopper.reader.osm.conditional;

import com.graphhopper.reader.OSMWay;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.List;
import java.util.Set;

/**
 * Inspects the conditional tags of an OSMWay according to the given conditional tags.
 * <p>
 * @author Robin Boldt
 */
public class ConditionalTagsInspector
{
    private static final Logger logger = LoggerFactory.getLogger(ConditionalTagsInspector.class);

    private final Calendar calendar;
    private final List<String> tagsToCheck;
    private final ConditionalParser restrictiveParser;
    private final ConditionalParser permitParser;
    private final boolean enabledLogs = false;

    /**
     * Create with todays date
     */
    public ConditionalTagsInspector( List<String> tagsToCheck, Set<String> restrictiveValues, Set<String> permittedValues )
    {
        this(DateRangeParser.createCalendar(), tagsToCheck, restrictiveValues, permittedValues);
    }

    /**
     * Create with given date
     */
    public ConditionalTagsInspector( Calendar cal, List<String> tagsToCheck, Set<String> restrictiveValues, Set<String> permittedValues )
    {
        this.calendar = cal;
        this.tagsToCheck = new ArrayList(tagsToCheck.size());
        for (String tagToCheck : tagsToCheck)
        {
            this.tagsToCheck.add(tagToCheck + ":conditional");
        }
        this.restrictiveParser = new ConditionalParser(restrictiveValues, enabledLogs);
        this.permitParser = new ConditionalParser(permittedValues, enabledLogs);
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
            if (val != null && !val.isEmpty())
            {
                try
                {
                    DateRange dateRange;
                    if (checkPermissiveValues)
                        dateRange = permitParser.getDateRange(val);
                    else
                        dateRange = restrictiveParser.getDateRange(val);

                    if (dateRange != null && dateRange.isInRange(calendar))
                        return true;
                } catch (Exception e)
                {
                    if (enabledLogs)
                        logger.warn(way.getId() + " - could not parse the conditional value:" + val + " of tag:" + tagToCheck + ". Exception:" + e.getMessage());
                }
            }
        }
        return false;
    }
}
