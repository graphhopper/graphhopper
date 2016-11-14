package com.graphhopper.reader.gtfs;

import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.util.FlagEncoderManagerProtocol;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.InstructionAnnotation;
import com.graphhopper.util.Translation;

class PtFlagEncoder extends FlagEncoderManagerProtocol {

    private boolean registered;

    PtFlagEncoder() {
	}

	public String toString() {
		return "pt";
	}

    @Override
    public boolean isTurnRestricted(long flags) {
        return false;
    }

    @Override
    public double getTurnCost(long flags) {
        return 0;
    }

    @Override
    public long getTurnFlags(boolean restricted, double costs) {
        return 0;
    }

    @Override
    public int getVersion() {
        return 0;
    }

    @Override
    public double getMaxSpeed() {
        return 0;
    }

    @Override
    public double getSpeed(long flags) {
        return 5.0;
    }

    @Override
    public long setSpeed(long flags, double speed) {
        return 0;
    }

    @Override
    public double getReverseSpeed(long flags) {
        return 0;
    }

    @Override
    public long setReverseSpeed(long flags, double speed) {
        return 0;
    }

    @Override
    public long setAccess(long flags, boolean forward, boolean backward) {
        return 0;
    }

    @Override
    public long setProperties(double speed, boolean forward, boolean backward) {
        return 0;
    }

    @Override
    public boolean isForward(long flags) {
        if (flags == 0) {
            return true;
        } else if (flags == 1) {
            return false;
        }
        throw new IllegalStateException();
    }

    @Override
    public boolean isBackward(long flags) {
        return false;
    }

    @Override
    public boolean isBool(long flags, int key) {
        if (flags == 1) {
            return true;
        } else if (flags == 0) {
            return false;
        }
        throw new IllegalStateException();
    }

    @Override
    public long setBool(long flags, int key, boolean value) {
        return 0;
    }

    @Override
    public long getLong(long flags, int key) {
        return 0;
    }

    @Override
    public long setLong(long flags, int key, long value) {
        return 0;
    }

    @Override
    public double getDouble(long flags, int key) {
        return 0;
    }

    @Override
    public long setDouble(long flags, int key, double value) {
        return 0;
    }

    @Override
    public boolean supports(Class<?> feature) {
        return false;
    }

    @Override
    public InstructionAnnotation getAnnotation(long flags, Translation tr) {
        return null;
    }

    @Override
    public boolean isRegistered() {
        return registered;
    }

    @Override
    public void setRegistered(boolean b) {
        registered = b;
    }

    @Override
    public int defineNodeBits(int encoderCount, int nextNodeBit) {
        return 0;
    }

    @Override
    public void setNodeBitMask(int i, int nextNodeBit) {

    }

    @Override
    public int defineWayBits(int encoderCount, int nextWayBit) {
        return 0;
    }

    @Override
    public void setWayBitMask(int i, int nextWayBit) {

    }

    @Override
    public int defineRelationBits(int encoderCount, int nextRelBit) {
        return 0;
    }

    @Override
    public void setRelBitMask(int i, int nextRelBit) {

    }

    @Override
    public int defineTurnBits(int encoderCount, int nextTurnBit) {
        return 0;
    }

    @Override
    public long acceptWay(ReaderWay way) {
        return 0;
    }

    @Override
    public long handleRelationTags(ReaderRelation relation, long oldRelationFlags) {
        return 0;
    }

    @Override
    public long getRelBitMask() {
        return 0;
    }

    @Override
    public long handleWayTags(ReaderWay way, long includeWay, long l) {
        return 0;
    }

    @Override
    public char[] getPropertiesString() {
        return new char[0];
    }

    @Override
    public long flagsDefault(boolean forward, boolean backward) {
        return 0;
    }

    @Override
    public long reverseFlags(long flags) {
        return 1-flags;
    }

    @Override
    public long handleNodeTags(ReaderNode node) {
        return 0;
    }

    @Override
    public void applyWayTags(ReaderWay way, EdgeIteratorState edge) {

    }
}
