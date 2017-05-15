/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper GmbH licenses this file to you under the Apache License, 
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
import java.util.*;
import java.util.Map.Entry;

/**
 * A class which manages the translations in-memory. See here for more information:
 * ./docs/core/translations.md
 * <p>
 *
 * @author Peter Karich
 */
public class TranslationMap {
    // ISO codes (639-1), use 'en_US' as reference
    private static final List<String> LOCALES = Arrays.asList("ar", "ast", "bg", "ca",
            "cs_CZ", "da_DK", "de_DE", "el", "en_US", "es", "fa", "fil", "fi",
            "fr_FR", "fr_CH", "gl", "he", "hr_HR", "hsb", "hu_HU", "it", "ja", "ko", "lt_LT", "ne",
            "nl", "pl_PL", "pt_BR", "pt_PT", "ro", "ru", "sl_SI", "sk", "sv_SE", "tr", "uk",
            "vi_VI", "zh_CN", "zh_HK");
    private final Map<String, Translation> translations = new HashMap<String, Translation>();

    public static int countOccurence(String phrase, String splitter) {
        if (Helper.isEmpty(phrase))
            return 0;
        return phrase.trim().split(splitter).length;
    }

    /**
     * This loads the translation files from the specified folder.
     */
    public TranslationMap doImport(File folder) {
        try {
            for (String locale : LOCALES) {
                TranslationHashMap trMap = new TranslationHashMap(Helper.getLocale(locale));
                trMap.doImport(new FileInputStream(new File(folder, locale + ".txt")));
                add(trMap);
            }
            postImportHook();
            return this;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * This loads the translation files from classpath.
     */
    public TranslationMap doImport() {
        try {
            for (String locale : LOCALES) {
                TranslationHashMap trMap = new TranslationHashMap(Helper.getLocale(locale));
                trMap.doImport(TranslationMap.class.getResourceAsStream(locale + ".txt"));
                add(trMap);
            }
            postImportHook();
            return this;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public void add(Translation tr) {
        Locale locale = tr.getLocale();
        translations.put(locale.toString(), tr);
        if (!locale.getCountry().isEmpty() && !translations.containsKey(tr.getLanguage()))
            translations.put(tr.getLanguage(), tr);

        // Map old Java 'standard' to latest, Java is a bit ugly here: http://stackoverflow.com/q/13974169/194609
        // Hebrew
        if ("iw".equals(locale.getLanguage()))
            translations.put("he", tr);

        // Indonesia
        if ("in".equals(locale.getLanguage()))
            translations.put("id", tr);
    }

    /**
     * Returns the Translation object for the specified locale and falls back to English if the
     * locale was not found.
     */
    public Translation getWithFallBack(Locale locale) {
        Translation tr = get(locale.toString());
        if (tr == null) {
            tr = get(locale.getLanguage());
            if (tr == null)
                tr = get("en");
        }
        return tr;
    }

    /**
     * Returns the Translation object for the specified locale and returns null if not found.
     */
    public Translation get(String locale) {
        locale = locale.replace("-", "_");
        Translation tr = translations.get(locale);
        if (locale.contains("_") && tr == null)
            tr = translations.get(locale.substring(0, 2));

        return tr;
    }

    /**
     * This method does some checks and fills missing translation from en
     */
    private void postImportHook() {
        Map<String, String> enMap = get("en").asMap();
        StringBuilder sb = new StringBuilder();
        for (Translation tr : translations.values()) {
            Map<String, String> trMap = tr.asMap();
            for (Entry<String, String> enEntry : enMap.entrySet()) {
                String value = trMap.get(enEntry.getKey());
                if (Helper.isEmpty(value)) {
                    trMap.put(enEntry.getKey(), enEntry.getValue());
                    continue;
                }

                int expectedCount = countOccurence(enEntry.getValue(), "\\%");
                if (expectedCount != countOccurence(value, "\\%")) {
                    sb.append(tr.getLocale()).append(" - error in ").
                            append(enEntry.getKey()).append("->").
                            append(value).append("\n");
                } else {
                    // try if formatting works, many times e.g. '%1$' instead of '%1$s'
                    Object[] strs = new String[expectedCount];
                    Arrays.fill(strs, "tmp");
                    try {
                        String.format(value, strs);
                    } catch (Exception ex) {
                        sb.append(tr.getLocale()).append(" - error ").append(ex.getMessage()).append("in ").
                                append(enEntry.getKey()).append("->").
                                append(value).append("\n");
                    }
                }
            }
        }

        if (sb.length() > 0) {
            System.out.println(sb);
            throw new IllegalStateException(sb.toString());
        }
    }

    @Override
    public String toString() {
        return translations.toString();
    }

    public static class TranslationHashMap implements Translation {
        final Locale locale;
        private final Map<String, String> map = new HashMap<String, String>();

        public TranslationHashMap(Locale locale) {
            this.locale = locale;
        }

        public void clear() {
            map.clear();
        }

        @Override
        public Locale getLocale() {
            return locale;
        }

        @Override
        public String getLanguage() {
            return locale.getLanguage();
        }

        @Override
        public String tr(String key, Object... params) {
            String val = map.get(key.toLowerCase());
            if (Helper.isEmpty(val))
                return key;

            return String.format(val, params);
        }

        public TranslationHashMap put(String key, String val) {
            String existing = map.put(key.toLowerCase(), val);
            if (existing != null)
                throw new IllegalStateException("Cannot overwrite key " + key + " with " + val + ", was: " + existing);
            return this;
        }

        @Override
        public String toString() {
            return map.toString();
        }

        @Override
        public Map<String, String> asMap() {
            return map;
        }

        public TranslationHashMap doImport(InputStream is) {
            if (is == null)
                throw new IllegalStateException("No input stream found in class path!?");
            try {
                for (String line : Helper.readFile(new InputStreamReader(is, Helper.UTF_CS))) {
                    if (line.isEmpty() || line.startsWith("//") || line.startsWith("#"))
                        continue;

                    int index = line.indexOf('=');
                    if (index < 0)
                        continue;
                    String key = line.substring(0, index);
                    if (key.isEmpty())
                        throw new IllegalStateException("No key provided:" + line);

                    String value = line.substring(index + 1);
                    if (!value.isEmpty())
                        put(key, value);

                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
            return this;
        }
    }
}
