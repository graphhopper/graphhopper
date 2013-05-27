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
import java.util.Map;

/**
 * @author Peter Karich
 */
public class CarFlagEncoder extends AbstractFlagEncoder {

    private static final Map<String, Integer> SPEED = new CarSpeed();

    public CarFlagEncoder() {
        super(0, 2, SPEED.get("secondary"), SPEED.get("motorway"));

        restrictions = new String[] { "motorcar", "motor_vehicle", "vehicle", "access"};
        restrictedValues.add( "private" );
        restrictedValues.add( "agricultural" );
        restrictedValues.add( "forestry" );
        restrictedValues.add( "no" );
        restrictedValues.add( "restricted" );

    }

    public boolean isMotorway(int flags) {
        return getSpeedPart(flags) * factor == SPEED.get("motorway");
    }

    public boolean isService(int flags) {
        return getSpeedPart(flags) * factor == SPEED.get("service");
    }

    public Integer getSpeed(String string) {
        return SPEED.get(string);
    }

   @Override
   public int isAllowed( Map<String, String> osmProperties ) {

       String highwayValue = osmProperties.get( "highway" );

       if( highwayValue == null ) {
           if( hasTag( "route", ferries, osmProperties ) ) {
               String markedFor = osmProperties.get( "motorcar" );
               if( markedFor == null )
                   markedFor = osmProperties.get( "motor_vehicle" );
               if( "yes".equals( markedFor ) )
                   return acceptBit | ferryBit;
           }
           return 0;
       }
       else {
           if( !SPEED.containsKey( highwayValue ) )
               return 0;

           // check access restrictions
           if( hasTag( restrictions, restrictedValues, osmProperties ) )
               return 0;

           return acceptBit;
       }
   }

    @Override
    public int handleWayTags( int allowed, Map<String, String> osmProperties ) {

        if( (allowed & acceptBit) == 0 )
            return 0;

        int encoded = 0;
        if( (allowed & ferryBit) == 0 ) {
            String highwayValue = osmProperties.get("highway");
            // get assumed speed from highway type
            Integer speed = getSpeed( highwayValue );
            // apply speed limit
            int maxspeed = AcceptWay.parseSpeed( osmProperties.get( "maxspeed" ) );
            if( maxspeed > 0 && speed > maxspeed )
                //outProperties.put( "car", maxspeed );
                speed = maxspeed;
/*            else {
                // not used on ways according to taginfo
                if( "city_limit".equals( osmProperties.get( "traffic_sign" ) ) )
                    speed = 50;
                //outProperties.put( "car", speed );
            }

            // usually used with a node, this does not work as intended
            if( "toll_booth".equals( osmProperties.get( "barrier" ) ) )
                outProperties.put( "carpaid", true );
*/
            if( hasTag( "oneway", oneways, osmProperties )) {
                //outProperties.put("caroneway", true);
                encoded = flags( speed, false );
                if( hasTag( "oneway", "-1", osmProperties ))
                {
                    //outProperties.put("caronewayreverse", true);
                    encoded = swapDirection( encoded );
                }
            }
            else
                encoded = flags( speed, true );

        }
        else {
            // TODO read duration and calculate speed 00:30 for ferry
//            Object duration = osmProperties.get("duration");
//            if (duration != null) {
//            }

            //outProperties.put("car", 20);
            encoded = flags( 10, true );
            //outProperties.put("carpaid", true);
        }

        return encoded;
    }

    @Override public String toString() {
        return "CAR";
    }

    /**
     * A map which associates string to speed. Get some impression:
     * http://www.itoworld.com/map/124#fullscreen
     * http://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Maxspeed
     */
    private static class CarSpeed extends HashMap<String, Integer> {

        {
            // autobahn
            put("motorway", 100);
            put("motorway_link", 70);
            // bundesstraße
            put("trunk", 70);
            put("trunk_link", 65);
            // linking bigger town
            put("primary", 65);
            put("primary_link", 60);
            // linking towns + villages
            put("secondary", 60);
            put("secondary_link", 50);
            // streets without middle line separation
            put("tertiary", 50);
            put("tertiary_link", 40);
            put("unclassified", 30);
            put("residential", 30);
            // spielstraße
            put("living_street", 10);
            put("service", 20);
            // unknown road
            put("road", 20);
            // forestry stuff
            put("track", 20);
        }
    }
}
