package com.graphhopper.navigation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;


public class VoiceInstructionConfigTest {

    @Test(expected = IllegalArgumentException.class)
    public void decisionVoiceInstructionConfigShouldHaveSameImportValueSize() {
        new DecisionVoiceInstructionConfig("", new int[]{400, 200}, new int[]{400});
    }

    @Test
    public void decisionVoiceInstructionConfigShouldReturnFirstFittingIndex() {
        DecisionVoiceInstructionConfig config = new DecisionVoiceInstructionConfig("", new int[]{400, 200}, new int[]{400, 200});
        assertEquals(0, config.getFittingInstructionIndex(10010));
        assertEquals(0, config.getFittingInstructionIndex(450));
        assertEquals(0, config.getFittingInstructionIndex(400));
        assertEquals(1, config.getFittingInstructionIndex(399));
        assertEquals(1, config.getFittingInstructionIndex(200));
        assertEquals(-1, config.getFittingInstructionIndex(190));
    }

    @Test
    public void initialVoiceInstructionConfigMetricTest() {
        InitialVoiceInstructionConfig configMetric = new InitialVoiceInstructionConfig("", 4250, 250, DistanceUtils.Unit.METRIC);
        assertEquals(4000, configMetric.distanceAlongGeometry(5000));
        assertEquals(4000, configMetric.distanceAlongGeometry(4500));

        assertEquals(4, configMetric.distanceVoiceValue(5000));
        assertEquals(4, configMetric.distanceVoiceValue(4500));

        InitialVoiceInstructionConfig configImperial = new InitialVoiceInstructionConfig("", 4250, 250, DistanceUtils.Unit.IMPERIAL);
        assertEquals(3219, configImperial.distanceAlongGeometry(5000));
        assertEquals(3219, configImperial.distanceAlongGeometry(4500));

        assertEquals(2, configImperial.distanceVoiceValue(5000));
        assertEquals(2, configImperial.distanceVoiceValue(4500));
    }

}