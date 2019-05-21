package com.graphhopper.navigation;

import java.util.Arrays;
import java.util.List;

import static com.graphhopper.navigation.DistanceUtils.UnitTranslationKey.*;

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
                    new DecisionVoiceInstructionConfig(IN_LOWER_DISTANCE_PLURAL.imperial, new int[]{400, 200}, new int[]{1300, 600})
            );
        }

    }


}