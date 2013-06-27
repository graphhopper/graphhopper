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

import java.util.HashMap;
import java.util.Map;

/**
 * @author Peter Karich
 */
public class TranslationMap
{
    private Map<String, Translation> translations = new HashMap<String, Translation>();

    
    {
        TranslationHashMap de = new TranslationHashMap();
        translations.put("de", de);
        de.put("left", "links");
        de.put("right", "rechts");
        de.put("continue", "geradeaus");
        de.put("continue onto %s", "geradeaus auf %s");
        de.put("turn %s", "%s abbiegen");
        de.put("turn %s onto %s", "%s abbiegen auf %s");

        TranslationHashMap en = new TranslationHashMap()
        {
            @Override
            public String tr( String key, Object... params )
            {
                return String.format(key, params);
            }
        };
        translations.put("en", en);
    }

    public Translation get( String language )
    {
        return translations.get(language);
    }

    public static interface Translation
    {
        String tr( String key, Object... params );
    }

    public static class TranslationHashMap implements Translation
    {
        Map<String, String> map = new HashMap<String, String>();

        @Override
        public String tr( String key, Object... params )
        {
            String val = map.get(key);
            if (Helper.isEmpty(val))
            {
                return key;
            }
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
    }

    @Override
    public String toString()
    {
        return translations.toString();
    }        
}
