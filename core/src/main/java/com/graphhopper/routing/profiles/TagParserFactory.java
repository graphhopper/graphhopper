package com.graphhopper.routing.profiles;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.util.AbstractFlagEncoder;
import com.graphhopper.util.EdgeIteratorState;

public class TagParserFactory {

    public static TagParser createRoundabout(final BooleanEncodedValue ev) {
        return new TagParser() {
            @Override
            public void parse(EdgeSetter setter, ReaderWay way, EdgeIteratorState edgeState) {
                setter.set(edgeState, ev, way.hasTag("junction", "roundabout"));
            }

            @Override
            public String getName() {
                return "roundabout";
            }

            @Override
            public EncodedValue getEncodedValue() {
                return ev;
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
            public String getName() {
                return "highway";
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
                public String getName() {
                    return "maxspeed";
                }
            };
        }

        public static TagParser createAverageSpeed(final DecimalEncodedValue ev) {
            return new TagParser() {
                @Override
                public void parse(EdgeSetter setter, ReaderWay way, EdgeIteratorState edgeState) {
                    double num = AbstractFlagEncoder.parseSpeed(way.getTag("maxspeed"));
                    // TODO use highway tags etc instead!
                    setter.set(edgeState, ev, num < 0.01 ? 20 : num * 0.9);
                }

                @Override
                public EncodedValue getEncodedValue() {
                    return ev;
                }

                @Override
                public String getName() {
                    return "averagespeed";
                }
            };
        }

        public static TagParser createAccess(final BooleanEncodedValue access) {
            return new TagParser() {

                @Override
                public String getName() {
                    return "access";
                }

                @Override
                public void parse(EdgeSetter setter, ReaderWay way, EdgeIteratorState edgeState) {
                    if (way.getTag("oneway", "").equals("yes")) {
                        setter.set(edgeState, access, true);
                    } else {
                        setter.set(edgeState, access, true);
                        setter.set(edgeState.detach(true), access, true);
                    }
                }

                @Override
                public EncodedValue getEncodedValue() {
                    return access;
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
                public String getName() {
                    return "weight";
                }
            };
        }
    }
}
