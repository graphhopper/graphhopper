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
import com.graphhopper.routing.util.AbstractFlagEncoder;
import com.graphhopper.util.EdgeIteratorState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TagParserFactory {

    public static final ReaderWayFilter ACCEPT_IF_HIGHWAY = new ReaderWayFilter() {
        @Override
        public boolean accept(ReaderWay way) {
            return way.getTag("highway") != null;
        }
    };

    public static TagParser createRoundabout(final BooleanEncodedValue ev) {
        return new TagParser() {
            @Override
            public void parse(EdgeSetter setter, ReaderWay way, EdgeIteratorState edgeState) {
                setter.set(edgeState, ev, way.hasTag("junction", "roundabout"));
            }


            @Override
            public EncodedValue getEncodedValue() {
                return ev;
            }

            @Override
            public ReaderWayFilter getReadWayFilter() {
                return ACCEPT_IF_HIGHWAY;
            }

            @Override
            public String getName() {
                return "roundabout";
            }

            // TODO should we use toString instead of getName for the identification?
            @Override
            public String toString() {
                return getName();
            }
        };
    }

    public static TagParser createHighway(final StringEncodedValue ev) {
        return new TagParser() {
            @Override
            public void parse(EdgeSetter setter, ReaderWay way, EdgeIteratorState edgeState) {
                setter.set(edgeState, ev, way.getTag("highway"));
            }

            @Override
            public EncodedValue getEncodedValue() {
                return ev;
            }

            @Override
            public ReaderWayFilter getReadWayFilter() {
                return ACCEPT_IF_HIGHWAY;
            }

            @Override
            public String getName() {
                return "highway";
            }

            @Override
            public String toString() {
                return getName();
            }
        };
    }

    public static class Car {
        public static TagParser createMaxSpeed(final DecimalEncodedValue ev) {
            return new TagParser() {
                @Override
                public void parse(EdgeSetter setter, ReaderWay way, EdgeIteratorState edgeState) {
                    double val = AbstractFlagEncoder.parseSpeed(way.getTag("maxspeed"));
                    if (val < 0)
                        return;
                    setter.set(edgeState, ev, val);
                }

                @Override
                public EncodedValue getEncodedValue() {
                    return ev;
                }

                @Override
                public ReaderWayFilter getReadWayFilter() {
                    return ACCEPT_CAR_HIGHWAYS;
                }

                @Override
                public String getName() {
                    return "max_speed";
                }

                @Override
                public String toString() {
                    return getName();
                }
            };
        }

        private final static Map<String, Double> defaultSpeedMap = new HashMap<String, Double>() {
            {
                // autobahn
                put("motorway", 100d);
                put("motorway_link", 70d);
                put("motorroad", 90d);
                // bundesstraße
                put("trunk", 70d);
                put("trunk_link", 65d);
                // linking bigger town
                put("primary", 65d);
                put("primary_link", 60d);
                // linking towns + villages
                put("secondary", 60d);
                put("secondary_link", 50d);
                // streets without middle line separation
                put("tertiary", 50d);
                put("tertiary_link", 40d);
                put("unclassified", 30d);
                put("residential", 30d);
                // spielstraße
                put("living_street", 5d);
                put("service", 20d);
                // unknown road
                put("road", 20d);
                // forestry stuff
                put("track", 15d);
            }
        };

        private static final ReaderWayFilter ACCEPT_CAR_HIGHWAYS = new ReaderWayFilter() {
            @Override
            public boolean accept(ReaderWay way) {
                return defaultSpeedMap.containsKey(way.getTag("highway"));
            }
        };

        public static TagParser createAverageSpeed(final DecimalEncodedValue ev) {
            return new TagParser() {
                @Override
                public void parse(EdgeSetter setter, ReaderWay way, EdgeIteratorState edgeState) {
                    double num = AbstractFlagEncoder.parseSpeed(way.getTag("max_speed"));
                    Double defaultSp = defaultSpeedMap.get(way.getTag("highway"));
                    // TODO should not happen as arranged via access
                    if (defaultSp == null)
                        defaultSp = 20d;
                    setter.set(edgeState, ev, num < 0.01 ? defaultSp : num * 0.9);
                }

                @Override
                public EncodedValue getEncodedValue() {
                    return ev;
                }

                @Override
                public ReaderWayFilter getReadWayFilter() {
                    return ACCEPT_CAR_HIGHWAYS;
                }

                @Override
                public String getName() {
                    return "average_speed";
                }

                @Override
                public String toString() {
                    return getName();
                }
            };
        }

        public static TagParser createAccess(final BooleanEncodedValue access) {
            return new TagParser() {

                Set<String> fwdOneways = new HashSet<String>() {
                    {
                        add("yes");
                        add("true");
                        add("1");
                    }
                };


                @Override
                public void parse(EdgeSetter setter, ReaderWay way, EdgeIteratorState edgeState) {
                    if (defaultSpeedMap.containsKey(way.getTag("highway"))) {
                        if (way.hasTag("oneway", fwdOneways)
                                || way.hasTag("junction", "roundabout")) {
                            setter.set(edgeState, access, true);
                        } else {
                            setter.set(edgeState, access, true);
                            setter.set(edgeState.detach(true), access, true);
                        }
                    }
                }

                @Override
                public EncodedValue getEncodedValue() {
                    return access;
                }

                @Override
                public ReaderWayFilter getReadWayFilter() {
                    return ACCEPT_CAR_HIGHWAYS;
                }

                @Override
                public String getName() {
                    return "access";
                }

                @Override
                public String toString() {
                    return getName();
                }
            };
        }
    }

    public static class Truck {
        public static TagParser createWeight(final DecimalEncodedValue ev) {
            return new TagParser() {
                @Override
                public void parse(EdgeSetter setter, ReaderWay way, EdgeIteratorState edgeState) {
                    try {
                        setter.set(edgeState, ev, Double.parseDouble(way.getTag("weight")));
                    } catch (Exception value) {
                        return;
                    }
                }

                @Override
                public EncodedValue getEncodedValue() {
                    return ev;
                }

                @Override
                public ReaderWayFilter getReadWayFilter() {
                    return Car.ACCEPT_CAR_HIGHWAYS;
                }

                @Override
                public String getName() {
                    return "weight";
                }

                @Override
                public String toString() {
                    return getName();
                }
            };
        }
    }
}
