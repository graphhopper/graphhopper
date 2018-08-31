package com.graphhopper.navigation.mapbox;

import org.junit.Test;

import java.util.Locale;

import static com.graphhopper.navigation.mapbox.SimpleTranslationMap.TranslationKeys;
import static com.graphhopper.navigation.mapbox.SimpleTranslationMap.tr;
import static org.junit.Assert.assertEquals;

public class SimpleTranslationMapTest {

    @Test
    public void basicTest() {

        assertEquals("In 12 kilometers ", tr(Locale.US, TranslationKeys.IN_KM, 12));
        assertEquals("In 12 Kilometern ", tr(Locale.GERMAN, TranslationKeys.IN_KM, 12));
        assertEquals("In 12 Kilometern ", tr(new Locale("de", "DE"), TranslationKeys.IN_KM, 12));
        assertEquals("In 1 Kilometer ", tr(new Locale("de", "CH"), TranslationKeys.IN_KM_SINGULAR));

    }
}
