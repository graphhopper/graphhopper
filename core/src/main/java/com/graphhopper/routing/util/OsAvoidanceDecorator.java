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
				return hasTag(way, "natural", "boulder");
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
				return hasTag(way, "natural", "water")
						&& way.hasTag("tidal", "no");
			}
		},
		Marsh(16) {
			@Override
			public boolean isValidForWay(Way way) {
				return way.hasTag("wetland", "marsh");
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
		}
		// ,
		// Spoil(1024) {
		// @Override
		// public boolean isValidForWay(Way way) {
		// return hasTag(way, "natural", "spoil");
		// }
		// },
		//
		// TidalWater(2048) {
		// @Override
		// public boolean isValidForWay(Way way) {
		// return hasTag(way, "natural", "water")
		// && way.hasTag("tidal", "yes");
		// }
		// }
		;

		public String toString() {
			return super.toString().toLowerCase();
		}

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

		public boolean representedIn(String[] attributes) {
			for (String attribute : attributes) {
				if (attribute.equals(this.toString())) {
					return true;
				}
			}
			return false;
		}

	}

	protected void defineEncoder(int shift) {
		wayTypeEncoder = new EncodedValue("HazardType", shift, 12, 1, 0, 2048,
				true);
	}

	protected EdgeAttribute[] getEdgeAttributesOfInterest() {
		return AvoidanceType.values();
	}

}
