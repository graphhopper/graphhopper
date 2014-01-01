/*
 * Copyright 2013 User.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.graphhopper.routing.util;

public class RacingBikeFlagEncoder extends BikeFlagCommonEncoder
{
    RacingBikeFlagEncoder()
    {
        super ();
        setTrackTypeSpeed("grade1", 20); // paved
        setTrackTypeSpeed("grade2", 1); // now unpaved ...
        setTrackTypeSpeed("grade3", 1);
        setTrackTypeSpeed("grade4", 1);
        setTrackTypeSpeed("grade5", 1); // like sand/grass     
        
        setSurfaceSpeed("asphalt", 20);
        setSurfaceSpeed("concrete", 20);
        setSurfaceSpeed("paved", 20);
        setSurfaceSpeed("unpaved", 1);
        setSurfaceSpeed("gravel", 1);
        setSurfaceSpeed("ground", 1);
        setSurfaceSpeed("dirt", 1);
        setSurfaceSpeed("paving_stones", 1);
        setSurfaceSpeed("grass", 1);
        setSurfaceSpeed("cobblestone", 1);

        setHighwaySpeed("living_street", 15);
        setHighwaySpeed("steps", 1);

        setHighwaySpeed("cycleway", 18);
        setHighwaySpeed("path", 15);
        setHighwaySpeed("footway", 15);
        setHighwaySpeed("pedestrian", 15);
        setHighwaySpeed("road", 10);
        setHighwaySpeed("track", 20);
        setHighwaySpeed("service", 20);
        setHighwaySpeed("unclassified", 20);
        setHighwaySpeed("residential", 20);

        setHighwaySpeed("trunk", 20);
        setHighwaySpeed("trunk_link", 20);
        setHighwaySpeed("primary", 20);
        setHighwaySpeed("primary_link", 20);
        setHighwaySpeed("secondary", 24);
        setHighwaySpeed("secondary_link", 24);
        setHighwaySpeed("tertiary", 24);
        setHighwaySpeed("tertiary_link", 24);
        
        setPushingSection("path");
        setPushingSection("track");
        setPushingSection("footway");
        setPushingSection("pedestrian");
        setPushingSection("steps");
        
        setCyclingNetworkPreference("icn", RelationMapCode.OUTSTANDING_NICE.getValue());
        setCyclingNetworkPreference("ncn", RelationMapCode.OUTSTANDING_NICE.getValue());
        setCyclingNetworkPreference("rcn", RelationMapCode.VERY_NICE.getValue());
        setCyclingNetworkPreference("lcn", RelationMapCode.UNCHANGED.getValue());
        setCyclingNetworkPreference("mtb", RelationMapCode.UNCHANGED.getValue());        
        
    }
    
    @Override
    public String toString()
    {
        return "racingbike";
    }    
}
