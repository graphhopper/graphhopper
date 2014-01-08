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

import static com.graphhopper.routing.util.BikeFlagCommonEncoder.PUSHING_SECTION_SPEED;

/**
 * Specifies the settings for mountain biking
 * <p/>
 * @author ratrun
 */
public class MountainBikeFlagEncoder extends BikeFlagCommonEncoder
{
    MountainBikeFlagEncoder()
    {
        setTrackTypeSpeed("grade1", 12); // paved
        setTrackTypeSpeed("grade2", 20); // now unpaved ...
        setTrackTypeSpeed("grade3", 20);
        setTrackTypeSpeed("grade4", 20);
        setTrackTypeSpeed("grade5", 20); // like sand/grass     

        setSurfaceSpeed("asphalt", 12);
        setSurfaceSpeed("concrete", 12);
        setSurfaceSpeed("paved", 12);
        setSurfaceSpeed("unpaved", 20);
        setSurfaceSpeed("gravel", 20);
        setSurfaceSpeed("ground", 20);
        setSurfaceSpeed("dirt", 20);
        setSurfaceSpeed("paving_stones", 20);
        setSurfaceSpeed("grass", 20);
        setSurfaceSpeed("cobblestone", 20);

        setHighwaySpeed("living_street", 15);
        setHighwaySpeed("steps", PUSHING_SECTION_SPEED / 2);

        setHighwaySpeed("cycleway", 12);
        setHighwaySpeed("path", 24);
        setHighwaySpeed("footway", 15);
        setHighwaySpeed("pedestrian", 15);
        setHighwaySpeed("road", 10);
        setHighwaySpeed("track", 24);
        setHighwaySpeed("service", 15);
        setHighwaySpeed("unclassified", 15);
        setHighwaySpeed("residential", 15);

        setHighwaySpeed("trunk", 12);
        setHighwaySpeed("trunk_link", 12);
        setHighwaySpeed("primary", 10);
        setHighwaySpeed("primary_link", 10);
        setHighwaySpeed("secondary", 12);
        setHighwaySpeed("secondary_link", 12);
        setHighwaySpeed("tertiary", 14);
        setHighwaySpeed("tertiary_link", 14);

        setPushingSection("footway");
        setPushingSection("pedestrian");
        setPushingSection("steps");

        setCyclingNetworkPreference("icn", BikeFlagCommonEncoder.RelationMapCode.PREFER.getValue());
        setCyclingNetworkPreference("ncn", BikeFlagCommonEncoder.RelationMapCode.PREFER.getValue());
        setCyclingNetworkPreference("rcn", BikeFlagCommonEncoder.RelationMapCode.PREFER.getValue());
        setCyclingNetworkPreference("lcn", BikeFlagCommonEncoder.RelationMapCode.PREFER.getValue());
        setCyclingNetworkPreference("mtb", BikeFlagCommonEncoder.RelationMapCode.OUTSTANDING_NICE.getValue());

    }

    @Override
    public String toString()
    {
        return "mtb";
    }
}
