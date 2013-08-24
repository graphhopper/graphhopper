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
 * A class which manages the translations in-memory.
 * <p/>
 * @author Peter Karich
 */
public class TranslationMap
{
    // use 'en' as reference
    private static final List<String> LANGUAGES = Arrays.asList("bg", "de", "en", "es", "fr", "ja", "pt", "ro", "ru");
    private Map<String, Translation> translations = new HashMap<String, Translation>();

    /**
     * This loads the translation files from the specified folder.
     */
    public TranslationMap doImport( File folder )
    {
        try
        {
            for (String key : LANGUAGES)
            {
                TranslationHashMap trMap = new TranslationHashMap(key);
                trMap.doImport(new FileInputStream(new File(folder, key + ".txt")));
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
            for (String key : LANGUAGES)
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
        translations.put(tr.getLanguage(), tr);
    }

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

    public Translation get( String language )
    {
        return translations.get(language);
    }

    public static interface Translation
    {
        String tr( String key, Object... params );

        Map<String, String> asMap();

        String getLanguage();
    }

    public static class TranslationHashMap implements Translation
    {
        private final Map<String, String> map = new HashMap<String, String>();
        private final String language;

        public TranslationHashMap( String language )
        {
            this.language = language;
        }

        public void clear()
        {
            map.clear();
        }

        @Override
        public String getLanguage()
        {
            return language;
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
