package com.graphhopper.navigation;

import static com.graphhopper.navigation.DistanceUtils.meterToKilometer;
import static com.graphhopper.navigation.DistanceUtils.meterToMiles;

class VoiceInstructionConfig {
    public final String key; // TranslationMap key

    public VoiceInstructionConfig(String key) {
        this.key = key;
    }
}

class DecisionVoiceInstructionConfig extends VoiceInstructionConfig {
    public final int[] distanceAlongGeometry; // distances in meter in which the instruction should be spoken
    public final int[] distanceVoiceValue; // distances in required unit. f.e: 1km, 300m or 2mi

    public DecisionVoiceInstructionConfig(String key, int[] distanceAlongGeometry, int[] distanceVoiceValue) {
        super(key);
        this.distanceAlongGeometry = distanceAlongGeometry;
        this.distanceVoiceValue = distanceVoiceValue;
        if (distanceAlongGeometry.length != distanceVoiceValue.length) {
            throw new IllegalArgumentException("distanceAlongGeometry and distanceVoiceValue are not same size");
        }
    }

    public int getFittingInstructionIndex(double distanceMeter) {
        for (int i = 0; i < distanceAlongGeometry.length; i++) {
            if (distanceMeter >= distanceAlongGeometry[i]) {
                return i;
            }
        }
        return -1;
    }
}

class FixedVoiceInstructionConfig extends VoiceInstructionConfig {
    public final int distanceAlongGeometry; // distance in meter in which the instruction should be spoken
    public final int distanceVoiceValue; // distance in required unit. f.e: 1km, 300m or 2mi

    public FixedVoiceInstructionConfig(String key, int distanceAlongGeometry, int distanceVoiceValue) {
        super(key);
        this.distanceAlongGeometry = distanceAlongGeometry;
        this.distanceVoiceValue = distanceVoiceValue;
    }
}

// The instruction should not be spoken straight away, but wait until the user merged on the new road and can listen to instructions again
class InitialVoiceInstructionConfig extends VoiceInstructionConfig {
    private final int distanceDelay; // delay distance in meter
    public final int distanceForInitialStayInstruction; // min distance in meter for initial instruction
    private final DistanceUtils.Unit unit;

    public InitialVoiceInstructionConfig(String key, int distanceForInitialStayInstruction, int distanceDelay, DistanceUtils.Unit unit) {
        super(key);
        this.distanceForInitialStayInstruction = distanceForInitialStayInstruction;
        this.distanceDelay = distanceDelay;
        this.unit = unit;
    }

    public int distanceAlongGeometry(double distanceMeter) {
        // Cast to full units
        int tmpDistance = (int) (distanceMeter - distanceDelay);
        if (unit == DistanceUtils.Unit.METRIC) {
            return (tmpDistance / 1000) * 1000;
        } else {
            tmpDistance = (int) (tmpDistance * meterToMiles);
            return (int) Math.ceil(tmpDistance / meterToMiles);
        }
    }

    public int distanceVoiceValue(double distanceInMeter) {
        if (unit == DistanceUtils.Unit.METRIC) {
            return (int) (distanceAlongGeometry(distanceInMeter) * meterToKilometer);
        } else {
            return (int) (distanceAlongGeometry(distanceInMeter) * meterToMiles);
        }
    }
}
