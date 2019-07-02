package com.graphhopper.navigation;

import com.graphhopper.util.TranslationMap;
import org.junit.Test;

import java.util.Locale;

import static com.graphhopper.navigation.DistanceUtils.UnitTranslationKey.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;


public class VoiceInstructionConfigTest {

    private final TranslationMap mtrMap = new NavigateResponseConverterTranslationMap().doImport();
    private final Locale locale = Locale.ENGLISH;

    @Test(expected = IllegalArgumentException.class)
    public void conditionalDistanceVICShouldHaveSameImportValueSize() {
        new ConditionalDistanceVoiceInstructionConfig(IN_LOWER_DISTANCE_PLURAL.metric, mtrMap, locale, new int[]{400, 200}, new int[]{400});
    }

    @Test
    public void conditionalDistanceVICShouldReturnFirstFittingMetricValues() {
        ConditionalDistanceVoiceInstructionConfig config = new ConditionalDistanceVoiceInstructionConfig(IN_LOWER_DISTANCE_PLURAL.metric, mtrMap, locale, new int[]{400, 200}, new int[]{400, 200});

        compareVoiceInstrucationValues(
                400,
                "In 400 meters turn then",
                config.getConfigForDistance(10010, "turn", " then")
        );

        compareVoiceInstrucationValues(
                400,
                "In 400 meters turn then",
                config.getConfigForDistance(450, "turn", " then")
        );

        compareVoiceInstrucationValues(
                400,
                "In 400 meters turn then",
                config.getConfigForDistance(400, "turn", " then")
        );

        compareVoiceInstrucationValues(
                200,
                "In 200 meters turn then",
                config.getConfigForDistance(399, "turn", " then")
        );

        compareVoiceInstrucationValues(
                200,
                "In 200 meters turn then",
                config.getConfigForDistance(200, "turn", " then")
        );

        assertNull( config.getConfigForDistance(190, "turn", " then"));
    }

    @Test
    public void conditionalDistanceVICShouldReturnFirstFittingImperialValues() {
        ConditionalDistanceVoiceInstructionConfig config = new ConditionalDistanceVoiceInstructionConfig(IN_LOWER_DISTANCE_PLURAL.imperial, mtrMap, locale, new int[]{400, 200}, new int[]{600, 500});

        compareVoiceInstrucationValues(
                400,
                "In 600 feet turn then",
                config.getConfigForDistance(10010, "turn", " then")
        );

        compareVoiceInstrucationValues(
                400,
                "In 600 feet turn then",
                config.getConfigForDistance(450, "turn", " then")
        );

        compareVoiceInstrucationValues(
                400,
                "In 600 feet turn then",
                config.getConfigForDistance(400, "turn", " then")
        );

        compareVoiceInstrucationValues(
                200,
                "In 500 feet turn then",
                config.getConfigForDistance(399, "turn", " then")
        );

        compareVoiceInstrucationValues(
                200,
                "In 500 feet turn then",
                config.getConfigForDistance(200, "turn", " then")
        );

        assertNull( config.getConfigForDistance(190, "turn", " then"));
    }

    @Test
    public void initialVICMetricTest() {
        InitialVoiceInstructionConfig configMetric = new InitialVoiceInstructionConfig(FOR_HIGHER_DISTANCE_PLURAL.metric, mtrMap, locale, 4250, 250, DistanceUtils.Unit.METRIC);

        compareVoiceInstrucationValues(
                4000,
                "continue for 4 kilometer",
                configMetric.getConfigForDistance(5000, "turn", " then")
        );

        compareVoiceInstrucationValues(
                4000,
                "continue for 4 kilometer",
                configMetric.getConfigForDistance(4500, "turn", " then")
        );
    }

    @Test
    public void initialVICImperialTest() {
        InitialVoiceInstructionConfig configImperial = new InitialVoiceInstructionConfig(FOR_HIGHER_DISTANCE_PLURAL.imperial, mtrMap, locale, 4250, 250, DistanceUtils.Unit.IMPERIAL);

        compareVoiceInstrucationValues(
                3219,
                "continue for 2 miles",
                configImperial.getConfigForDistance(5000, "turn", " then")
        );

        compareVoiceInstrucationValues(
                3219,
                "continue for 2 miles",
                configImperial.getConfigForDistance(4500, "turn", " then")
        );
    }

    @Test
    public void fixedDistancenitialVICMetricTest(){
        FixedDistanceVoiceInstructionConfig configMetric = new FixedDistanceVoiceInstructionConfig(IN_HIGHER_DISTANCE_PLURAL.metric,  mtrMap, locale, 2000, 2);

        compareVoiceInstrucationValues(
                2000,
                "In 2 kilometers turn",
                configMetric.getConfigForDistance(2100, "turn", " then")
        );

        compareVoiceInstrucationValues(
                2000,
                "In 2 kilometers turn",
                configMetric.getConfigForDistance(2000, "turn", " then")
        );

        assertNull( configMetric.getConfigForDistance(1999, "turn", " then"));
    }

    @Test
    public void fixedDistancenitialVICImperialTest(){
        FixedDistanceVoiceInstructionConfig configImperial = new FixedDistanceVoiceInstructionConfig(IN_HIGHER_DISTANCE_PLURAL.imperial,  mtrMap, locale, 2000, 2);

        compareVoiceInstrucationValues(
                2000,
                "In 2 miles turn",
                configImperial.getConfigForDistance(2100, "turn", " then")
        );

        compareVoiceInstrucationValues(
                2000,
                "In 2 miles turn",
                configImperial.getConfigForDistance(2000, "turn", " then")
        );
        assertNull( configImperial.getConfigForDistance(1999, "turn", " then"));
    }


    // Helper

    private void compareVoiceInstrucationValues(int expectedSpokenDistance,
                                                String expectedInstruction,
                                                VoiceInstructionConfig.VoiceInstructionValue values) {
        assertEquals(expectedSpokenDistance, values.spokenDistance);
        assertEquals(expectedInstruction, values.turnDescription);

    }

}