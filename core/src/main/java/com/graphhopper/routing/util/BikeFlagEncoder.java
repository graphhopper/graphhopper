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

/**
 * Specifies the settings for cycletouring/trekking
 * <p/>
 * @author ratrun
 */
public class BikeFlagEncoder extends BikeFlagCommonEncoder
{
    BikeFlagEncoder()
    {
        setTrackTypeSpeed("grade1", 20); // paved
        setTrackTypeSpeed("grade2", 12); // now unpaved ...
        setTrackTypeSpeed("grade3", 12);
        setTrackTypeSpeed("grade4", 10);
        setTrackTypeSpeed("grade5", 8); // like sand/grass     

        setSurfaceSpeed("paved", 20);        
        setSurfaceSpeed("asphalt", 20);
        setSurfaceSpeed("cobblestone", 10);
        setSurfaceSpeed("cobblestone:flattened", 10);
        setSurfaceSpeed("sett",8);
        setSurfaceSpeed("concrete", 20);
        setSurfaceSpeed("concrete:lanes", 16);
        setSurfaceSpeed("concrete:plates", 16);
        setSurfaceSpeed("paving_stones", 10);
        setSurfaceSpeed("paving_stones:30", 10);
        setSurfaceSpeed("unpaved", 16);
        setSurfaceSpeed("compacted",18);        
        setSurfaceSpeed("dirt", 10);
        setSurfaceSpeed("earth", 10);
        setSurfaceSpeed("fine_gravel", 18);
        setSurfaceSpeed("grass", 8);
        setSurfaceSpeed("grass_paver", 8);
        setSurfaceSpeed("gravel", 12);
        setSurfaceSpeed("ground", 12);
        setSurfaceSpeed("ice", PUSHING_SECTION_SPEED / 2);
        setSurfaceSpeed("metal", 10);
        setSurfaceSpeed("mud", 10);
        setSurfaceSpeed("pebblestone", 16);
        setSurfaceSpeed("salt", 6);
        setSurfaceSpeed("sand", 8);
        setSurfaceSpeed("wood", 10);

        setHighwaySpeed("living_street", 15);
        setHighwaySpeed("steps", PUSHING_SECTION_SPEED / 2);

        setHighwaySpeed("cycleway", 18);
        setHighwaySpeed("path", 18);
        setHighwaySpeed("footway", 18);
        setHighwaySpeed("pedestrian", 16);
        setHighwaySpeed("road", 10);
        setHighwaySpeed("track", 20);
        setHighwaySpeed("service", 20);
        setHighwaySpeed("unclassified", 20);
        setHighwaySpeed("residential", 20);

        setHighwaySpeed("trunk", 18);
        setHighwaySpeed("trunk_link", 18);
        setHighwaySpeed("primary", 18);
        setHighwaySpeed("primary_link", 15);
        setHighwaySpeed("secondary", 16);
        setHighwaySpeed("secondary_link", 16);
        setHighwaySpeed("tertiary", 18);
        setHighwaySpeed("tertiary_link", 18);

        setPushingSection("path");
        setPushingSection("track");
        setPushingSection("footway");
        setPushingSection("pedestrian");
        setPushingSection("steps");

        setCyclingNetworkPreference("icn", RelationMapCode.OUTSTANDING_NICE.getValue());
        setCyclingNetworkPreference("ncn", RelationMapCode.OUTSTANDING_NICE.getValue());
        setCyclingNetworkPreference("rcn", RelationMapCode.VERY_NICE.getValue());
        setCyclingNetworkPreference("lcn", RelationMapCode.PREFER.getValue());
        setCyclingNetworkPreference("mtb", RelationMapCode.UNCHANGED.getValue());
    }

    @Override
    public String toString()
    {
        return "bike";
    }
}
