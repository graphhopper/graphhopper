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

import com.graphhopper.util.DistanceCalc;
import gnu.trove.list.array.TLongArrayList;
import java.util.Map;

/**
 * @author Peter Karich
 */
public class AcceptWay {

    private boolean car;
    private boolean publicTransport;
    private boolean bike;
    private boolean foot;

    public AcceptWay(boolean car, boolean publicTransport, boolean bike, boolean foot) {
        this.car = car;
        this.publicTransport = publicTransport;
        this.bike = bike;
        this.foot = foot;
    }

    public boolean acceptsCar() {
        return car;
    }

    public boolean acceptsPublicTransport() {
        return publicTransport;
    }

    public boolean acceptsBike() {
        return bike;
    }

    public boolean acceptsFoot() {
        return foot;
    }

    /**
     * Processes way properties of different kind to determine speed and
     * direction.
     *
     * @param outProperties will be filled with speed and other way information
     * about the way (derivived from the key+value)
     * @return true if a way (speed attribute) is determined from the specified
     * tag
     */
    public boolean handleTags(Map<String, Object> outProperties, TLongArrayList osmIds) {
        boolean includeWay = false;
        Object highwayValue = outProperties.get("highway");
        if ("highway" != null) {
//            if ("proposed".equals(val) || "preproposed".equals(val)
//                    || "platform".equals(val) || "raceway".equals(val)
//                    || "bus_stop".equals(val) || "bridleway".equals(val)
//                    || "construction".equals(val) || "no".equals(val) || "centre_line".equals(val))
//                // ignore
//                val = val;
//            else
//                logger.warn("unknown highway type:" + val);
            if (foot) {
                if ("footway".equals(highwayValue) || "path".equals(highwayValue) || "steps".equals(highwayValue)
                        || "pedestrian".equals(highwayValue) || "foot".equals(highwayValue)) {
                    includeWay = true;
                    outProperties.put("foot", 5);
                }
            }
            if (bike) {
                // add bike support later
                // http://wiki.openstreetmap.org/wiki/Cycleway
                // http://wiki.openstreetmap.org/wiki/Map_Features#Cycleway
                // https://github.com/Tristramg/osm4routing/blob/master/parameters.cc
                // + TODO toFlags
                // + some foot paths?
                if ("cycleway".equals(highwayValue) || "path".equals(highwayValue)) {
                    includeWay = true;
                    outProperties.put("bike", 10);
                }
            }

            if (publicTransport) {
            }

            if (car) {
                Integer integ = CarStreetType.SPEED.get((String) highwayValue);
                if (integ != null) {
                    int maxspeed = parseSpeed((String) outProperties.get("maxspeed")) / CarStreetType.FACTOR;
                    includeWay = true;
                    if (maxspeed > 0 && integ > maxspeed)
                        outProperties.put("car", maxspeed);
                    else
                        outProperties.put("car", integ);
                }
            }
        }

        Object routeValue = outProperties.get("route");
        if (routeValue != null
                && ("shuttle_train".equals(routeValue) || "ferry".equals(routeValue))) {
            if (car && isTrue(outProperties.get("motorcar"))
                    || bike && isTrue(outProperties.get("bike"))
                    || foot && isTrue(outProperties.get("foot"))) {

                int velo = 30;
                // TODO read duration and calculate speed 00:30
                Object duration = outProperties.get("duration");
                if (duration != null) {
                }

                includeWay = true;
                if (car)
                    outProperties.put("car", velo / CarStreetType.FACTOR);
                if (bike)
                    outProperties.put("bike", velo);
                if (foot)
                    outProperties.put("foot", velo);
            }
        }

        Object oneWayValue = outProperties.get("oneway");
        if (oneWayValue != null) {
            if (isTrue(((String) oneWayValue)))
                outProperties.put("oneway", true);
        }

        Object junctionValue = outProperties.get("oneway");
        if (junctionValue != null) {
            // abzweigung
            if ("roundabout".equals(junctionValue))
                outProperties.put("oneway", true);
        }
        return includeWay;
    }

    /**
     * @return the speed in km/h
     */
    static int parseSpeed(String str) {
        if (str == null || str.isEmpty())
            return -1;
        int kmInteger = str.indexOf("km");
        if (kmInteger > 0)
            str = str.substring(0, kmInteger).trim();

        // see https://en.wikipedia.org/wiki/Knot_%28unit%29#Definitions
        int mpInteger = str.indexOf("m");
        if (mpInteger > 0)
            str = str.substring(0, mpInteger).trim();

        int knotInteger = str.indexOf("knots");
        if (knotInteger > 0)
            str = str.substring(0, knotInteger).trim();

        try {
            int val = Integer.parseInt(str);
            if (mpInteger > 0)
                return (int) Math.round(val * 1.609);
            if (knotInteger > 0)
                return (int) Math.round(val * 1.852);
            return val;
        } catch (Exception ex) {
            return -1;
        }
    }

    boolean isTrue(Object str) {
        if (str == null)
            return false;
        return "yes".equals(str) || "true".equals(str) || "1".equals(str);
    }

    /**
     * Convert properties to 4 byte flags. The most significant byte is reserved
     * for car, then public transport, then bike and the last one for foot.
     *
     * Every byte contains the speed and the possible direction.
     */
    public int toFlags(Map<String, Object> properties) {
        int flags = 0;
        boolean bothways = !Boolean.TRUE.equals(properties.get("oneway"));
        Integer integ;
        if (car) {
            integ = (Integer) properties.get("car");
            if (integ != null) {
                // TODO avoid this second conversion
                integ *= CarStreetType.FACTOR;
                flags = CarStreetType.flags(integ, bothways);
            }
        }
        // TODO if(publicTransport)
        if (bike) {
            integ = (Integer) properties.get("bike");
            if (integ != null) {
                // TODO
                // flags |= BikeEdgeFlags.flags(integ, bothway);
            }
        }
        // TODO if(foot)
        return flags;
    }
}
