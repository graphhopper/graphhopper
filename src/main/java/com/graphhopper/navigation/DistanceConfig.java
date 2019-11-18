package com.graphhopper.navigation;


import com.graphhopper.util.TranslationMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static com.graphhopper.navigation.DistanceUtils.UnitTranslationKey.*;

public class DistanceConfig {
    final List<VoiceInstructionConfig> voiceInstructions;

    public DistanceConfig(DistanceUtils.Unit unit,  TranslationMap translationMap, TranslationMap navigateResponseConverterTranslationMap, Locale locale) {
        if (unit == DistanceUtils.Unit.METRIC) {
            voiceInstructions = Arrays.asList(
                    new InitialVoiceInstructionConfig(FOR_HIGHER_DISTANCE_PLURAL.metric, translationMap, navigateResponseConverterTranslationMap, locale, 4250, 250, unit),
                    new FixedDistanceVoiceInstructionConfig(IN_HIGHER_DISTANCE_PLURAL.metric, navigateResponseConverterTranslationMap, locale, 2000, 2),
                    new FixedDistanceVoiceInstructionConfig(IN_HIGHER_DISTANCE_SINGULAR.metric, navigateResponseConverterTranslationMap, locale, 1000, 1),
                    new ConditionalDistanceVoiceInstructionConfig(IN_LOWER_DISTANCE_PLURAL.metric, navigateResponseConverterTranslationMap, locale, new int[]{400, 200}, new int[]{400, 200})
            );
        } else {
            voiceInstructions = Arrays.asList(
                    new InitialVoiceInstructionConfig(FOR_HIGHER_DISTANCE_PLURAL.metric, translationMap, navigateResponseConverterTranslationMap, locale, 4250, 250, unit),
                    new FixedDistanceVoiceInstructionConfig(IN_HIGHER_DISTANCE_PLURAL.imperial, navigateResponseConverterTranslationMap, locale, 3220, 2),
                    new FixedDistanceVoiceInstructionConfig(IN_HIGHER_DISTANCE_SINGULAR.imperial, navigateResponseConverterTranslationMap, locale, 1610, 1),
                    new ConditionalDistanceVoiceInstructionConfig(IN_LOWER_DISTANCE_PLURAL.imperial, navigateResponseConverterTranslationMap, locale, new int[]{400, 200}, new int[]{1300, 600})
            );
        }

    }

    public List<VoiceInstructionConfig.VoiceInstructionValue> getVoiceInstructionsForDistance(double distance, String turnDescription, String thenVoiceInstruction) {
        List<VoiceInstructionConfig.VoiceInstructionValue> instructionsConfigs = new ArrayList(voiceInstructions.size());
        for (VoiceInstructionConfig voiceConfig : voiceInstructions) {
            VoiceInstructionConfig.VoiceInstructionValue confi = voiceConfig.getConfigForDistance(distance, turnDescription, thenVoiceInstruction);
            if(confi!=null){
                instructionsConfigs.add(confi);
            }
        }
        return instructionsConfigs;
    }


}