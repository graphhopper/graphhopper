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
 * Specifies the settings for racebikeing
 * <p/>
 * @author ratrun
 */
public class RacingBikeFlagEncoder extends BikeFlagCommonEncoder
{
    RacingBikeFlagEncoder()
    {
        setTrackTypeSpeed("grade1", 20); // paved
        setTrackTypeSpeed("grade2", PUSHING_SECTION_SPEED); // now unpaved ...
        setTrackTypeSpeed("grade3", PUSHING_SECTION_SPEED / 2);
        setTrackTypeSpeed("grade4", PUSHING_SECTION_SPEED / 2);
        setTrackTypeSpeed("grade5", PUSHING_SECTION_SPEED / 2); // like sand/grass     

        setSurfaceSpeed("paved", 20);        
        setSurfaceSpeed("asphalt", 20);
        setSurfaceSpeed("cobblestone", 10);
        setSurfaceSpeed("cobblestone:flattened", 10);
        setSurfaceSpeed("sett",10);
        setSurfaceSpeed("concrete", 20);
        setSurfaceSpeed("concrete:lanes", 16);
        setSurfaceSpeed("concrete:plates", 16);
        setSurfaceSpeed("paving_stones", 10);
        setSurfaceSpeed("paving_stones:30", 10);
        setSurfaceSpeed("unpaved", PUSHING_SECTION_SPEED / 2);
        setSurfaceSpeed("compacted",PUSHING_SECTION_SPEED / 2);
        setSurfaceSpeed("dirt", PUSHING_SECTION_SPEED / 2);
        setSurfaceSpeed("earth", PUSHING_SECTION_SPEED / 2);
        setSurfaceSpeed("fine_gravel", PUSHING_SECTION_SPEED);
        setSurfaceSpeed("grass", PUSHING_SECTION_SPEED / 2);
        setSurfaceSpeed("grass_paver", PUSHING_SECTION_SPEED / 2);
        setSurfaceSpeed("gravel", PUSHING_SECTION_SPEED / 2);
        setSurfaceSpeed("ground", PUSHING_SECTION_SPEED / 2);
        setSurfaceSpeed("ice", PUSHING_SECTION_SPEED / 2);
        setSurfaceSpeed("metal", PUSHING_SECTION_SPEED / 2);
        setSurfaceSpeed("mud", PUSHING_SECTION_SPEED / 2);
        setSurfaceSpeed("pebblestone", PUSHING_SECTION_SPEED );
        setSurfaceSpeed("salt", PUSHING_SECTION_SPEED / 2);
        setSurfaceSpeed("sand", PUSHING_SECTION_SPEED / 2);
        setSurfaceSpeed("wood", PUSHING_SECTION_SPEED / 2);
        
        setHighwaySpeed("living_street", 15);
        setHighwaySpeed("steps", PUSHING_SECTION_SPEED / 2);

        setHighwaySpeed("cycleway", 18);
        setHighwaySpeed("path", 15);
        setHighwaySpeed("footway", 15);
        setHighwaySpeed("pedestrian", 15);
        setHighwaySpeed("road", 10);
        setHighwaySpeed("track", PUSHING_SECTION_SPEED / 2); // assume unpaved
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

    // In case that the way belongs to a relation for which we do have a relation triggered weight change.    
    // FIXME: Re-write in case that there is a more generic way to influence the weighting (issue #124).
    // Here we boost or reduce the speed according to the relationWeightCode:
    @Override
    int relationWeightCodeToSpeed( int highwaySpeed, int relationCode )
    {
        int speed;
        if ((highwaySpeed > PUSHING_SECTION_SPEED) && (highwaySpeed < 15))
            // We know that our way belongs to a cycle route, so we assume 15km/h minimum
            speed = 15;
        else
            speed = highwaySpeed;

        if (speed > PUSHING_SECTION_SPEED)
            // Add or remove 4km/h per every relation weight boost point
            return speed + 4 * (relationCode - DEFAULT_REL_CODE);
        else
            return speed;   // We are not pushing unpaved parts
    }

    @Override
    public String toString()
    {
        return "racingbike";
    }
}
