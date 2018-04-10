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
import com.graphhopper.routing.util.AbstractFlagEncoder;
import com.graphhopper.routing.util.spatialrules.SpatialRule;
import com.graphhopper.routing.util.spatialrules.SpatialRuleLookup;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.GHPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class TagParserFactory {

    public static final Logger LOGGER = LoggerFactory.getLogger(TagParserFactory.class);
    public static final ReaderWayFilter ACCEPT_IF_HIGHWAY = new ReaderWayFilter() {
        @Override
        public boolean accept(ReaderWay way) {
            return way.getTag("highway") != null;
        }
    };

    private static final Set<String> fwdOneways = new HashSet<String>() {
        {
            add("yes");
            add("true");
            add("1");
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
    public static final String FOOT_ACCESS = "foot.access";
    public static final String FOOT_AVERAGE_SPEED = "foot.average_speed";

    public static TagParser createParser(final String parser) {
        switch(parser){
            case ROUNDABOUT: return new RoundaboutParser();
            case SURFACE: return new SurfaceParser();
            case CAR_MAX_SPEED: return new CarMaxSpeedParser();
            case ROAD_CLASS: return new RoadClassParser();
            default: throw new IllegalArgumentException("Unsupported TagParser type " + parser);
        }
    }

    public static TagParser createRoundabout(final BooleanEncodedValue ev) {
        return new AbstractTagParser(ev) {
            @Override
            public void parse(IntsRef ints, ReaderWay way) {
                if (way.hasTag("junction", "roundabout")) ev.setBool(false, ints, true);
            }

            @Override
            public ReaderWayFilter getReadWayFilter() {
                return ACCEPT_IF_HIGHWAY;
            }

        };
    }

    /**
     * For OpenStreetMap this TagParser handles the highway tag and sets it to "ferry" if this is a ferry relation.
     */
    public static TagParser createRoadClass(final StringEncodedValue ev) {
        return new AbstractTagParser(ev) {
            @Override
            public void parse(IntsRef ints, ReaderWay way) {
                ev.setString(false, ints, way.getTag("highway"));
            }

            @Override
            public ReaderWayFilter getReadWayFilter() {
                return ACCEPT_IF_HIGHWAY;
            }
        };
    }

    public static TagParser createSurface(final StringEncodedValue ev) {
        return new AbstractTagParser(ev) {
            @Override
            public void parse(IntsRef ints, ReaderWay way) {
                ev.setString(false, ints, way.getTag("surface"));
            }

            @Override
            public ReaderWayFilter getReadWayFilter() {
                return ACCEPT_IF_HIGHWAY;
            }
        };
    }

    public static TagParser createSpatialRuleId(final SpatialRuleLookup spatialRuleLookup, final IntEncodedValue spatialId) {
        return new AbstractTagParser(spatialId) {
            @Override
            public ReaderWayFilter getReadWayFilter() {
                return ACCEPT_IF_HIGHWAY;
            }

            @Override
            public void parse(IntsRef ints, ReaderWay way) {
                GHPoint estimatedCenter = way.getTag("estimated_center", null);
                if (estimatedCenter != null) {
                    SpatialRule rule = spatialRuleLookup.lookupRule(estimatedCenter);
                    spatialId.setInt(false, ints, spatialRuleLookup.getSpatialId(rule));
                }
            }
        };
    }

    /**
     * For OpenStreetMap this TagParser handles the road environment like if the transportation happens on a ferry or tunnel.
     */
    public static TagParser createRoadEnvironment(final StringEncodedValue roadEnvEnc, final List<String> roadEnvOrder) {
        if (roadEnvOrder.isEmpty())
            throw new IllegalArgumentException("Road environment list mustn't be empty");

        return new AbstractTagParser(roadEnvEnc) {
            @Override
            public ReaderWayFilter getReadWayFilter() {
                return ACCEPT_IF_HIGHWAY;
            }

            @Override
            public void parse(IntsRef ints, ReaderWay way) {
                // TODO use roadEnvEnc.getDefault instead
                String roadEnv = roadEnvOrder.get(0);
                for (String tm : roadEnvOrder) {
                    if (way.hasTag(tm)) {
                        roadEnv = tm;
                        break;
                    }
                }

                roadEnvEnc.setString(false, ints, roadEnv);
            }
        };
    }

    public static TagParser createMaxWeight(final DecimalEncodedValue ev, final ReaderWayFilter filter) {
        final List<String> weightTags = Arrays.asList("maxweight", "maxgcweight");
        return new AbstractTagParser(ev) {
            @Override
            public void parse(IntsRef ints, ReaderWay way) {
                String value = way.getFirstPriorityTag(weightTags);
                if (Helper.isEmpty(value)) return;

                try {
                    ev.setDecimal(false, ints, stringToTons(value));
                } catch (Throwable ex) {
                    LOGGER.warn("Unable to extract tons from malformed road attribute '{}' for way (OSM_ID = {}).", value, way.getId(), ex);
                    return;
                }
            }

            @Override
            public ReaderWayFilter getReadWayFilter() {
                return filter;
            }
        };
    }

    public static TagParser createMaxHeight(final DecimalEncodedValue ev, final ReaderWayFilter filter) {
        final List<String> heightTags = Arrays.asList("maxheight", "maxheight:physical");

        return new AbstractTagParser(ev) {
            @Override
            public void parse(IntsRef ints, ReaderWay way) {
                String value = way.getFirstPriorityTag(heightTags);
                if (Helper.isEmpty(value)) return;

                try {
                    ev.setDecimal(false, ints, stringToMeter(value));
                } catch (Throwable ex) {
                    LOGGER.warn("Unable to extract height from malformed road attribute '{}' for way (OSM_ID = {}).", value, way.getId(), ex);
                    return;
                }
            }

            @Override
            public ReaderWayFilter getReadWayFilter() {
                return filter;
            }
        };
    }

    public static TagParser createMaxWidth(final DecimalEncodedValue ev, final ReaderWayFilter filter) {
        final List<String> widthTags = Arrays.asList("maxwidth", "maxwidth:physical");

        return new AbstractTagParser(ev) {
            @Override
            public void parse(IntsRef ints, ReaderWay way) {
                String value = way.getFirstPriorityTag(widthTags);
                if (Helper.isEmpty(value)) return;

                try {
                    ev.setDecimal(false, ints, stringToMeter(value));
                } catch (Throwable ex) {
                    LOGGER.warn("Unable to extract width from malformed road attribute '{}' for way (OSM_ID = {}).", value, way.getId(), ex);
                    return;
                }
            }

            @Override
            public ReaderWayFilter getReadWayFilter() {
                return filter;
            }
        };
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

    public static class Car {

        public static TagParser createMaxSpeed(final DecimalEncodedValue ev, final ReaderWayFilter filter) {
            return new AbstractTagParser(ev) {
                @Override
                public void parse(IntsRef ints, ReaderWay way) {
                    assert filter.accept(way);

                    double val = AbstractFlagEncoder.parseSpeed(way.getTag("maxspeed"));
                    if (val < 0)
                        return;
                    ev.setDecimal(false, ints, val);
                }

                @Override
                public ReaderWayFilter getReadWayFilter() {
                    return filter;
                }
            };
        }

        public static TagParser createAverageSpeed(final DecimalEncodedValue ev, final Map<String, Double> speedMap) {
            final ReaderWayFilter acceptKnownRoadClasses = new ReaderWayFilter() {
                @Override
                public boolean accept(ReaderWay way) {
                    return speedMap.containsKey(way.getTag("highway"));
                }
            };

            return new AbstractTagParser(ev) {
                @Override
                public void parse(IntsRef ints, ReaderWay way) {
                    assert acceptKnownRoadClasses.accept(way);

                    double num = AbstractFlagEncoder.parseSpeed(way.getTag("maxspeed"));
                    Double defaultSp = speedMap.get(way.getTag("highway"));
                    if (defaultSp == null)
                        throw new IllegalStateException("Illegal tag '" + way.getTag("highway") + "' should not happen as filtered before");

                    ev.setDecimal(false, ints, num < 0.01 ? defaultSp : num * 0.9);
                }

                @Override
                public ReaderWayFilter getReadWayFilter() {
                    return acceptKnownRoadClasses;
                }
            };
        }

        public static TagParser createAccess(final BooleanEncodedValue access, final ReaderWayFilter acceptKnownRoadClasses) {
            return new AbstractTagParser(access) {

                @Override
                public void parse(IntsRef ints, ReaderWay way) {
                    assert acceptKnownRoadClasses.accept(way);

                    if (way.hasTag("oneway", "-1")) {
                        access.setBool(true, ints, true);
                    } else if (way.hasTag("oneway", fwdOneways)
                            || way.hasTag("junction", "roundabout")) {
                        access.setBool(false, ints, true);
                    } else {
                        access.setBool(false, ints, true);
                        access.setBool(true, ints, true);
                    }
                }

                @Override
                public ReaderWayFilter getReadWayFilter() {
                    return acceptKnownRoadClasses;
                }
            };
        }

        public static Map<String, Double> createSpeedMap() {
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
    }

    public static class Foot {
        public static final double SLOW_SPEED = 2d;
        public static final double MEAN_SPEED = 5d;
        public static final double FERRY_SPEED = 10d;

        public static final TagParser createAverageSpeed(final DecimalEncodedValue encodedValue) {
            return new AbstractTagParser(encodedValue) {

                @Override
                public ReaderWayFilter getReadWayFilter() {
                    return ACCEPT_IF_HIGHWAY;
                }

                @Override
                public void parse(IntsRef ints, ReaderWay way) {
                    String sacScale = way.getTag("sac_scale");
                    if (sacScale != null) {
                        if ("hiking".equals(sacScale))
                            encodedValue.setDecimal(false, ints, MEAN_SPEED);
                        else
                            encodedValue.setDecimal(false, ints, SLOW_SPEED);
                    } else {
                        encodedValue.setDecimal(false, ints, MEAN_SPEED);
                    }
                }
            };
        }

        public static TagParser createAccess(final BooleanEncodedValue access, final ReaderWayFilter acceptKnownRoadClasses) {
            return new AbstractTagParser(access) {
                @Override
                public void parse(IntsRef ints, ReaderWay way) {
                    assert acceptKnownRoadClasses.accept(way) : way.toString();

                    access.setBool(false, ints, true);
                    access.setBool(true, ints, true);
                }

                @Override
                public ReaderWayFilter getReadWayFilter() {
                    return acceptKnownRoadClasses;
                }
            };
        }
    }

    public static class Bike {

        public static TagParser createAverageSpeed(final DecimalEncodedValue averageSpeedEnc) {
            return new AbstractTagParser(averageSpeedEnc) {
                @Override
                public ReaderWayFilter getReadWayFilter() {
                    return ACCEPT_IF_HIGHWAY;
                }

                @Override
                public void parse(IntsRef ints, ReaderWay way) {
                    // TODO NOW
                }
            };
        }

        public static TagParser createAccess(final BooleanEncodedValue access, final ReaderWayFilter acceptKnownRoadClasses) {
            return new AbstractTagParser(access) {
                @Override
                public void parse(IntsRef ints, ReaderWay way) {
                    assert acceptKnownRoadClasses.accept(way);

                    if (way.hasTag("oneway", fwdOneways)
                            || way.hasTag("junction", "roundabout")) {
                        access.setBool(false, ints, true);
                    } else {
                        access.setBool(false, ints, true);
                        access.setBool(true, ints, true);
                    }
                }

                @Override
                public ReaderWayFilter getReadWayFilter() {
                    return acceptKnownRoadClasses;
                }
            };
        }
    }

    private static abstract class AbstractTagParser implements TagParser {
        private EncodedValue ev;

        public AbstractTagParser(EncodedValue ev) {
            this.ev = ev;
        }

        @Override
        public final EncodedValue getEncodedValue() {
            return ev;
        }

        @Override
        public final String toString() {
            return ev.toString();
        }

        @Override
        public final String getName() {
            return ev.getName();
        }
    }
}
