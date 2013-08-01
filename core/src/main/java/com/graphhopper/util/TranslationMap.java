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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Peter Karich
 */
public class TranslationMap
{
    public static final TranslationMap SINGLETON = new TranslationMap().doImport();
    private Map<String, Translation> translations = new HashMap<String, Translation>();

    public TranslationMap doImport()
    {
        for (String key : Arrays.asList("en", "de", "es"))
        {
            TranslationHashMap map = new TranslationHashMap(key).doImport(getClass().getResourceAsStream(key + ".properties"));
            translations.put(key, map);
        }
        return this;
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

    static class TranslationHashMap implements Translation
    {
        final Map<String, String> map = new HashMap<String, String>();
        final String language;

        TranslationHashMap( String language )
        {
            this.language = language;
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
                for (String line : Helper.readFile(new InputStreamReader(resourceAsStream)))
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
