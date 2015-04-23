package com.graphhopper.routing.util;

import com.graphhopper.reader.Way;
import com.graphhopper.util.InstructionAnnotation;
import com.graphhopper.util.Translation;

/**
 * Created by sadam on 4/15/15.
 */
public class OsAvoidanceDecorator implements EncoderDecorator {
	private EncodedValue wayTypeEncoder;

	protected enum AvoidanceType {
		Boulders(1) {
			@Override
			public boolean isValidForWay(Way way) {
				return hasTag(way, "natural", "boulder");
			}
		},
		Cliff(2) {
			@Override
			public boolean isValidForWay(Way way) {
				return hasTag(way, "natural", "cliff");
			}
		},
		InlandWater(4) {
			@Override
			public boolean isValidForWay(Way way) {
				return hasTag(way, "natural", "water")
						&& way.hasTag("tidal", "no");
			}
		},
		Marsh(8) {
			@Override
			public boolean isValidForWay(Way way) {
				return way.hasTag("wetland", "marsh");
			}
		},
		QuarryOrPit(16) {
			 @Override
			 public boolean isValidForWay(Way way) {
				 return hasTag(way, "natural", "excavation");
			 }
		},
		Scree(32) {
			@Override
			public boolean isValidForWay(Way way) {
				return hasTag(way, "natural", "scree");
			}
		},
		Rock(64) {
			@Override
			public boolean isValidForWay(Way way) {
				return hasTag(way, "natural", "rock");
			}
		}
		,
		Mud(128) {
			@Override
			public boolean isValidForWay(Way way) {
				return hasTag(way, "natural", "mud");
			}
		},
		Sand(256) {
			@Override
			public boolean isValidForWay(Way way) {
				return hasTag(way, "natural", "sand");
			}
		},
		
		Shingle(512) {
			@Override
			public boolean isValidForWay(Way way) {
				return hasTag(way, "natural", "shingle");
			}
		}
//		,
//		Spoil(1024) {
//			@Override
//			public boolean isValidForWay(Way way) {
//				return hasTag(way, "natural", "spoil");
//			}
//		},
//		
//		TidalWater(2048) {
//			@Override
//			public boolean isValidForWay(Way way) {
//				return hasTag(way, "natural", "water")
//						&& way.hasTag("tidal", "yes");
//			}
//		}
	;

		private static boolean hasTag(Way way, String key, String value) {
			String wayTag = way.getTag(key);
			if (null != wayTag) {
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

		private AvoidanceType(long value) {
			this.value = value;
		}

		public long getValue() {
			return value;
		}

		public boolean isValidForWay(Way way) {
			return false;
		}

	}

	public int defineWayBits(int shift) {
		wayTypeEncoder = new EncodedValue("WayType", shift, 11, 1, 0, 1024,
				true);
		shift += wayTypeEncoder.getBits();
		return shift;
	}

	public long handleWayTags(Way way, long encoded) {
		long avoidanceValue = 0;

		for (AvoidanceType aType : AvoidanceType.values()) {
			if (aType.isValidForWay(way)) {
				avoidanceValue += aType.getValue();
			}
		}
		return wayTypeEncoder.setValue(encoded, avoidanceValue);
	}

	public InstructionAnnotation getAnnotation(long flags, Translation tr) {
		long wayType = wayTypeEncoder.getValue(flags);
		String wayName = getWayName(wayType, tr);
		return new InstructionAnnotation(1, wayName);
	}

	private String getWayName(long wayType, Translation tr) {
		String wayName = "";
		for (AvoidanceType aType : AvoidanceType.values()) {
			if ((wayType & aType.getValue()) == aType.getValue()) {
				wayName += " ";
				wayName += aType.name();
			}
		}

		return wayName;
	}

}
