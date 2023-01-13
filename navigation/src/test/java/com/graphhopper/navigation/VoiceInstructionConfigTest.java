package com.graphhopper.navigation;

import com.graphhopper.core.util.TranslationMap;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static com.graphhopper.navigation.DistanceUtils.UnitTranslationKey.*;
import static org.junit.jupiter.api.Assertions.*;


public class VoiceInstructionConfigTest {

    private final TranslationMap trMap = new TranslationMap().doImport();
    private final Locale locale = Locale.ENGLISH;

    @Test
    public void conditionalDistanceVICShouldHaveSameImportValueSize() {
        assertThrows(IllegalArgumentException.class, () -> new ConditionalDistanceVoiceInstructionConfig(IN_LOWER_DISTANCE_PLURAL.metric, trMap, locale, new int[]{400, 200}, new int[]{400}));
    }

    @Test
    public void conditionalDistanceVICShouldReturnFirstFittingMetricValues() {
        ConditionalDistanceVoiceInstructionConfig config = new ConditionalDistanceVoiceInstructionConfig(IN_LOWER_DISTANCE_PLURAL.metric, trMap, locale, new int[]{400, 200}, new int[]{400, 200});

        compareVoiceInstructionValues(
                400,
                "In 400 meters turn then",
                config.getConfigForDistance(10010, "turn", " then")
        );

        compareVoiceInstructionValues(
                400,
                "In 400 meters turn then",
                config.getConfigForDistance(450, "turn", " then")
        );

        compareVoiceInstructionValues(
                400,
                "In 400 meters turn then",
                config.getConfigForDistance(400, "turn", " then")
        );

        compareVoiceInstructionValues(
                200,
                "In 200 meters turn then",
                config.getConfigForDistance(399, "turn", " then")
        );

        compareVoiceInstructionValues(
                200,
                "In 200 meters turn then",
                config.getConfigForDistance(200, "turn", " then")
        );

        assertNull(config.getConfigForDistance(190, "turn", " then"));
    }

    @Test
    public void conditionalDistanceVICShouldReturnFirstFittingImperialValues() {
        ConditionalDistanceVoiceInstructionConfig config = new ConditionalDistanceVoiceInstructionConfig(IN_LOWER_DISTANCE_PLURAL.imperial,
                trMap, locale, new int[]{400, 200}, new int[]{600, 500});

        compareVoiceInstructionValues(
                400,
                "In 600 feet turn then",
                config.getConfigForDistance(10010, "turn", " then")
        );

        compareVoiceInstructionValues(
                400,
                "In 600 feet turn then",
                config.getConfigForDistance(450, "turn", " then")
        );

        compareVoiceInstructionValues(
                400,
                "In 600 feet turn then",
                config.getConfigForDistance(400, "turn", " then")
        );

        compareVoiceInstructionValues(
                200,
                "In 500 feet turn then",
                config.getConfigForDistance(399, "turn", " then")
        );

        compareVoiceInstructionValues(
                200,
                "In 500 feet turn then",
                config.getConfigForDistance(200, "turn", " then")
        );

        assertNull(config.getConfigForDistance(190, "turn", " then"));
    }

    @Test
    public void initialVICMetricTest() {
        InitialVoiceInstructionConfig configMetric = new InitialVoiceInstructionConfig(FOR_HIGHER_DISTANCE_PLURAL.metric, trMap,
                locale, 4250, 250, DistanceUtils.Unit.METRIC);

        compareVoiceInstructionValues(
                4000,
                "Continue for 4 kilometers",
                configMetric.getConfigForDistance(5000, "turn", " then")
        );

        compareVoiceInstructionValues(
                4000,
                "Continue for 4 kilometers",
                configMetric.getConfigForDistance(4500, "turn", " then")
        );
    }

    @Test
    public void germanInitialVICMetricTest() {
        InitialVoiceInstructionConfig configMetric = new InitialVoiceInstructionConfig(FOR_HIGHER_DISTANCE_PLURAL.metric, trMap,
                Locale.GERMAN, 4250, 250, DistanceUtils.Unit.METRIC);

        compareVoiceInstructionValues(
                4000,
                "Dem Straßenverlauf folgen für 4 Kilometer",
                configMetric.getConfigForDistance(5000, "abbiegen", " dann")
        );

        compareVoiceInstructionValues(
                4000,
                "Dem Straßenverlauf folgen für 4 Kilometer",
                configMetric.getConfigForDistance(4500, "abbiegen", " dann")
        );
    }

    @Test
    public void initialVICImperialTest() {
        InitialVoiceInstructionConfig configImperial = new InitialVoiceInstructionConfig(FOR_HIGHER_DISTANCE_PLURAL.imperial, trMap,
                locale, 4250, 250, DistanceUtils.Unit.IMPERIAL);

        compareVoiceInstructionValues(
                3219,
                "Continue for 2 miles",
                configImperial.getConfigForDistance(5000, "turn", " then")
        );

        compareVoiceInstructionValues(
                3219,
                "Continue for 2 miles",
                configImperial.getConfigForDistance(4500, "turn", " then")
        );
    }

    @Test
    public void fixedDistanceInitialVICMetricTest() {
        FixedDistanceVoiceInstructionConfig configMetric = new FixedDistanceVoiceInstructionConfig(IN_HIGHER_DISTANCE_PLURAL.metric,
                trMap, locale, 2000, 2);

        compareVoiceInstructionValues(
                2000,
                "In 2 kilometers turn",
                configMetric.getConfigForDistance(2100, "turn", " then")
        );

        compareVoiceInstructionValues(
                2000,
                "In 2 kilometers turn",
                configMetric.getConfigForDistance(2000, "turn", " then")
        );

        assertNull(configMetric.getConfigForDistance(1999, "turn", " then"));
    }

    @Test
    public void germanFixedDistanceInitialVICMetricTest() {
        FixedDistanceVoiceInstructionConfig configMetric = new FixedDistanceVoiceInstructionConfig(IN_HIGHER_DISTANCE_PLURAL.metric,
                trMap, Locale.GERMAN, 2000, 2);

        compareVoiceInstructionValues(
                2000,
                "In 2 Kilometern abbiegen",
                configMetric.getConfigForDistance(2100, "abbiegen", " dann")
        );

        compareVoiceInstructionValues(
                2000,
                "In 2 Kilometern abbiegen",
                configMetric.getConfigForDistance(2000, "abbiegen", " dann")
        );

        assertNull(configMetric.getConfigForDistance(1999, "abbiegen", " dann"));
    }

    @Test
    public void fixedDistanceInitialVICImperialTest() {
        FixedDistanceVoiceInstructionConfig configImperial = new FixedDistanceVoiceInstructionConfig(IN_HIGHER_DISTANCE_PLURAL.imperial,
                trMap, locale, 2000, 2);

        compareVoiceInstructionValues(
                2000,
                "In 2 miles turn",
                configImperial.getConfigForDistance(2100, "turn", " then")
        );

        compareVoiceInstructionValues(
                2000,
                "In 2 miles turn",
                configImperial.getConfigForDistance(2000, "turn", " then")
        );
        assertNull(configImperial.getConfigForDistance(1999, "turn", " then"));
    }

    private void compareVoiceInstructionValues(int expectedSpokenDistance,
                                               String expectedInstruction,
                                               VoiceInstructionConfig.VoiceInstructionValue values) {
        assertEquals(expectedSpokenDistance, values.spokenDistance);
        assertEquals(expectedInstruction, values.turnDescription);
    }
}