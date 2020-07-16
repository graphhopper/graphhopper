package com.graphhopper.navigation;

import com.graphhopper.util.Helper;
import com.graphhopper.util.TranslationMap;

import java.util.Locale;

import static com.graphhopper.navigation.DistanceUtils.meterToKilometer;
import static com.graphhopper.navigation.DistanceUtils.meterToMiles;

abstract class VoiceInstructionConfig {
    protected final String translationKey;
    protected final TranslationMap translationMap;
    protected final Locale locale;

    public VoiceInstructionConfig(String translationKey, TranslationMap translationMap, Locale locale) {
        this.translationKey = translationKey;
        this.translationMap = translationMap;
        this.locale = locale;
    }

    class VoiceInstructionValue {
        final int spokenDistance;
        final String turnDescription;

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

    public ConditionalDistanceVoiceInstructionConfig(String key, TranslationMap translationMap, Locale locale,
                                                     int[] distanceAlongGeometry, int[] distanceVoiceValue) {
        super(key, translationMap, locale);
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
        String totalDescription = translationMap.getWithFallBack(locale).tr("navigate." + translationKey, distanceVoiceValue[instructionIndex]) + " " + turnDescription + thenVoiceInstruction;
        int spokenDistance = distanceAlongGeometry[instructionIndex];
        return new VoiceInstructionValue(spokenDistance, totalDescription);
    }
}

class FixedDistanceVoiceInstructionConfig extends VoiceInstructionConfig {
    private final int distanceAlongGeometry; // distance in meter in which the instruction should be spoken
    private final int distanceVoiceValue; // distance in required unit. f.e: 1km, 300m or 2mi

    public FixedDistanceVoiceInstructionConfig(String key, TranslationMap navigateResponseConverterTranslationMap, Locale locale, int distanceAlongGeometry, int distanceVoiceValue) {
        super(key, navigateResponseConverterTranslationMap, locale);
        this.distanceAlongGeometry = distanceAlongGeometry;
        this.distanceVoiceValue = distanceVoiceValue;
    }

    @Override
    public VoiceInstructionValue getConfigForDistance(double distance, String turnDescription, String thenVoiceInstruction) {
        if (distance >= distanceAlongGeometry) {
            String totalDescription = translationMap.getWithFallBack(locale).tr("navigate." + translationKey, distanceVoiceValue) + " " + turnDescription;
            return new VoiceInstructionValue(distanceAlongGeometry, totalDescription);
        }
        return null;
    }
}


class InitialVoiceInstructionConfig extends VoiceInstructionConfig {
    // The instruction should not be spoken straight away, but wait until the user merged on the new road and can listen to instructions again
    private final int distanceDelay; // delay distance in meter
    private final int distanceForInitialStayInstruction; // min distance in meter for initial instruction
    private final DistanceUtils.Unit unit;
    private final TranslationMap translationMap;

    public InitialVoiceInstructionConfig(String key, TranslationMap translationMap, Locale locale, int distanceForInitialStayInstruction, int distanceDelay, DistanceUtils.Unit unit) {
        super(key, translationMap, locale);
        this.distanceForInitialStayInstruction = distanceForInitialStayInstruction;
        this.distanceDelay = distanceDelay;
        this.unit = unit;
        this.translationMap = translationMap;
    }

    private int distanceAlongGeometry(double distanceMeter) {
        // Cast to full units
        int tmpDistance = (int) (distanceMeter - distanceDelay);
        if (unit == DistanceUtils.Unit.METRIC) {
            return (tmpDistance / 1000) * 1000;
        } else {
            tmpDistance = (int) (tmpDistance * meterToMiles);
            return (int) Math.ceil(tmpDistance / meterToMiles);
        }
    }

    private int distanceVoiceValue(double distanceInMeter) {
        if (unit == DistanceUtils.Unit.METRIC) {
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
            String continueDescription = translationMap.getWithFallBack(locale).tr("continue") + " " +
                    this.translationMap.getWithFallBack(locale).tr("navigate." + translationKey, distanceVoiceValue);
            continueDescription = Helper.firstBig(continueDescription);
            return new VoiceInstructionValue(spokenDistance, continueDescription);
        }
        return null;
    }
}
