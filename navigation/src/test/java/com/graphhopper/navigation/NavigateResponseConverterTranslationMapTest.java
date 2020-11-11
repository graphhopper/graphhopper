package com.graphhopper.navigation;

import com.graphhopper.util.TranslationMap;
import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;

public class NavigateResponseConverterTranslationMapTest {

    @Test
    public void basicTest() {
        TranslationMap translationMap = new TranslationMap().doImport();
        assertEquals("In 12 kilometers", translationMap.getWithFallBack(Locale.US).tr("navigate.in_km", 12));
        assertEquals("In 12 Kilometern", translationMap.getWithFallBack(Locale.GERMAN).tr("navigate.in_km", 12));
        assertEquals("In 12 Kilometern", translationMap.getWithFallBack(new Locale("de", "DE")).tr("navigate.in_km", 12));
        assertEquals("In 1 Kilometer", translationMap.getWithFallBack(new Locale("de", "CH")).tr("navigate.in_km_singular"));
    }
}
