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

import com.graphhopper.util.TranslationMap.Translation;
import java.util.Locale;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class TranslationMapTest
{
    // use a static singleton to parse the I18N files only once per test execution
    public final static TranslationMap SINGLETON = new TranslationMap().doImport();

    @Test
    public void testToString()
    {
        Translation enMap = SINGLETON.getWithFallBack(Locale.UK);
        assertEquals("continue onto blp street", enMap.tr("continue_onto", "blp street"));

        Translation deMap = SINGLETON.getWithFallBack(Locale.GERMANY);
        assertEquals("Zu Fuß", deMap.tr("web.FOOT"));

        Translation ruMap = SINGLETON.getWithFallBack(new Locale("ru"));
        assertEquals("Пешком", ruMap.tr("web.FOOT"));

        deMap = SINGLETON.get("de_DE");
        assertEquals("Zu Fuß", deMap.tr("web.FOOT"));

        deMap = SINGLETON.get("de");
        assertEquals("Zu Fuß", deMap.tr("web.FOOT"));

        deMap = SINGLETON.get("de_AT");
        assertEquals("Zu Fuß", deMap.tr("web.FOOT"));
    }
}
