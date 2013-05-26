/*
 *  Licensed to Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  Peter Karich licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the
 *  License at
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Peter Karich
 */
public class FootFlagEncoder extends AbstractFlagEncoder {
    
    private final Set<String> saveHighwayTags = new HashSet<String>() {
        {
            add("footway");
            add("path");
            add("steps");
            add("pedestrian");
            add("foot");
            add("living_street");
            add("track");
        }
    };
    private final Set<String> allowedHighwayTags = new HashSet<String>() {
        {
            addAll(saveHighwayTags);
            add("trunk");
            add("primary");
            add("secondary");
            add("tertiary");
            add("unclassified");
            add("residential");
            add("road");
            add("service");
            // disallowed in some countries?
            add("bridleway");
        }
    };
    private static final Map<String, Integer> SPEED = new FootSpeed();
    protected HashSet<String> intended = new HashSet<String>();
    protected HashSet<String> sidewalks = new HashSet<String>();

    public FootFlagEncoder() {
        super(16, 1, SPEED.get("mean"), SPEED.get("max"));

        restrictions = new String[] { "foot", "access"};
        restricted.add("private");
        restricted.add("no");
        restricted.add("restricted");

        intended.add( "yes" );
        intended.add( "designated" );
        intended.add( "official" );
        intended.add( "permissive" );

        sidewalks.add( "yes" );
        sidewalks.add("both" );
        sidewalks.add( "left" );
        sidewalks.add( "right" );

    }

    public Integer getSpeed(String string) {
        return SPEED.get(string);
    }

    @Override public String toString() {
        return "FOOT";
    }

    /**
     * Some ways are okay but not separate for pedestrians.
     */
    @Override
    public boolean isAllowed(Map<String, String> osmProperties) {
        if( hasTag( "sidewalk", sidewalks, osmProperties ))
            return true;

        if( hasTag( "foot", intended, osmProperties ))
            return true;

        String highwayValue = osmProperties.get("highway");
        if (!allowedHighwayTags.contains(highwayValue))
            return false;

        if( hasTag( "motorroad", "yes", osmProperties ))
            return false;

        if( hasTag( "bicycle", "official", osmProperties ))
            return false;

        return checkAccessRestrictions( osmProperties );
    }

    /**
     * Separate ways for pedestrians.
     */
    public boolean isSaveHighway(String highwayValue) {
        return saveHighwayTags.contains(highwayValue);
    }

    private static class FootSpeed extends HashMap<String, Integer> {

        {
            put("min", 1);
            put("slow", 3);
            put("mean", 5);
            put("fast", 10);
            put("max", 15);
        }
    }
}
