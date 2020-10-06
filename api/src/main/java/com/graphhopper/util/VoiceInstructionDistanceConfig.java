/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
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
package com.graphhopper.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.graphhopper.util.VoiceInstructionDistanceUtils.UnitTranslationKey.*;

public class VoiceInstructionDistanceConfig {
    final List<VoiceInstructionConfig> voiceInstructions;
    private final Translation translation;

    public VoiceInstructionDistanceConfig(VoiceInstructionDistanceUtils.Unit unit, Translation translation) {
        if (unit == VoiceInstructionDistanceUtils.Unit.METRIC) {
            voiceInstructions = Arrays.asList(
                    new InitialVoiceInstructionConfig(FOR_HIGHER_DISTANCE_PLURAL.metric, translation, 4250, 250, unit),
                    new FixedDistanceVoiceInstructionConfig(IN_HIGHER_DISTANCE_PLURAL.metric, translation, 2000, 2),
                    new FixedDistanceVoiceInstructionConfig(IN_HIGHER_DISTANCE_SINGULAR.metric, translation, 1000, 1),
                    new ConditionalDistanceVoiceInstructionConfig(IN_LOWER_DISTANCE_PLURAL.metric, translation, new int[]{400, 200}, new int[]{400, 200})
            );
        } else {
            voiceInstructions = Arrays.asList(
                    new InitialVoiceInstructionConfig(FOR_HIGHER_DISTANCE_PLURAL.metric, translation, 4250, 250, unit),
                    new FixedDistanceVoiceInstructionConfig(IN_HIGHER_DISTANCE_PLURAL.imperial, translation, 3220, 2),
                    new FixedDistanceVoiceInstructionConfig(IN_HIGHER_DISTANCE_SINGULAR.imperial, translation, 1610, 1),
                    new ConditionalDistanceVoiceInstructionConfig(IN_LOWER_DISTANCE_PLURAL.imperial, translation, new int[]{400, 200}, new int[]{1300, 600})
            );
        }

        this.translation = translation;
    }

    public List<VoiceInstructionConfig.VoiceInstructionValue> getVoiceInstructionsForDistance(double distance, String turnDescription, String thenVoiceInstruction) {
        List<VoiceInstructionConfig.VoiceInstructionValue> instructionsConfigs = new ArrayList<>(voiceInstructions.size());
        for (VoiceInstructionConfig voiceConfig : voiceInstructions) {
            VoiceInstructionConfig.VoiceInstructionValue confi = voiceConfig.getConfigForDistance(distance, turnDescription, thenVoiceInstruction);
            if (confi != null) {
                instructionsConfigs.add(confi);
            }
        }
        return instructionsConfigs;
    }

    public Translation getTranslation() {
        return translation;
    }

}