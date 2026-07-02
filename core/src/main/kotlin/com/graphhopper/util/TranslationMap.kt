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
package com.graphhopper.util

import com.graphhopper.util.Helper.UTF_CS
import com.graphhopper.util.Helper.getLocale
import com.graphhopper.util.Helper.isEmpty
import com.graphhopper.util.Helper.readFile
import com.graphhopper.util.Helper.toLowerCase
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Arrays
import java.util.Locale

/**
 * A class which manages the translations in-memory. See here for more information:
 * ./docs/core/translations.md
 *
 * @author Peter Karich
 */
class TranslationMap {
    private val translations = HashMap<String, Translation>()

    /**
     * This loads the translation files from the specified folder.
     */
    fun doImport(folder: File): TranslationMap {
        try {
            for (locale in LOCALES) {
                val trMap = TranslationHashMap(getLocale(locale))
                trMap.doImport(FileInputStream(File(folder, "$locale.txt")))
                add(trMap)
            }
            postImportHook()
            return this
        } catch (ex: Exception) {
            throw RuntimeException(ex)
        }
    }

    /**
     * This loads the translation files from classpath.
     */
    fun doImport(): TranslationMap {
        try {
            for (locale in LOCALES) {
                val trMap = TranslationHashMap(getLocale(locale))
                trMap.doImport(TranslationMap::class.java.getResourceAsStream("$locale.txt"))
                add(trMap)
            }
            postImportHook()
            return this
        } catch (ex: Exception) {
            throw RuntimeException(ex)
        }
    }

    fun add(tr: Translation) {
        val locale = tr.locale
        translations[locale.toString()] = tr
        if (!locale.country.isEmpty() && !translations.containsKey(tr.language))
            translations[tr.language] = tr

        // Hebrew locale was "iw" in old JDKs but is now he
        // required in old JDKs:
        if ("iw" == locale.language) translations["he"] = tr
        // required since jdk17 to still provide translation for "iw":
        if ("he" == locale.language) translations["iw"] = tr

        // Indonesia locale was "in_ID" in old JDKs but is now id_ID
        // required in old JDKs:
        if ("in" == locale.language) translations["id"] = tr
        // required since jdk17 to still provide translation for "in":
        if ("id" == locale.language) translations["in"] = tr
        // Indian locales are: en-IN and hi-IN and are not overwritten by that
    }

    /**
     * Returns the Translation object for the specified locale and falls back to English if the
     * locale was not found.
     */
    fun getWithFallBack(locale: Locale): Translation? {
        var tr = get(locale.toString())
        if (tr == null) {
            tr = get(locale.language)
            if (tr == null)
                tr = get("en")
        }
        return tr
    }

    /**
     * Returns the Translation object for the specified locale and returns null if not found.
     */
    fun get(locale: String): Translation? {
        val key = locale.replace("-", "_")
        var tr = translations[key]
        if (key.contains("_") && tr == null)
            tr = translations[key.substring(0, 2)]

        return tr
    }

    /**
     * This method does some checks and fills missing translation from en
     */
    private fun postImportHook() {
        val enMap = get("en")!!.asMap()
        val sb = StringBuilder()
        for (tr in translations.values) {
            val trMap = tr.asMap()
            for (enEntry in enMap.entries) {
                val value = trMap[enEntry.key]
                if (isEmpty(value)) {
                    trMap[enEntry.key] = enEntry.value
                    continue
                }

                val expectedCount = countOccurence(enEntry.value, "\\%")
                if (expectedCount != countOccurence(value, "\\%")) {
                    sb.append(tr.locale).append(" - error in ").
                        append(enEntry.key).append("->").
                        append(value).append("\n")
                } else {
                    // try if formatting works, many times e.g. '%1$' instead of '%1$s'
                    val strs = arrayOfNulls<String>(expectedCount)
                    Arrays.fill(strs, "tmp")
                    try {
                        String.format(Locale.ROOT, value!!, *strs)
                    } catch (ex: Exception) {
                        sb.append(tr.locale).append(" - error ").append(ex.message).append("in ").
                            append(enEntry.key).append("->").
                            append(value).append("\n")
                    }
                }
            }
        }

        if (sb.length > 0) {
            println(sb)
            throw IllegalStateException(sb.toString())
        }
    }

    override fun toString(): String = translations.toString()

    class TranslationHashMap(private val locale: Locale) : Translation {
        private val map = HashMap<String, String>()

        fun clear() {
            map.clear()
        }

        override fun getLocale(): Locale = locale

        override fun getLanguage(): String = locale.language

        override fun tr(key: String, vararg params: Any?): String {
            val value = map[toLowerCase(key)]
            if (isEmpty(value))
                return key

            return String.format(Locale.ROOT, value!!, *params)
        }

        fun put(key: String, value: String): TranslationHashMap {
            val existing = map.put(toLowerCase(key), value)
            if (existing != null)
                throw IllegalStateException("Cannot overwrite key $key with $value, was: $existing")
            return this
        }

        override fun toString(): String = map.toString()

        override fun asMap(): MutableMap<String, String> = map

        fun doImport(inputStream: InputStream?): TranslationHashMap {
            if (inputStream == null)
                throw IllegalStateException("No input stream found in class path!?")
            try {
                for (line in readFile(InputStreamReader(inputStream, UTF_CS))) {
                    if (line.isEmpty() || line.startsWith("//") || line.startsWith("#"))
                        continue

                    val index = line.indexOf('=')
                    if (index < 0)
                        continue
                    val key = line.substring(0, index)
                    if (key.isEmpty())
                        throw IllegalStateException("No key provided:$line")

                    val value = line.substring(index + 1)
                    if (!value.isEmpty())
                        put(key, value)
                }
            } catch (ex: IOException) {
                throw RuntimeException(ex)
            }
            return this
        }
    }

    companion object {
        // ISO codes (639-1), use 'en_US' as reference
        private val LOCALES = Arrays.asList("ar", "ast", "bg", "bn_BN", "ca",
            "cs_CZ", "da_DK", "de_DE", "el",
            /* default for en -> must come first: */ "en_US", "en_AU",
            "eo", "es", "fa", "fil", "fi",
            "fr_FR", "fr_CH", "gl", "he", "hr_HR", "hsb", "hu_HU", "in_ID", "it", "ja", "kab_DZ", "ko",
            "kz", "lt_LT", "mn", "nb_NO", "ne", "nl", "pl_PL", "pt_BR", "pt_PT", "ro", "ru", "sk",
            "sl_SI", "sr_RS", "sv_SE", "tr", "uk", "uz", "vi_VN", "zh_CN", "zh_HK", "zh_TW")

        @JvmStatic
        fun countOccurence(phrase: String?, splitter: String): Int {
            if (isEmpty(phrase))
                return 0
            // use java.lang.String.split to keep Java's exact trailing-empty-string semantics
            return (phrase!!.trim { it <= ' ' } as java.lang.String).split(splitter).size
        }
    }
}
