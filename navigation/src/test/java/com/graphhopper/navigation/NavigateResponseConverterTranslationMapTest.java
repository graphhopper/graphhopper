package com.graphhopper.navigation;

import com.graphhopper.util.TranslationMap;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NavigateResponseConverterTranslationMapTest {

    @Test
    public void basicTest() {
        TranslationMap translationMap = new TranslationMap().doImport();
        assertEquals(translationMap.getWithFallBack(Locale.US).tr("navigate.in_km", 12), "In 12 kilometers");
        assertEquals(translationMap.getWithFallBack(Locale.GERMAN).tr("navigate.in_km", 12), "In 12 Kilometern");
        assertEquals(translationMap.getWithFallBack(new Locale("de", "DE")).tr("navigate.in_km", 12), "In 12 Kilometern");
        assertEquals(translationMap.getWithFallBack(new Locale("de", "CH")).tr("navigate.in_km_singular"), "In 1 Kilometer");
    }
}
