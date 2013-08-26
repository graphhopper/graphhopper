/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.util;

import java.io.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A class which manages the translations in-memory. Translations are managed here:
 * https://docs.google.com/spreadsheet/ccc?key=0AmukcXek0JP6dGM4R1VTV2d3TkRSUFVQakhVeVBQRHc#gid=0
 * <p/>
 * and can be easily converted to a language file via:
 * <p/>
 * cat GraphHopper.csv | cut -d',' -s -f1,10 --output-delimiter='=' > ja.txt
 * <p/>
 * @author Peter Karich
 */
public class TranslationMap
{
    // use 'en' as reference
    private static final List<String> LOCALES = Arrays.asList("bg", "de_DE", "en", "es", "fr", "ja", "pt_PT", "ro", "ru");
    private Map<String, Translation> translations = new HashMap<String, Translation>();

    /**
     * This loads the translation files from the specified folder.
     */
    public TranslationMap doImport( File folder )
    {
        try
        {
            for (String locale : LOCALES)
            {
                TranslationHashMap trMap = new TranslationHashMap(locale);
                trMap.doImport(new FileInputStream(new File(folder, locale + ".txt")));
                add(trMap);
            }
            return this;
        } catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }

    /**
     * This loads the translation files from classpath.
     */
    public TranslationMap doImport()
    {
        try
        {
            for (String key : LOCALES)
            {
                TranslationHashMap trMap = new TranslationHashMap(key);
                trMap.doImport(TranslationMap.class.getResourceAsStream(key + ".txt"));
                add(trMap);
            }
            return this;
        } catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }

    public void add( Translation tr )
    {
        String locale = tr.getLocale();
        translations.put(locale, tr);
        if (locale.contains("_") && locale.endsWith(tr.getLanguage().toUpperCase()))
            translations.put(tr.getLanguage(), tr);
    }

    /**
     * Returns the Translation object for the specified locale and falls back to english if the
     * locale was not found.
     */
    public Translation getWithFallBack( Locale locale )
    {
        Translation tr = get(locale.toString());
        if (tr == null)
        {
            tr = get(locale.getLanguage());
            if (tr == null)
                tr = get("en");
        }
        return tr;
    }

    /**
     * Returns the Translation object for the specified locale.
     */
    public Translation get( String locale )
    {
        locale = locale.replace("-", "_");
        Translation tr = translations.get(locale);
        if (locale.contains("_") && tr == null)
            tr = translations.get(locale.substring(0, 2));

        return tr;
    }

    public static interface Translation
    {
        String tr( String key, Object... params );

        Map<String, String> asMap();

        String getLocale();

        String getLanguage();
    }

    public static class TranslationHashMap implements Translation
    {
        private final Map<String, String> map = new HashMap<String, String>();
        private final String locale;

        public TranslationHashMap( String locale )
        {
            this.locale = locale;
        }

        public void clear()
        {
            map.clear();
        }

        @Override
        public String getLocale()
        {
            return locale;
        }

        @Override
        public String getLanguage()
        {
            if (locale.contains("_"))
                return locale.substring(0, 2);
            return locale;
        }

        @Override
        public String tr( String key, Object... params )
        {
            String val = map.get(key);
            if (Helper.isEmpty(val))
                return key;

            return String.format(val, params);
        }

        public TranslationHashMap put( String key, String val )
        {
            map.put(key, val);
            return this;
        }

        @Override
        public String toString()
        {
            return map.toString();
        }

        @Override
        public Map<String, String> asMap()
        {
            return map;
        }

        public TranslationHashMap doImport( InputStream resourceAsStream )
        {
            try
            {
                for (String line : Helper.readFile(new InputStreamReader(resourceAsStream, "UTF-8")))
                {
                    if (line.isEmpty() || line.startsWith("//") || line.startsWith("#"))
                        continue;

                    int index = line.indexOf('=');
                    if (index < 0)
                        continue;
                    String key = line.substring(0, index);
                    String value = line.substring(index + 1);
                    put(key, value);
                }
            } catch (IOException ex)
            {
                throw new RuntimeException(ex);
            }
            return this;
        }
    }

    @Override
    public String toString()
    {
        return translations.toString();
    }
}
