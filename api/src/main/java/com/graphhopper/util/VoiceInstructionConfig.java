package com.graphhopper.util;

import static com.graphhopper.util.VoiceInstructionDistanceUtils.meterToKilometer;
import static com.graphhopper.util.VoiceInstructionDistanceUtils.meterToMiles;

public abstract class VoiceInstructionConfig {
    protected final String translationKey;
    protected final Translation translation;

    public VoiceInstructionConfig(String translationKey, Translation translation) {
        this.translationKey = translationKey;
        this.translation = translation;
    }

    public static class VoiceInstructionValue {
        public final int spokenDistance;
        public final String turnDescription;

        public VoiceInstructionValue(int spokenDistance, String turnDescription) {
            this.spokenDistance = spokenDistance;
            this.turnDescription = turnDescription;
        }
    }

    public abstract VoiceInstructionValue getConfigForDistance(
            double distance,
            String turnDescription,
            String thenVoiceInstruction);
}

class ConditionalDistanceVoiceInstructionConfig extends VoiceInstructionConfig {
    private final int[] distanceAlongGeometry; // distances in meter in which the instruction should be spoken
    private final int[] distanceVoiceValue; // distances in required unit. f.e: 1km, 300m or 2mi

    public ConditionalDistanceVoiceInstructionConfig(String key, Translation translation, int[] distanceAlongGeometry,
                                                     int[] distanceVoiceValue) {
        super(key, translation);
        this.distanceAlongGeometry = distanceAlongGeometry;
        this.distanceVoiceValue = distanceVoiceValue;
        if (distanceAlongGeometry.length != distanceVoiceValue.length) {
            throw new IllegalArgumentException("distanceAlongGeometry and distanceVoiceValue are not same size");
        }
    }

    private int getFittingInstructionIndex(double distanceMeter) {
        for (int i = 0; i < distanceAlongGeometry.length; i++) {
            if (distanceMeter >= distanceAlongGeometry[i]) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public VoiceInstructionValue getConfigForDistance(double distance, String turnDescription, String thenVoiceInstruction) {
        int instructionIndex = getFittingInstructionIndex(distance);
        if (instructionIndex < 0) {
            return null;
        }
        String totalDescription = translation.tr("navigate." + translationKey, distanceVoiceValue[instructionIndex]) + " " + turnDescription + thenVoiceInstruction;
        int spokenDistance = distanceAlongGeometry[instructionIndex];
        return new VoiceInstructionValue(spokenDistance, totalDescription);
    }
}

class FixedDistanceVoiceInstructionConfig extends VoiceInstructionConfig {
    private final int distanceAlongGeometry; // distance in meter in which the instruction should be spoken
    private final int distanceVoiceValue; // distance in required unit. f.e: 1km, 300m or 2mi

    public FixedDistanceVoiceInstructionConfig(String key, Translation translation, int distanceAlongGeometry, int distanceVoiceValue) {
        super(key, translation);
        this.distanceAlongGeometry = distanceAlongGeometry;
        this.distanceVoiceValue = distanceVoiceValue;
    }

    @Override
    public VoiceInstructionValue getConfigForDistance(double distance, String turnDescription, String thenVoiceInstruction) {
        if (distance >= distanceAlongGeometry) {
            String totalDescription = translation.tr("navigate." + translationKey, distanceVoiceValue) + " " + turnDescription;
            return new VoiceInstructionValue(distanceAlongGeometry, totalDescription);
        }
        return null;
    }
}


class InitialVoiceInstructionConfig extends VoiceInstructionConfig {
    // The instruction should not be spoken straight away, but wait until the user merged on the new road and can listen to instructions again
    private final int distanceDelay; // delay distance in meter
    private final int distanceForInitialStayInstruction; // min distance in meter for initial instruction
    private final VoiceInstructionDistanceUtils.Unit unit;

    public InitialVoiceInstructionConfig(String key, Translation translation, int distanceForInitialStayInstruction, int distanceDelay, VoiceInstructionDistanceUtils.Unit unit) {
        super(key, translation);
        this.distanceForInitialStayInstruction = distanceForInitialStayInstruction;
        this.distanceDelay = distanceDelay;
        this.unit = unit;
    }

    private int distanceAlongGeometry(double distanceMeter) {
        // Cast to full units
        int tmpDistance = (int) (distanceMeter - distanceDelay);
        if (unit == VoiceInstructionDistanceUtils.Unit.METRIC) {
            return (tmpDistance / 1000) * 1000;
        } else {
            tmpDistance = (int) (tmpDistance * meterToMiles);
            return (int) Math.ceil(tmpDistance / meterToMiles);
        }
    }

    private int distanceVoiceValue(double distanceInMeter) {
        if (unit == VoiceInstructionDistanceUtils.Unit.METRIC) {
            return (int) (distanceAlongGeometry(distanceInMeter) * meterToKilometer);
        } else {
            return (int) (distanceAlongGeometry(distanceInMeter) * meterToMiles);
        }
    }

    @Override
    public VoiceInstructionValue getConfigForDistance(double distance, String turnDescription, String thenVoiceInstruction) {
        if (distance > distanceForInitialStayInstruction) {
            int spokenDistance = distanceAlongGeometry(distance);
            int distanceVoiceValue = distanceVoiceValue(distance);
            String continueDescription = translation.tr("continue") + " " +
                    translation.tr("navigate." + translationKey, distanceVoiceValue);
            continueDescription = Helper.firstBig(continueDescription);
            return new VoiceInstructionValue(spokenDistance, continueDescription);
        }
        return null;
    }
}
