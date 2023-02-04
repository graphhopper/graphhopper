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

import com.graphhopper.core.util.Translation;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Peter Karich
 */
public class TranslationMapTest {
    // use a static singleton to parse the I18N files only once per test execution
    public final static TranslationMap SINGLETON = new TranslationMap().doImport();

    @Test
    public void testToString() {
        Translation enMap = SINGLETON.getWithFallBack(Locale.UK);
        assertEquals("continue onto blp street", enMap.tr("continue_onto", "blp street"));

        Translation trMap = SINGLETON.getWithFallBack(Locale.GERMANY);
        assertEquals("Zu Fuß", trMap.tr("web.FOOT"));

        Translation ruMap = SINGLETON.getWithFallBack(new Locale("ru"));
        assertEquals("Пешком", ruMap.tr("web.FOOT"));

        Translation zhMap = SINGLETON.getWithFallBack(new Locale("vi", "VI"));
        assertEquals("Đi bộ", zhMap.tr("web.FOOT"));

        trMap = SINGLETON.get("de_DE");
        assertEquals("Zu Fuß", trMap.tr("web.FOOT"));

        trMap = SINGLETON.get("de");
        assertEquals("Zu Fuß", trMap.tr("web.FOOT"));

        trMap = SINGLETON.get("de_AT");
        assertEquals("Zu Fuß", trMap.tr("web.FOOT"));

        trMap = SINGLETON.get("he");
        assertEquals("רגל", trMap.tr("web.FOOT"));
        trMap = SINGLETON.get("iw");
        assertEquals("רגל", trMap.tr("web.FOOT"));

        // Indonesian
        // for jdk17 and later "id" is returned, before "in" was returned
        String lang = SINGLETON.get("id").getLanguage();
        assertTrue(Arrays.asList("id", "in").contains(lang));
        assertEquals(lang, SINGLETON.get("in").getLanguage());
        assertEquals(lang, SINGLETON.get("in_ID").getLanguage());

        // Vietnamese
        assertEquals("vi", SINGLETON.get("vi").getLanguage());
        assertEquals("vi", SINGLETON.get("vi_VN").getLanguage());
    }

    @Test
    public void testToRoundaboutString() {
        Translation ptMap = SINGLETON.get("pt");
        assertTrue(ptMap.tr("roundabout_exit_onto", "1", "somestreet").contains("somestreet"));
    }
}
