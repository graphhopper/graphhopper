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

import gnu.trove.list.array.TLongArrayList;
import java.util.Map;

/**
 * @author Peter Karich
 */
public class AcceptWay {

    private CarFlagsEncoder carEncoder = new CarFlagsEncoder();
    private FootFlagsEncoder footEncoder = new FootFlagsEncoder();
    private BikeFlagsEncoder bikeEncoder = new BikeFlagsEncoder();
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

    /**
     * Processes way properties of different kind to determine speed and
     * direction.
     *
     * @param outProperties will be filled with speed and other way information
     * about the way (derivived from the key+value)
     * @return true if a way (speed attribute) is determined from the specified
     * tag
     */
    public boolean handleTags(Map<String, Object> outProperties, Map<String, Object> osmProperties, TLongArrayList osmIds) {
        boolean includeWay = false;
        Object value = osmProperties.get("highway");
        if (value != null) {
            String highwayValue = (String) value;
            if (foot) {
                if (footEncoder.isAllowedHighway(highwayValue)
                        || osmProperties.get("sidewalk") != null) {
                    includeWay = true;
                    outProperties.put("foot", true);
                    outProperties.put("save", footEncoder.isSaveHighway(highwayValue));
                }
            }
            if (bike) {
                // http://wiki.openstreetmap.org/wiki/Cycleway
                // http://wiki.openstreetmap.org/wiki/Map_Features#Cycleway                                
                if (bikeEncoder.isAllowedHighway(highwayValue)) {
                    includeWay = true;
                    outProperties.put("bike", 10);
                    outProperties.put("save", bikeEncoder.isSaveHighway(highwayValue));
                }
            }

            if (car) {
                Integer integ = carEncoder.getSpeed(highwayValue);
                if (integ != null) {
                    int maxspeed = parseSpeed((String) osmProperties.get("maxspeed"));
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
        }

        value = osmProperties.get("route");
        if (value != null
                && ("shuttle_train".equals(value) || "ferry".equals(value))) {
            if (car && isTrue(osmProperties.get("motorcar"))
                    || bike && isTrue(osmProperties.get("bike"))
                    || foot && isTrue(osmProperties.get("foot"))) {

                int velo = 30;
                // TODO read duration and calculate speed 00:30 for ferry
                Object duration = osmProperties.get("duration");
                if (duration != null) {
                }

                includeWay = true;
                if (car)
                    outProperties.put("car", velo);
                if (bike)
                    outProperties.put("bike", velo);
                if (foot)
                    outProperties.put("foot", velo);
                outProperties.put("carpaid", true);
                outProperties.put("bikepaid", true);
            }
        }

        boolean oneWayForBike = !"no".equals(osmProperties.get("oneway:bicycle"));
        String cycleway = (String) osmProperties.get("cycleway");
        boolean oneWayBikeIsOpposite = bikeEncoder.isOpposite(cycleway);
        value = osmProperties.get("oneway");
        if (value != null) {
            if (isTrue(((String) value)))
                outProperties.put("caroneway", true);

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
        Integer integ;
        if (car) {
            integ = (Integer) properties.get("car");
            if (integ != null) {
                boolean bothways = !Boolean.TRUE.equals(properties.get("caroneway"));
                flags = carEncoder.flags(integ, bothways);
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
            str += "CAR,";
        if (acceptsBike())
            str += "BIKE,";
        if (acceptsFoot())
            str += "FOOT";
        return str;
    }

    public CombinedEncoder createCombination() {
        return new CombinedEncoder() {
            @Override public int swapDirection(int flags) {
                if (foot)
                    flags = footEncoder.swapDirection(flags);
                if (bike)
                    flags = bikeEncoder.swapDirection(flags);
                if (car)
                    flags = carEncoder.swapDirection(flags);
                return flags;
            }

            @Override public int flagsDefault(boolean bothDirections) {
                int res = 0;
                if (foot)
                    res |= footEncoder.flagsDefault(bothDirections);
                if (bike)
                    res |= bikeEncoder.flagsDefault(bothDirections);
                if (car)
                    res |= carEncoder.flagsDefault(bothDirections);
                return res;
            }
        };
    }
}
