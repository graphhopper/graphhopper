package com.graphhopper.routing.util;

import com.graphhopper.reader.Way;
import com.graphhopper.util.InstructionAnnotation;
import com.graphhopper.util.Translation;

/**
 * Created by sadam on 4/15/15.
 */
public class OsAvoidanceDecorator {
    private EncodedValue wayTypeEncoder;


    protected enum AvoidanceType
    {
        MOTORWAYS(1) {
            @Override
            public boolean isValidForWay(Way way) {
               return way.hasTag("highway", "Motorway", "motorway");
            }
        },
        TOLL(2) {
            @Override
            public boolean isValidForWay(Way way) {
                return way.hasTag("toll", "yes");
            }
        },
        Boulders(4) {
            @Override
            public boolean isValidForWay(Way way) {
                return hasTag(way, "natural", "boulder");
            }
        },
        Cliff(8) {
            @Override
            public boolean isValidForWay(Way way) {
                return hasTag(way, "natural","cliff");
            }
        },
        Marsh(16) {
            @Override
            public boolean isValidForWay(Way way) {
                return way.hasTag("wetland", "marsh");
            }
        },
        Mud(32) {
            @Override
            public boolean isValidForWay(Way way) {
                return hasTag(way, "natural", "mud");
            }
        },
        Sand(64) {
            @Override
            public boolean isValidForWay(Way way) {
                return hasTag(way, "natural", "sand");
            }
        },
        Scree(128) {
            @Override
            public boolean isValidForWay(Way way) {
                return hasTag(way, "natural", "scree");
            }
        },
        Shingle(256) {
            @Override
            public boolean isValidForWay(Way way) {
                return hasTag(way, "natural", "shingle");
            }
        },
        Spoil(512) {
            @Override
            public boolean isValidForWay(Way way) {
                return hasTag(way, "natural", "spoil");
            }
        },
        Rock(1024) {
            @Override
            public boolean isValidForWay(Way way) {
                return hasTag(way, "natural", "rock");
            }
        },
        TidalWater(2048) {
            @Override
            public boolean isValidForWay(Way way) {
                return hasTag(way, "natural", "water")  && way.hasTag("tidal", "yes");
            }
        },
        InlandWater(4096) {
            @Override
            public boolean isValidForWay(Way way) {
                return hasTag(way, "natural", "water")  && way.hasTag("tidal", "no");
            }
        },
        QuarryOrPit(8192) {
            @Override
            public boolean isValidForWay(Way way) {
                return hasTag(way, "natural", "excavation");
            }
        };

        private static boolean hasTag(Way way, String key, String value) {
            String wayTag = way.getTag(key);
            if(null!=wayTag) {
                String[] values = wayTag.split(",");
                for (String tvalue : values) {
                    if (tvalue.equals(value)) {
                        return true;
                    }
                }
            }
            return false;
        }


        private final long value;

        private AvoidanceType( long value )
        {
            this.value = value;
        }

        public long getValue()
        {
            return value;
        }

        public boolean isValidForWay(Way way) {
            return false;
        }



    }

    public int defineWayBits(int shift) {
        wayTypeEncoder = new EncodedValue("WayType", shift, 14, 1, 0, 16383, true);
        shift += wayTypeEncoder.getBits();
        return shift;
    }

    public long handleWayTags(Way way, long encoded) {
        long avoidanceValue=0;

        for (AvoidanceType aType: AvoidanceType.values()) {
            if(aType.isValidForWay(way)) {
                avoidanceValue += aType.getValue();
            }
        }
        return wayTypeEncoder.setValue(encoded, avoidanceValue);
    }

    public InstructionAnnotation getAnnotation( long flags, Translation tr )
    {
        long wayType = wayTypeEncoder.getValue(flags);
        String wayName = getWayName(wayType, tr);
        return new InstructionAnnotation(0, wayName);
    }

    private String getWayName(long wayType, Translation tr) {
        String wayName="";
        for (AvoidanceType aType: AvoidanceType.values()) {
            if ((wayType & aType.getValue()) == aType.getValue()) {
                wayName += " ";
                wayName += aType.name();
            }
        }

        return wayName;
    }

}
