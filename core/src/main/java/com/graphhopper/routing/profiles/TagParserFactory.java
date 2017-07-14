package com.graphhopper.routing.profiles;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.util.AbstractFlagEncoder;

public class TagParserFactory {

    public static TagParser createRoundabout() {
        return new TagParser() {
            @Override
            public Object parse(ReaderWay way) {
                return way.hasTag("junction", "roundabout");
            }

            @Override
            public String getName() {
                return "roundabout";
            }
        };
    }

    public static TagParser createHighway() {
        return new TagParser() {
            @Override
            public Object parse(ReaderWay way) {
                return way.getTag("highway");
            }

            @Override
            public String getName() {
                return "highway";
            }
        };
    }

    public static class Car {
        public static TagParser createMaxSpeed() {
            return new TagParser() {
                @Override
                public Object parse(ReaderWay way) {
                    double val = AbstractFlagEncoder.parseSpeed(way.getTag("maxspeed"));
                    if (val < 0)
                        return null;
                    return val;
                }

                @Override
                public String getName() {
                    return "maxspeed";
                }
            };
        }

        public static TagParser createAverageSpeed() {
            return new TagParser() {
                @Override
                public Object parse(ReaderWay way) {
                    double num = AbstractFlagEncoder.parseSpeed(way.getTag("maxspeed"));
                    // TODO use highway tags etc instead!
                    if (num < 0.01)
                        return 20;
                    return num * 0.9;
                }

                @Override
                public String getName() {
                    return "averagespeed";
                }
            };
        }
    }

    public static class Truck {
        public static TagParser createWeight() {
            return new TagParser() {
                @Override
                public Object parse(ReaderWay way) {
                    try {
                        return Double.parseDouble(way.getTag("weight"));
                    } catch (Exception value) {
                        return null;
                    }
                }

                @Override
                public String getName() {
                    return "weight";
                }
            };
        }
    }
}
