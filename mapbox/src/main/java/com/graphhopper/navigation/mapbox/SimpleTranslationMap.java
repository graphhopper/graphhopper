package com.graphhopper.navigation.mapbox;

import java.util.Locale;

public class SimpleTranslationMap {

    public enum TranslationKeys {
        IN_KM_SINGULAR,
        IN_KM,
        IN_M
    }

    public static String tr(Locale locale, TranslationKeys key, Object... params) {
        return String.format(Locale.ROOT, SimpleTranslationMap.privateTr(locale, key), params);
    }

    private static String privateTr(Locale locale, TranslationKeys key) {
        if (locale.getLanguage().equals(Locale.GERMAN.getLanguage())) {
            switch (key) {
                case IN_KM_SINGULAR:
                    return "In 1 Kilometer ";
                case IN_KM:
                    return "In %1$s Kilometern ";
                case IN_M:
                    return "In %1$s Metern ";
                default:
                    return "unknown translation - " + key;
            }
        }

        // Fallback to en
        switch (key) {
            case IN_KM_SINGULAR:
                return "In 1 kilometer ";
            case IN_KM:
                return "In %1$s kilometers ";
            case IN_M:
                return "In %1$s meters ";
            default:
                return "unknown translation - " + key;
        }

    }

}
