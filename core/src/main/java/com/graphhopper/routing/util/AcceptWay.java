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

import com.graphhopper.util.Helper;
import gnu.trove.list.array.TLongArrayList;
import java.util.Map;

/**
 * @author Peter Karich
 */
public class AcceptWay {

    private CarFlagEncoder carEncoder = new CarFlagEncoder();
    private FootFlagEncoder footEncoder = new FootFlagEncoder();
    private BikeFlagEncoder bikeEncoder = new BikeFlagEncoder();
    private boolean car;
    private boolean bike;
    private boolean foot;

    public AcceptWay(boolean car, boolean bike, boolean foot) {
        this.car = car;
        this.bike = bike;
        this.foot = foot;
    }

    public AcceptWay foot(boolean foot) {
        this.foot = foot;
        return this;
    }

    public AcceptWay car(boolean car) {
        this.car = car;
        return this;
    }

    public boolean acceptsCar() {
        return car;
    }

    public boolean acceptsBike() {
        return bike;
    }

    public boolean acceptsFoot() {
        return foot;
    }

    /*
    Determine whether an osm way is a routable way
     */
    public boolean accept( Map<String, String> osmProperties ) {
        boolean includeWay = false;
        String value = osmProperties.get("highway");
        if (value != null) {
            if (foot && footEncoder.isAllowed(osmProperties)) {
                includeWay = true;
            }
            if (bike && bikeEncoder.isAllowed(osmProperties)) {
                includeWay = true;
            }

            if (car && carEncoder.isAllowed(osmProperties)) {
                includeWay = true;
            }
        }

        value = osmProperties.get("route");
        if (value != null
                && ("shuttle_train".equals(value) || "ferry".equals(value))) {
            Object motorcarProp = osmProperties.get("motorcar");
            Object bikeProp = osmProperties.get("bike");
            Object footProp = osmProperties.get("motorcar");
            boolean allEmpty = motorcarProp == null && bikeProp == null && footProp == null;
            if (car && (allEmpty || isTrue(motorcarProp))
                    || bike && (allEmpty || isTrue(bikeProp))
                    || foot && (allEmpty || isTrue(footProp))) {

                includeWay = true;
            }
        }
        return includeWay;
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
    public boolean handleTags(Map<String, Object> outProperties,
            Map<String, String> osmProperties, TLongArrayList osmIds) {
        boolean includeWay = false;
        String value = osmProperties.get("highway");
        if (value != null) {
            String highwayValue = value;
            if (foot && footEncoder.isAllowed(osmProperties)) {
                includeWay = true;
                outProperties.put("foot", true);
                outProperties.put("footsave", footEncoder.isSaveHighway(highwayValue));
            }
            if (bike && bikeEncoder.isAllowed(osmProperties)) {
                // http://wiki.openstreetmap.org/wiki/Cycleway
                // http://wiki.openstreetmap.org/wiki/Map_Features#Cycleway
                includeWay = true;
                outProperties.put("bike", bikeEncoder.getSpeed(value));
                outProperties.put("bikesave", bikeEncoder.isSaveHighway(highwayValue));
            }

            if (car && carEncoder.isAllowed(osmProperties)) {
                Integer integ = carEncoder.getSpeed(highwayValue);
                int maxspeed = parseSpeed(osmProperties.get("maxspeed"));
                includeWay = true;
                if (maxspeed > 0 && integ > maxspeed)
                    outProperties.put("car", maxspeed);
                else {
                    if ("city_limit".equals(osmProperties.get("traffic_sign")))
                        integ = 50;
                    outProperties.put("car", integ);
                }

                if ("toll_booth".equals(osmProperties.get("barrier")))
                    outProperties.put("carpaid", true);
            }
        }

        value = osmProperties.get("route");
        if (value != null
                && ("shuttle_train".equals(value) || "ferry".equals(value))) {
            Object motorcarProp = osmProperties.get("motorcar");
            Object bikeProp = osmProperties.get("bike");
            Object footProp = osmProperties.get("motorcar");
            boolean allEmpty = motorcarProp == null && bikeProp == null && footProp == null;
            if (car && (allEmpty || isTrue(motorcarProp))
                    || bike && (allEmpty || isTrue(bikeProp))
                    || foot && (allEmpty || isTrue(footProp))) {

                // TODO read duration and calculate speed 00:30 for ferry
                Object duration = osmProperties.get("duration");
                if (duration != null) {
                }

                includeWay = true;
                if (car)
                    outProperties.put("car", 20);
                if (bike)
                    outProperties.put("bike", 10);
                if (foot)
                    outProperties.put("foot", true);
                outProperties.put("carpaid", true);
                outProperties.put("bikepaid", true);
            }
        }

        boolean oneWayForBike = !"no".equals(osmProperties.get("oneway:bicycle"));
        String cycleway = osmProperties.get("cycleway");
        boolean oneWayBikeIsOpposite = bikeEncoder.isOpposite(cycleway);
        value = osmProperties.get("oneway");
        if (value != null) {
            if (isTrue(value))
                outProperties.put("caroneway", true);
            else if( "-1".equals( value ))
            {
                outProperties.put("caroneway", true);
                outProperties.put("caronewayreverse", true);
            }

            // Abzweigung
            if ("roundabout".equals(value))
                outProperties.put("caroneway", true);

            if (oneWayForBike && Boolean.TRUE.equals(outProperties.get("caroneway"))) {
                outProperties.put("bikeoneway", true);
                outProperties.put("bikeopposite", oneWayBikeIsOpposite);
            }
        }
        return includeWay;
    }

    /**
     * @return the speed in km/h
     */
    static int parseSpeed(String str) {
        if (Helper.isEmpty(str))
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

    public int countVehicles() {
        int count = 0;
        if (car)
            count++;
        if (bike)
            count++;
        if (foot)
            count++;
        return count;
    }

    public boolean accepts(EdgePropertyEncoder encoder) {
        if (car && encoder instanceof CarFlagEncoder)
            return true;
        else if (bike && encoder instanceof BikeFlagEncoder)
            return true;
        else if (foot && encoder instanceof FootFlagEncoder)
            return true;
        return false;
    }

    boolean isTrue(Object obj) {
        if (obj == null)
            return false;
        return "yes".equals(obj) || "true".equals(obj) || "1".equals(obj);
    }

    /**
     * Convert properties to 4 byte flags. The most significant byte is reserved
     * for car, then public transport, then bike and the last one for foot.
     *
     * Every byte contains the speed and the possible direction.
     */
    public int toFlags(Map<String, Object> properties) {
        int flags = 0;
        Integer integ;
        if (car) {
            integ = (Integer) properties.get("car");
            if (integ != null) {
                boolean bothways = !Boolean.TRUE.equals(properties.get("caroneway"));
                flags = carEncoder.flags(integ, bothways);

                if( Boolean.TRUE.equals(properties.get("caronewayreverse")) )
                    flags = carEncoder.swapDirection( flags );
            }
        }

        if (bike) {
            integ = (Integer) properties.get("bike");
            if (integ != null) {
                boolean bothways = !Boolean.TRUE.equals(properties.get("bikeoneway"));
                int tmp = bikeEncoder.flags(integ, bothways);
                boolean opposite = Boolean.TRUE.equals(properties.get("bikeopposite"));
                if (!bothways && opposite)
                    tmp = bikeEncoder.swapDirection(tmp);
                flags |= tmp;
            }
        }

        if (foot && Boolean.TRUE.equals(properties.get("foot")))
            flags |= footEncoder.flagsDefault(true);
        return flags;
    }

    @Override
    public String toString() {
        String str = "";
        if (acceptsCar())
            str += "CAR ";
        if (acceptsBike())
            str += "BIKE ";
        if (acceptsFoot())
            str += "FOOT";
        return str.trim().replaceAll("\\ ", ",");
    }

    public static AcceptWay parse(String acceptWayString) {
        return new AcceptWay(acceptWayString.contains("CAR"),
                acceptWayString.contains("BIKE"),
                acceptWayString.contains("FOOT"));
    }
}