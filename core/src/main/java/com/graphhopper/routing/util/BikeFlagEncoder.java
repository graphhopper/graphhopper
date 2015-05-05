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
package com.graphhopper.routing.util;

import com.graphhopper.reader.Way;

/**
 * Specifies the settings for cycletouring/trekking
 * <p/>
 * @author ratrun
 * @author Peter Karich
 */
public class BikeFlagEncoder extends BikeCommonFlagEncoder
{
    public BikeFlagEncoder()
    {
        this(4, 2, 0);
    }

    public BikeFlagEncoder( String propertiesStr )
    {
        this((int) parseLong(propertiesStr, "speedBits", 4),
                parseDouble(propertiesStr, "speedFactor", 2),
                parseBoolean(propertiesStr, "turnCosts", false) ? 3 : 0);
        this.setBlockFords(parseBoolean(propertiesStr, "blockFords", true));
    }

    public BikeFlagEncoder( int speedBits, double speedFactor, int maxTurnCosts )
    {
        super(speedBits, speedFactor, maxTurnCosts);
        addPushingSection("path");
        addPushingSection("footway");
        addPushingSection("pedestrian");
        addPushingSection("steps");

        avoidHighwayTags.add("trunk");
        avoidHighwayTags.add("trunk_link");
        avoidHighwayTags.add("primary");
        avoidHighwayTags.add("primary_link");
        avoidHighwayTags.add("secondary");
        avoidHighwayTags.add("secondary_link");

        // preferHighwayTags.add("road");
        preferHighwayTags.add("service");
        preferHighwayTags.add("tertiary");
        preferHighwayTags.add("tertiary_link");
        preferHighwayTags.add("residential");
        preferHighwayTags.add("unclassified");
    }

    @Override
    boolean isPushingSection( Way way )
    {
        String highway = way.getTag("highway");
        String trackType = way.getTag("tracktype");
        return way.hasTag("highway", pushingSections)
                || way.hasTag("railway", "platform")
                || "track".equals(highway) && trackType != null && !"grade1".equals(trackType);
    }

    @Override
    public String toString()
    {
        return "bike";
    }
}
