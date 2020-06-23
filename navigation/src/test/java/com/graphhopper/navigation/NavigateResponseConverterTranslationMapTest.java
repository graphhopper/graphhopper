package com.graphhopper.navigation;

import com.graphhopper.util.TranslationMap;
import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;

public class NavigateResponseConverterTranslationMapTest {

    @Test
    public void basicTest() {

        TranslationMap translationMap = new NavigateResponseConverterTranslationMap().doImport();

        assertEquals("In 12 kilometers", translationMap.getWithFallBack(Locale.US).tr("in_km", 12));
        assertEquals("In 12 Kilometern", translationMap.getWithFallBack(Locale.GERMAN).tr("in_km", 12));
        assertEquals("In 12 Kilometern", translationMap.getWithFallBack(new Locale("de", "DE")).tr("in_km", 12));
        assertEquals("In 1 Kilometer", translationMap.getWithFallBack(new Locale("de", "CH")).tr("in_km_singular"));

    }
}
