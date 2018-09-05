package com.graphhopper.navigation.mapbox;

import com.graphhopper.util.TranslationMap;

import java.io.File;
import java.util.Arrays;

import static com.graphhopper.util.Helper.getLocale;

public class MapboxResponseConverterTranslationMap extends TranslationMap {

    public static MapboxResponseConverterTranslationMap INSTANCE;

    private MapboxResponseConverterTranslationMap() {
        super();
    }

    static {
        INSTANCE = new MapboxResponseConverterTranslationMap();
        INSTANCE.doImport();
    }

    @Override
    /**
     * This loads the translation files from the specified folder.
     */
    public TranslationMap doImport() {

        File folder = new File("files/translations/");

        try {
            for (String locale : Arrays.asList("de_DE", "en_US")) {
                TranslationHashMap trMap = new TranslationHashMap(getLocale(locale));
                trMap.doImport(MapboxResponseConverterTranslationMap.class.getResourceAsStream(locale + ".txt"));
                add(trMap);
            }
            //Not accessible, how bad is this?
            //postImportHook();
            return this;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

}
