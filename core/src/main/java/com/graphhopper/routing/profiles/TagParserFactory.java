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
package com.graphhopper.routing.profiles;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.tagparsers.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class TagParserFactory {

    public static final Logger LOGGER = LoggerFactory.getLogger(TagParserFactory.class);
    private static Map<String, Double> speedMap = createCarSpeedMap();
    private static Set<String> footAccessTags = createFootAccessTags();
    public static final Set<String> fwdOneways = new HashSet<String>() {
        {
            add("yes");
            add("true");
            add("1");
        }
    };

    public static final ReaderWayFilter ACCEPT_IF_HIGHWAY = new ReaderWayFilter() {
        @Override
        public boolean accept(ReaderWay way) {
            return way.getTag("highway") != null;
        }
    };

    public static final ReaderWayFilter SPEEDMAPFILTER = new ReaderWayFilter() {
        @Override
        public boolean accept(ReaderWay way) {
            return speedMap.containsKey(way.getTag("highway"));
        }
    };

    public static final ReaderWayFilter FOOTACCESSFILTER = new ReaderWayFilter() {
        @Override
        public boolean accept(ReaderWay way) {
            return footAccessTags.contains(way.getTag("highway"));
        }
    };

    public static final String ROUNDABOUT = "roundabout";
    public static final String ROAD_CLASS = "road_class";
    public static final String ROAD_ENVIRONMENT = "road_environment";
    public static final String SURFACE = "surface";
    public static final String MAX_HEIGHT = "max_height";
    public static final String MAX_WEIGHT = "max_weight";
    public static final String MAX_WIDTH = "max_width";
    public static final String SPATIAL_RULE_ID = "spatial_rule_id";
    public static final String CURVATURE = "curvature";
    public static final String CAR_ACCESS = "car.access";
    public static final String CAR_MAX_SPEED = "car.max_speed";
    public static final String CAR_AVERAGE_SPEED = "car.average_speed";
    public static final String BIKE_ACCESS = "bike.access";
    public static final String BIKE_AVERAGE_SPEED = "bike.average_speed";
    public static final String BIKE_PRIORITY = "bike.priority";
    public static final String FOOT_ACCESS = "foot.access";
    public static final String FOOT_AVERAGE_SPEED = "foot.average_speed";

    public static class Foot {
        public static final double FOOT_SLOW_SPEED = 2d;
        public static final double FOOT_MEAN_SPEED = 5d;
        public static final double FOOT_FERRY_SPEED = 10d;
    }



    public static TagParser createParser(final String parser) {
        switch(parser){
            case CAR_MAX_SPEED: return new CarMaxSpeedParser();
            case CAR_ACCESS: return new CarAccessParser();
            case CAR_AVERAGE_SPEED: return new CarAverageSpeedParser();

            case BIKE_ACCESS: return new BikeAccessParser();
            case BIKE_AVERAGE_SPEED: return new BikeAverageSpeedParser();

            case FOOT_ACCESS: return new FootAccessParser();
            case FOOT_AVERAGE_SPEED: return new FootAverageSpeedParser();

            case SPATIAL_RULE_ID: return new SpatialRuleIdParser();
            case ROUNDABOUT: return new RoundaboutParser();
            case SURFACE: return new SurfaceParser();
            case ROAD_CLASS: return new RoadClassParser();
            case ROAD_ENVIRONMENT: return new RoadEnvironmentParser();
            case MAX_HEIGHT: return new MaxHeightParser();
            case MAX_WEIGHT: return new MaxWeightParser();
            case MAX_WIDTH: return new MaxWidthParser();
            default: throw new IllegalArgumentException("Unsupported TagParser type " + parser);
        }
    }


    public static double stringToTons(String value) {
        value = value.toLowerCase().replaceAll(" ", "").replaceAll("(tons|ton)", "t");
        value = value.replace("mgw", "").trim();
        double factor = 1;
        if (value.endsWith("t")) {
            value = value.substring(0, value.length() - 1);
        } else if (value.endsWith("lbs")) {
            value = value.substring(0, value.length() - 3);
            factor = 0.00045359237;
        }

        return Double.parseDouble(value) * factor;
    }

    public static double stringToMeter(String value) {
        value = value.toLowerCase().replaceAll(" ", "").replaceAll("(meters|meter|mtrs|mtr|mt|m\\.)", "m");
        double factor = 1;
        double offset = 0;
        value = value.replaceAll("(\\\"|\'\')", "in").replaceAll("(\'|feet)", "ft");
        if (value.startsWith("~") || value.contains("approx")) {
            value = value.replaceAll("(\\~|approx)", "").trim();
            factor = 0.8;
        }

        if (value.endsWith("in")) {
            int startIndex = value.indexOf("ft");
            String inchValue;
            if (startIndex < 0) {
                startIndex = 0;
            } else {
                startIndex += 2;
            }

            inchValue = value.substring(startIndex, value.length() - 2);
            value = value.substring(0, startIndex);
            offset = Double.parseDouble(inchValue) * 0.0254;
        }

        if (value.endsWith("ft")) {
            value = value.substring(0, value.length() - 2);
            factor *= 0.3048;
        } else if (value.endsWith("m")) {
            value = value.substring(0, value.length() - 1);
        }

        if (value.isEmpty()) {
            return offset;
        } else {
            return Double.parseDouble(value) * factor + offset;
        }
    }

    public static Map<String, Double> createCarSpeedMap() {
        Map<String, Double> map = new LinkedHashMap<>();
        // autobahn
        map.put("motorway", 100d);
        map.put("motorway_link", 70d);
        map.put("motorroad", 90d);
        // bundesstraße
        map.put("trunk", 70d);
        map.put("trunk_link", 65d);
        // linking bigger town
        map.put("primary", 65d);
        map.put("primary_link", 60d);
        // linking towns + villages
        map.put("secondary", 60d);
        map.put("secondary_link", 50d);
        // streets without middle line separation
        map.put("tertiary", 50d);
        map.put("tertiary_link", 40d);
        map.put("unclassified", 30d);
        map.put("residential", 30d);
        // spielstraße
        map.put("living_street", 5d);
        map.put("service", 20d);
        // unknown road
        map.put("road", 20d);
        // forestry stuff
        map.put("track", 15d);
        return map;
    }

    public static Set<String> createFootAccessTags() {
        Set<String> map = new HashSet<String>();
        map.add("footway");
        map.add("path");
        map.add("steps");
        map.add("pedestrian");
        map.add("living_street");
        map.add("track");
        map.add("residential");
        map.add("service");

        map.add("trunk");
        map.add("trunk_link");
        map.add("primary");
        map.add("primary_link");
        map.add("secondary");
        map.add("secondary_link");
        map.add("tertiary");
        map.add("tertiary_link");

        map.add("cycleway");
        map.add("unclassified");
        map.add("road");

        return map;
    }
    
    //TODO
    public static Map<String, Double> createBikeSpeedMap(){
        Map<String, Double> map = new LinkedHashMap<>();
    return map;
    }

    public static Map<String, Double> getSpeedMap() {
        return speedMap;
    }

}
