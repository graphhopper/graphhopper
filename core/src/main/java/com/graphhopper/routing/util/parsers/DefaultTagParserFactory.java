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
package com.graphhopper.routing.util.parsers;

import com.graphhopper.routing.profiles.*;
import com.graphhopper.util.PMap;

import static com.graphhopper.util.Helper.toLowerCase;

public class DefaultTagParserFactory implements TagParserFactory {
    @Override
    public TagParser create(String name, PMap configuration) {
        name = name.trim();
        if (!name.equals(toLowerCase(name)))
            throw new IllegalArgumentException("Use lower case for TagParsers: " + name);

        // for Country (SpatialRuleParser) see SpatialRuleLookupHelper
        if (Roundabout.KEY.equals(name))
            return new OSMRoundaboutParser();
        else if (name.equals(RoadClass.KEY))
            return new OSMRoadClassParser();
        else if (name.equals(RoadClassLink.KEY))
            return new OSMRoadClassLinkParser();
        else if (name.equals(RoadEnvironment.KEY))
            return new OSMRoadEnvironmentParser();
        else if (name.equals(RoadAccess.KEY))
            return new OSMRoadAccessParser();
        else if (name.equals(MaxSpeed.KEY))
            return new OSMMaxSpeedParser();
        else if (name.equals(MaxWeight.KEY))
            return new OSMMaxWeightParser();
        else if (name.equals(MaxHeight.KEY))
            return new OSMMaxHeightParser();
        else if (name.equals(MaxWidth.KEY))
            return new OSMMaxWidthParser();
        else if (name.equals(Surface.KEY))
            return new OSMSurfaceParser();
        else if (name.equals(Toll.KEY))
            return new OSMTollParser();
        throw new IllegalArgumentException("entry in encoder list not supported " + name);
    }
}
