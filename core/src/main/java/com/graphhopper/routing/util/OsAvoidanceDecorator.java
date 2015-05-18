package com.graphhopper.routing.util;

import com.graphhopper.reader.Way;

/**
 * Created by sadam on 4/15/15.
 */
public class OsAvoidanceDecorator extends AbstractAvoidanceDecorator {

    protected enum AvoidanceType implements EdgeAttribute {
        ARoad(1) {
            @Override
            public boolean isValidForWay(Way way) {
                return hasTag(way, "highway", "primary");
            }
        },
        Boulders(2) {
            @Override
            public boolean isValidForWay(Way way) {
                return hasTag(way, "natural", "boulders");
            }
        },
        Cliff(4) {
            @Override
            public boolean isValidForWay(Way way) {
                return hasTag(way, "natural", "cliff");
            }
        },
        InlandWater(8) {
            @Override
            public boolean isValidForWay(Way way) {
                return hasTag(way, "natural", "water") && hasTag(way, "tidal", "no");
            }
        },
        Marsh(16) {
            @Override
            public boolean isValidForWay(Way way) {
                return hasTag(way, "wetland", "marsh");
            }
        },
        QuarryOrPit(32) {
            @Override
            public boolean isValidForWay(Way way) {
                return hasTag(way, "natural", "excavation");
            }
        },
        Scree(64) {
            @Override
            public boolean isValidForWay(Way way) {
                return hasTag(way, "natural", "scree");
            }
        },
        Rock(128) {
            @Override
            public boolean isValidForWay(Way way) {
                return hasTag(way, "natural", "rock");
            }
        },
        Mud(256) {
            @Override
            public boolean isValidForWay(Way way) {
                return hasTag(way, "natural", "mud");
            }
        },
        Sand(512) {
            @Override
            public boolean isValidForWay(Way way) {
                return hasTag(way, "natural", "sand");
            }
        },

        Shingle(1024) {
            @Override
            public boolean isValidForWay(Way way) {
                return hasTag(way, "natural", "shingle");
            }
        },
        Spoil(2048) {
            @Override
            public boolean isValidForWay(Way way) {
                return hasTag(way, "natural", "spoil");
            }
        },

        TidalWater(4096) {
            @Override
            public boolean isValidForWay(Way way) {
                return hasTag(way, "natural", "water") && hasTag(way, "tidal", "yes");
            }
        };

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }

        private static boolean hasTag(Way way, String key, String value) {
            return OsFlagUtils.hasTag(way, key, value);
        }

        private final long value;

        private AvoidanceType(long value) {
            this.value = value;
        }

        @Override
        public long getValue() {
            return value;
        }

        @Override
        public boolean isValidForWay(Way way) {
            return false;
        }

        @Override
        public boolean representedIn(String[] attributes) {
            for (String attribute : attributes) {
                if (attribute.equals(this.toString())) {
                    return true;
                }
            }
            return false;
        }

    }

    @Override
    protected void defineEncoder(int shift) {
        wayTypeEncoder = new EncodedValue("HazardType", shift, 14, 1, 0, 8191, true);
    }

    @Override
    protected EdgeAttribute[] getEdgeAttributesOfInterest() {
        return AvoidanceType.values();
    }

}
