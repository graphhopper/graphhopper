package com.graphhopper.navigation.mapbox;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;

public class MapboxResponseConverterTranslationMapTest {

    @Test
    public void basicTest() {

        assertEquals("In 12 kilometers", MapboxResponseConverterTranslationMap.INSTANCE.getWithFallBack(Locale.US).tr("in_km", 12));
        assertEquals("In 12 Kilometern", MapboxResponseConverterTranslationMap.INSTANCE.getWithFallBack(Locale.GERMAN).tr("in_km", 12));
        assertEquals("In 12 Kilometern", MapboxResponseConverterTranslationMap.INSTANCE.getWithFallBack(new Locale("de", "DE")).tr("in_km", 12));
        assertEquals("In 1 Kilometer", MapboxResponseConverterTranslationMap.INSTANCE.getWithFallBack(new Locale("de", "CH")).tr("in_km_singular"));

    }
}
