/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper licenses this file to you under the Apache License,
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
package com.graphhopper.routing.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.graphhopper.reader.DataReader;
import com.graphhopper.reader.ITurnCostTableEntry;
import com.graphhopper.reader.Node;
import com.graphhopper.reader.Relation;
import com.graphhopper.reader.RoutingElement;
import com.graphhopper.reader.TurnRelation;
import com.graphhopper.reader.Way;
import com.graphhopper.util.BitUtil;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;
import com.graphhopper.util.InstructionAnnotation;
import com.graphhopper.util.Translation;

/**
 * Abstract class which handles flag decoding and encoding. Every encoder should
 * be registered to a EncodingManager to be usable. If you want the full long to
 * be stored you need to enable this in the GraphHopperStorage.
 * <p/>
 * 
 * @author Peter Karich
 * @author Nop
 * @see EncodingManager
 */
public abstract class AbstractFlagEncoder implements FlagEncoder,
		TurnCostEncoder {
	private final static Logger logger = LoggerFactory
			.getLogger(AbstractFlagEncoder.class);
	private final static int K_FORWARD = 0, K_BACKWARD = 1;
	/* Edge Flag Encoder fields */
	private long nodeBitMask;
	private long wayBitMask;
	private long relBitMask;
	protected long forwardBit;
	protected long backwardBit;
	protected long directionBitMask;
	protected long roundaboutBit;
	protected EncodedDoubleValue speedEncoder;
	// bit to signal that way is accepted
	protected long acceptBit;
	protected long ferryBit;
	
	// This value determines the maximal possible speed of any road regardless the maxspeed value
	// lower values allow more compact representation of the routing graph
	protected int maxPossibleSpeed;

	private EncodedValue turnCostEncoder;
	private long turnRestrictionBit;
	private final int maxTurnCosts;

	/* processing properties (to be initialized lazy when needed) */
	protected EdgeExplorer edgeOutExplorer;
	protected EdgeExplorer edgeInExplorer;

	/* restriction definitions where order is important */
	protected List<String> restrictions = new ArrayList<String>(5);
	protected final HashSet<String> intendedValues = new HashSet<String>(5);
	protected final HashSet<String> excludedValues = new HashSet<String>(5);
	protected final HashSet<String> restrictedValues = new HashSet<String>(5);
	protected final HashSet<String> ferries = new HashSet<String>(5);
	protected final HashSet<String> oneways = new HashSet<String>(5);
	protected final HashSet<String> acceptedRailways = new HashSet<String>(5);
	// http://wiki.openstreetmap.org/wiki/Mapfeatures#Barrier
	protected final HashSet<String> absoluteBarriers = new HashSet<String>(5);
	protected final HashSet<String> potentialBarriers = new HashSet<String>(5);
	/**
	 * Used to hold type exclusions
	 */
	protected final List<String> vehicleQualifierTypeExclusions = new ArrayList<String>(
			5);
	protected final List<String> vehicleQualifierTypeInclusions = new ArrayList<String>(
			5);
	private boolean blockByDefault = true;
	private boolean blockFords = true;
	protected final int speedBits;
	protected final double speedFactor;
	protected List<EncoderDecorator> encoderDecorators = null;

	/**
	 * @param speedBits
	 *            specify the number of bits used for speed
	 * @param speedFactor
	 *            specify the factor to multiple the stored value (can be used
	 *            to increase or decrease accuracy of speed value)
	 * @param maxTurnCosts
	 *            specify the maximum value used for turn costs, if this value
	 *            is reached a turn is forbidden and results in costs of
	 *            positive infinity.
	 */
	protected AbstractFlagEncoder(int speedBits, double speedFactor,
			int maxTurnCosts) {
		this.maxTurnCosts = maxTurnCosts <= 0 ? 0 : maxTurnCosts;
		this.speedBits = speedBits;
		this.speedFactor = speedFactor;
		oneways.add("yes");
		oneways.add("true");
		oneways.add("1");
		oneways.add("-1");

		ferries.add("shuttle_train");
		ferries.add("ferry");

		acceptedRailways.add("tram");
		acceptedRailways.add("abandoned");
		acceptedRailways.add("disused");

		// http://wiki.openstreetmap.org/wiki/Demolished_Railway
		acceptedRailways.add("dismantled");
		acceptedRailways.add("razed");
		acceptedRailways.add("historic");
		acceptedRailways.add("obliterated");

		intendedValues.add("true");
		intendedValues.add("yes");

		excludedValues.add("false");
		excludedValues.add("no");

	}

	/**
	 * Should potential barriers block when no access limits are given?
	 */
	public void setBlockByDefault(boolean blockByDefault) {
		this.blockByDefault = blockByDefault;
	}

	public void setBlockFords(boolean blockFords) {
		this.blockFords = blockFords;
	}

	public boolean isBlockFords() {
		return blockFords;
	}

	/**
	 * Defines the bits for the node flags, which are currently used for
	 * barriers only.
	 * <p>
	 * 
	 * @return incremented shift value pointing behind the last used bit
	 */
	public int defineNodeBits(int index, int shift) {
		return shift;
	}

	/**
	 * Defines bits used for edge flags used for access, speed etc.
	 * <p/>
	 * 
	 * @param index
	 * @param shift
	 *            bit offset for the first bit used by this encoder
	 * @return incremented shift value pointing behind the last used bit
	 */
	public int defineWayBits(int index, int shift) {
		if (forwardBit != 0)
			throw new IllegalStateException(
					"You must not register a FlagEncoder (" + toString()
							+ ") twice!");

		// define the first 2 speedBits in flags for routing
		forwardBit = 1L << shift;
		backwardBit = 2L << shift;
		directionBitMask = 3L << shift;
		shift += 2;
		roundaboutBit = 1L << shift;
		shift++;

		// define internal flags for parsing
		index *= 2;
		acceptBit = 1L << index;
		ferryBit = 2L << index;

		if (null != encoderDecorators) {
			for (EncoderDecorator decorator : encoderDecorators) {
				shift = decorator.defineWayBits(shift);
			}
		}
		return shift;
	}

	/**
	 * Defines the bits which are used for relation flags.
	 * <p>
	 * 
	 * @return incremented shift value pointing behind the last used bit
	 */
	public int defineRelationBits(int index, int shift) {
		return shift;
	}

	/**
	 * Analyze the properties of a relation and create the routing flags for the
	 * second read step. In the pre-parsing step this method will be called to
	 * determine the useful relation tags.
	 * <p/>
	 */
	public abstract long handleRelationTags(Relation relation,
			long oldRelationFlags);

	/**
	 * Decide whether a way is routable for a given mode of travel. This skips
	 * some ways before handleWayTags is called.
	 * <p/>
	 * 
	 * @return the encoded value to indicate if this encoder allows travel or
	 *         not.
	 */
	public abstract long acceptWay(Way way);

	/**
	 * Analyze properties of a way and create the routing flags. This method is
	 * called in the second parsing step.
	 */
	public long handleWayTags(Way way, long allowed, long relationFlags) {
		if (null != encoderDecorators) {
			for (EncoderDecorator decorator : encoderDecorators) {
				relationFlags = decorator.handleWayTags(way, relationFlags);
			}
		}
		return relationFlags;
	};

	/**
	 * Parse tags on nodes. Node tags can add to speed (like traffic_signals)
	 * where the value is strict negative or blocks access (like a barrier),
	 * then the value is strict positive.This method is called in the second
	 * parsing step.
	 */
	public long handleNodeTags(Node node) {
		// absolute barriers always block
		if (node.hasTag("barrier", absoluteBarriers))
			return directionBitMask;

		// movable barriers block if they are not marked as passable
		if (node.hasTag("barrier", potentialBarriers)) {
			boolean locked = false;
			if (node.hasTag("locked", "yes"))
				locked = true;

			for (String res : restrictions) {
				if (!locked && node.hasTag(res, intendedValues))
					return 0;

				if (node.hasTag(res, restrictedValues))
					return directionBitMask;
			}

			if (blockByDefault)
				return directionBitMask;
		}

		if (blockFords
				&& (node.hasTag("highway", "ford") || node.hasTag("ford"))
				&& !node.hasTag(restrictions, intendedValues))
			return directionBitMask;

		return 0;
	}

	@Override
	public InstructionAnnotation getAnnotation(long flags, Translation tr) {
		if (null != encoderDecorators) {
			for (EncoderDecorator decorator : encoderDecorators) {
				InstructionAnnotation anno = decorator.getAnnotation(flags, tr);
				if (!anno.isEmpty()) {
					return anno;
				}
			}
		}
		return InstructionAnnotation.EMPTY;
	}

	/**
	 * Swapping directions means swapping bits which are dependent on the
	 * direction of an edge like the access bits. But also direction dependent
	 * speed values should be swapped too. Keep in mind that this method is
	 * performance critical!
	 */
	public long reverseFlags(long flags) {
		long dir = flags & directionBitMask;
		if (dir == directionBitMask || dir == 0)
			return flags;

		return flags ^ directionBitMask;
	}

	/**
	 * Sets default flags with specified access.
	 */
	public long flagsDefault(boolean forward, boolean backward) {
		long flags = speedEncoder.setDefaultValue(0);
		return setAccess(flags, forward, backward);
	}

	@Override
	public long setAccess(long flags, boolean forward, boolean backward) {
		return setBool(setBool(flags, K_BACKWARD, backward), K_FORWARD, forward);
	}

	@Override
	public long setSpeed(long flags, double speed) {
		if (speed < 0)
			throw new IllegalArgumentException("Speed cannot be negative: "
					+ speed + ", flags:" + BitUtil.LITTLE.toBitString(flags));

		if (speed > getMaxSpeed())
			speed = getMaxSpeed();
		return speedEncoder.setDoubleValue(flags, speed);
	}

	@Override
	public double getSpeed(long flags) {
		double speedVal = speedEncoder.getDoubleValue(flags);
		if (speedVal < 0)
			throw new IllegalStateException("Speed was negative!? " + speedVal);

		return speedVal;
	}

	@Override
	public long setReverseSpeed(long flags, double speed) {
		return setSpeed(flags, speed);
	}

	@Override
	public double getReverseSpeed(long flags) {
		return getSpeed(flags);
	}

	@Override
	public long setProperties(double speed, boolean forward, boolean backward) {
		return setAccess(setSpeed(0, speed), forward, backward);
	}

	@Override
	public double getMaxSpeed() {
		return speedEncoder.getMaxValue();
	}

	/**
	 * @return -1 if no maxspeed found
	 */
	protected double getMaxSpeed(Way way) {
		double maxSpeed = parseSpeed(way.getTag("maxspeed"));
		double fwdSpeed = parseSpeed(way.getTag("maxspeed:forward"));
		if (fwdSpeed >= 0 && (maxSpeed < 0 || fwdSpeed < maxSpeed))
			maxSpeed = fwdSpeed;

		double backSpeed = parseSpeed(way.getTag("maxspeed:backward"));
		if (backSpeed >= 0 && (maxSpeed < 0 || backSpeed < maxSpeed))
			maxSpeed = backSpeed;

		return maxSpeed;
	}

	@Override
	public int hashCode() {
		int hash = 7;
		hash = 61 * hash + (int) this.directionBitMask;
		hash = 61 * hash + this.toString().hashCode();
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null)
			return false;

		// only rely on the string
		// if (getClass() != obj.getClass())
		// return false;
		final AbstractFlagEncoder other = (AbstractFlagEncoder) obj;
		if (this.directionBitMask != other.directionBitMask)
			return false;

		return this.toString().equals(other.toString());
	}

	/**
	 * @return the speed in km/h
	 */
	protected static double parseSpeed(String str) {
		if (Helper.isEmpty(str))
			return -1;

		try {
			int val;
			// see https://en.wikipedia.org/wiki/Knot_%28unit%29#Definitions
			int mpInteger = str.indexOf("mp");
			if (mpInteger > 0) {
				str = str.substring(0, mpInteger).trim();
				val = Integer.parseInt(str);
				return val * DistanceCalcEarth.KM_MILE;
			}

			int knotInteger = str.indexOf("knots");
			if (knotInteger > 0) {
				str = str.substring(0, knotInteger).trim();
				val = Integer.parseInt(str);
				return val * 1.852;
			}

			int kmInteger = str.indexOf("km");
			if (kmInteger > 0) {
				str = str.substring(0, kmInteger).trim();
			} else {
				kmInteger = str.indexOf("kph");
				if (kmInteger > 0) {
					str = str.substring(0, kmInteger).trim();
				}
			}

			return Integer.parseInt(str);
		} catch (Exception ex) {
			return -1;
		}
	}

	/**
	 * This method parses a string ala "00:00" (hours and minutes) or "0:00:00"
	 * (days, hours and minutes).
	 * <p/>
	 * 
	 * @return duration value in minutes
	 */
	protected static int parseDuration(String str) {
		if (str == null)
			return 0;

		try {
			// for now ignore this special duration notation
			// because P1M != PT1M but there are wrong edits in OSM! e.g.
			// http://www.openstreetmap.org/way/24791405
			// http://wiki.openstreetmap.org/wiki/Key:duration
			if (str.startsWith("P"))
				return 0;

			int index = str.indexOf(":");
			if (index > 0) {
				String hourStr = str.substring(0, index);
				String minStr = str.substring(index + 1);
				index = minStr.indexOf(":");
				int minutes = 0;
				if (index > 0) {
					// string contains hours too
					String dayStr = hourStr;
					hourStr = minStr.substring(0, index);
					minStr = minStr.substring(index + 1);
					minutes = Integer.parseInt(dayStr) * 60 * 24;
				}

				minutes += Integer.parseInt(hourStr) * 60;
				minutes += Integer.parseInt(minStr);
				return minutes;
			} else {
				return Integer.parseInt(str);
			}
		} catch (Exception ex) {
			logger.warn("Cannot parse " + str + " using 0 minutes");
		}
		return 0;
	}

	/**
	 * Second parsing step. Invoked after splitting the edges. Currently used to
	 * offer a hook to calculate precise speed values based on elevation data
	 * stored in the specified edge.
	 */
	public void applyWayTags(Way way, EdgeIteratorState edge) {
		if (null != encoderDecorators) {
			for (EncoderDecorator decorator : encoderDecorators) {

			}
		}
	}

	/**
	 * Special handling for ferry ways.
	 */
	protected long handleFerryTags(Way way, double unknownSpeed,
			double shortTripsSpeed, double longTripsSpeed) {
		// to hours
		double durationInHours = parseDuration(way.getTag("duration")) / 60d;
		if (durationInHours > 0)
			try {
				Number estimatedLength = way.getTag("estimated_distance", null);
				if (estimatedLength != null) {
					// to km
					double val = estimatedLength.doubleValue() / 1000;
					// If duration AND distance is available we can calculate
					// the speed more precisely
					// and set both speed to the same value. Factor 1.4 slower
					// because of waiting time!
					shortTripsSpeed = Math.round(val / durationInHours / 1.4);
					if (shortTripsSpeed > getMaxSpeed())
						shortTripsSpeed = getMaxSpeed();
					longTripsSpeed = shortTripsSpeed;
				}
			} catch (Exception ex) {
			}

		if (durationInHours == 0) {
			// unknown speed -> put penalty on ferry transport
			return setSpeed(0, unknownSpeed);
		} else if (durationInHours > 1) {
			// lengthy ferries should be faster than short trip ferry
			return setSpeed(0, longTripsSpeed);
		} else {
			return setSpeed(0, shortTripsSpeed);
		}
	}

	void setWayBitMask(int usedBits, int shift) {
		wayBitMask = (1L << usedBits) - 1;
		wayBitMask <<= shift;
	}

	long getWayBitMask() {
		return wayBitMask;
	}

	void setRelBitMask(int usedBits, int shift) {
		relBitMask = (1L << usedBits) - 1;
		relBitMask <<= shift;
	}

	long getRelBitMask() {
		return relBitMask;
	}

	void setNodeBitMask(int usedBits, int shift) {
		nodeBitMask = (1L << usedBits) - 1;
		nodeBitMask <<= shift;
	}

	long getNodeBitMask() {
		return nodeBitMask;
	}

	/**
	 * Defines the bits reserved for storing turn restriction and turn cost
	 * <p>
	 * 
	 * @param shift
	 *            bit offset for the first bit used by this encoder
	 * @return incremented shift value pointing behind the last used bit
	 */
	public int defineTurnBits(int index, int shift) {
		if (maxTurnCosts == 0)
			return shift;

		// optimization for turn restrictions only
		else if (maxTurnCosts == 1) {
			turnRestrictionBit = 1L << shift;
			return shift + 1;
		}

		int turnBits = Helper.countBitValue(maxTurnCosts);
		turnCostEncoder = new EncodedValue("TurnCost", shift, turnBits, 1, 0,
				maxTurnCosts) {
			// override to avoid expensive Math.round
			@Override
			public final long getValue(long flags) {
				// find value
				flags &= mask;
				flags >>= shift;
				return flags;
			}
		};
		return shift + turnBits;
	}

	@Override
	public boolean isTurnRestricted(long flags) {
		if (maxTurnCosts == 0)
			return false;

		else if (maxTurnCosts == 1)
			return (flags & turnRestrictionBit) != 0;

		return turnCostEncoder.getValue(flags) == maxTurnCosts;
	}

	@Override
	public double getTurnCost(long flags) {
		if (maxTurnCosts == 0)
			return 0;

		else if (maxTurnCosts == 1)
			return ((flags & turnRestrictionBit) == 0) ? 0
					: Double.POSITIVE_INFINITY;

		long cost = turnCostEncoder.getValue(flags);
		if (cost == maxTurnCosts)
			return Double.POSITIVE_INFINITY;

		return cost;
	}

	@Override
	public long getTurnFlags(boolean restricted, double costs) {
		if (maxTurnCosts == 0)
			return 0;

		else if (maxTurnCosts == 1) {
			if (costs != 0)
				throw new IllegalArgumentException(
						"Only restrictions are supported");

			return restricted ? turnRestrictionBit : 0;
		}

		if (restricted) {
			if (costs != 0 || Double.isInfinite(costs))
				throw new IllegalArgumentException(
						"Restricted turn can only have infinite costs (or use 0)");
		} else {
			if (costs >= maxTurnCosts)
				throw new IllegalArgumentException(
						"Cost is too high. Or specifiy restricted == true");
		}

		if (costs < 0)
			throw new IllegalArgumentException("Turn costs cannot be negative");

		if (costs >= maxTurnCosts || restricted)
			costs = maxTurnCosts;
		return turnCostEncoder.setValue(0L, (int) costs);
	}

	public Collection<ITurnCostTableEntry> analyzeTurnRelation(
			TurnRelation turnRelation, DataReader osmReader) {
		if (!supports(TurnWeighting.class))
			return Collections.emptyList();

		if (edgeOutExplorer == null || edgeInExplorer == null) {
			edgeOutExplorer = osmReader.getGraphStorage().createEdgeExplorer(
					new DefaultEdgeFilter(this, false, true));
			edgeInExplorer = osmReader.getGraphStorage().createEdgeExplorer(
					new DefaultEdgeFilter(this, true, false));
		}
		return turnRelation.getRestrictionAsEntries(this, edgeOutExplorer,
				edgeInExplorer, osmReader);
	}

	protected boolean isFerry(long internalFlags) {
		return (internalFlags & ferryBit) != 0;
	}

	protected boolean isAccept(long internalFlags) {
		return (internalFlags & acceptBit) != 0;
	}

	@Override
	public boolean isBackward(long flags) {
		return (flags & backwardBit) != 0;
	}

	@Override
	public boolean isForward(long flags) {
		return (flags & forwardBit) != 0;
	}

	@Override
	public long setBool(long flags, int key, boolean value) {
		switch (key) {
		case K_FORWARD:
			return value ? flags | forwardBit : flags & ~forwardBit;
		case K_BACKWARD:
			return value ? flags | backwardBit : flags & ~backwardBit;
		case K_ROUNDABOUT:
			return value ? flags | roundaboutBit : flags & ~roundaboutBit;
		default:
			throw new IllegalArgumentException("Unknown key " + key
					+ " for boolean value");
		}
	}

	@Override
	public boolean isBool(long flags, int key) {
		switch (key) {

		case K_FORWARD:
			return isForward(flags);
		case K_BACKWARD:
			return isBackward(flags);
		case K_ROUNDABOUT:
			return (flags & roundaboutBit) != 0;
		default:
			throw new IllegalArgumentException("Unknown key " + key
					+ " for boolean value");
		}
	}

	@Override
	public long setLong(long flags, int key, long value) {
		throw new UnsupportedOperationException("Unknown key " + key
				+ " for long value.");
	}

	@Override
	public long getLong(long flags, int key) {
		for (EncoderDecorator decorator : encoderDecorators) {
			if (decorator.supports(key)) {
				return decorator.getLong(flags);
			}
		}
		throw new UnsupportedOperationException("Unknown key " + key
				+ " for long value.");
	}

	@Override
	public long setDouble(long flags, int key, double value) {
		throw new UnsupportedOperationException("Unknown key " + key
				+ " for double value.");
	}

	@Override
	public double getDouble(long flags, int key) {
		for (EncoderDecorator decorator : encoderDecorators) {
			if (decorator.supports(key)) {
				return decorator.getDouble(flags);
			}
		}
		throw new UnsupportedOperationException("Unknown key " + key
				+ " for double value.");
	}

	protected static double parseDouble(String str, String key, double defaultD) {
		String val = getStr(str, key);
		if (val.isEmpty())
			return defaultD;
		return Double.parseDouble(val);
	}

	protected static long parseLong(String str, String key, long defaultL) {
		String val = getStr(str, key);
		if (val.isEmpty())
			return defaultL;
		return Long.parseLong(val);
	}

	protected static boolean parseBoolean(String str, String key,
			boolean defaultB) {
		String val = getStr(str, key);
		if (val.isEmpty())
			return defaultB;
		return Boolean.parseBoolean(val);
	}

	protected static String getStr(String str, String key) {
		key = key.toLowerCase();
		for (String s : str.split("\\|")) {
			s = s.trim().toLowerCase();
			int index = s.indexOf("=");
			if (index < 0)
				continue;

			String field = s.substring(0, index);
			String valueStr = s.substring(index + 1);
			if (key.equals(field))
				return valueStr;
		}
		return "";
	}

	/**
	 * @param force
	 *            should be false if speed should be changed only if it is
	 *            bigger than maxspeed.
	 */
	protected double applyMaxSpeed(Way way, double speed, boolean force) {
		double maxSpeed = getMaxSpeed(way);
		// apply only if smaller maxSpeed
		if (maxSpeed >= 0) {
			if (force || maxSpeed < speed)
				return maxSpeed * 0.9;
		}
		return speed;
	}

	protected String getPropertiesString() {
		return "speedFactor=" + speedFactor + "|speedBits=" + speedBits
				+ "|turnCosts=" + (maxTurnCosts > 0);
	}

	@Override
	public boolean supports(Class<?> feature) {
		if (TurnWeighting.class.isAssignableFrom(feature))
			return maxTurnCosts > 0;

		return false;
	}

	/**
	 * The routingElement is specifically included in this exception. For
	 * example Motor Vehicles=true or Buses=true
	 * 
	 * @param routingElement
	 * @return
	 */
	public boolean isVehicleQualifierTypeIncluded(RoutingElement routingElement) {
		if (routingElement.hasTag(vehicleQualifierTypeInclusions,
				intendedValues)) {
			// It is specifically included
			return true;
		}
		return false;
	}

	/**
	 * The routingElement is specifically excluded in this exception. For
	 * example Motor Vehicles=false or Buses=false
	 * 
	 * @param routingElement
	 * @return
	 */
	public boolean isVehicleQualifierTypeExcluded(RoutingElement routingElement) {
		if (routingElement.hasTag(vehicleQualifierTypeExclusions,
				excludedValues)) {
			// It is specifically excluded
			return true;
		}
		return false;
	}

	@Override
	public long getBitMask(String[] attributes, int key) {
		long bitMask = 0;
		if (null != encoderDecorators) {// BIT SHIFT FIRST
			for (EncoderDecorator decorator : encoderDecorators) {
				if(decorator.supports(key)) {
					return decorator.getBitMask(attributes);
				}
			}
		}
		return bitMask;
	}
}
