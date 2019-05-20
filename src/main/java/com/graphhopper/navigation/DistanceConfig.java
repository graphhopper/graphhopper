package com.graphhopper.navigation;

import com.sun.javaws.exceptions.InvalidArgumentException;
import com.sun.tools.corba.se.idl.InvalidArgument;

import java.util.Arrays;
import java.util.List;

import static com.graphhopper.navigation.DistanceUtils.UnitTranslationKey.*;
import static com.graphhopper.navigation.DistanceUtils.meterToKilometer;
import static com.graphhopper.navigation.DistanceUtils.meterToMiles;

public class DistanceConfig {
    final DistanceUtils.Unit unit;

    final List<VoiceInstructionConfig> voiceInstructions;

    public DistanceConfig(DistanceUtils.Unit unit) {
        this.unit = unit;
        if (unit == DistanceUtils.Unit.METRIC) {
            voiceInstructions = Arrays.asList(
                    new InitialVoiceInstructionConfig(IN_HIGHER_DISTANCE_PLURAL.metric, 4250, 250, unit),
                    new FixedVoiceInstructionConfig(IN_HIGHER_DISTANCE_PLURAL.metric, 2000, 2),
                    new FixedVoiceInstructionConfig(IN_HIGHER_DISTANCE_SINGULAR.metric, 1000, 1),
                    new DecisionVoiceInstructionConfig(IN_LOWER_DISTANCE_PLURAL.metric, new int[]{400, 200}, new int[]{400, 200})
            );
        } else {
            voiceInstructions = Arrays.asList(
                    new InitialVoiceInstructionConfig(IN_HIGHER_DISTANCE_PLURAL.metric, 4250, 250, unit),
                    new FixedVoiceInstructionConfig(IN_HIGHER_DISTANCE_PLURAL.imperial, 3220, 2),
                    new FixedVoiceInstructionConfig(IN_HIGHER_DISTANCE_SINGULAR.imperial, 1610, 1),
                    new DecisionVoiceInstructionConfig(IN_LOWER_DISTANCE_PLURAL.metric, new int[]{400, 200}, new int[]{1300, 600})
            );
        }

    }

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
                if (distanceMeter > distanceAlongGeometry[i]) {
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

        public InitialVoiceInstructionConfig(String key, int distanceForInitialStayInstruction, int distanceDelay, DistanceUtils.Unit unit) {
            super(key);
            this.distanceForInitialStayInstruction = distanceForInitialStayInstruction;
            this.distanceDelay = distanceDelay;
        }

        public int distanceAlongGeometry(double distanceMeter) {
            // Cast to full units
            int tmpDistance = (int) (distanceMeter - distanceDelay);
            if (unit == DistanceUtils.Unit.METRIC) {
                return (tmpDistance / 1000) * 1000;
            } else {
                tmpDistance = (int) (tmpDistance * meterToMiles);
                return (int) (tmpDistance / meterToMiles);
            }
        }

        public int distanceVoiceValue(double distanceInMeter) {
            if (unit == DistanceUtils.Unit.METRIC) {
                return (int) (distanceInMeter * meterToKilometer);
            } else {
                return (int) (distanceInMeter * meterToMiles);
            }
        }
    }
}