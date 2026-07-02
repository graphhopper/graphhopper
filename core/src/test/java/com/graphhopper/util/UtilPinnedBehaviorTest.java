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

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pins behavior discovered during the Kotlin conversion, verified against the pre-migration
 * java implementation. See docs/pinned-behavior.md.
 */
public class UtilPinnedBehaviorTest {

    @Test
    public void toBitStringTruncatesShiftedBytes() {
        // the shift-and-narrow per bit must not leak into neighbor bytes; little-endian
        // ordering reverses the byte order
        assertEquals("0000111110101010", BitUtil.LITTLE.toBitString(new byte[]{(byte) 0xAA, 0x0F}));
    }

    @Test
    public void nanAzimuthThrows() {
        // Double.compare-based checks make NaN throw - a plain '<'/'>' comparison would
        // silently accept NaN
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new AngleCalc().convertAzimuth2xaxisAngle(Double.NaN));
        assertEquals("Azimuth NaN must be in (0, 360)", e.getMessage());
    }

    @Test
    public void azimuthConversionReference() {
        assertEquals(0.0, new AngleCalc().convertAzimuth2xaxisAngle(90), 1e-12);
    }

    @Test
    public void unzipperProgressAccumulatesAcrossEntries() throws Exception {
        // the progress listener reports the total number of (compression-scaled) bytes read so
        // far, accumulated over ALL entries (never reset per entry); directories and empty
        // files report nothing. test.zip: file1 (5 bytes), "file2 bäh" (5), "folder1/file 3"
        // (0), "folder1/folder 3/file4" (4), all stored (factor 1)
        String to = "./target/tmp/unzip-progress";
        Helper.removeDir(new File(to));
        List<Long> progress = new ArrayList<>();
        new Unzipper().unzip(getClass().getResourceAsStream("test.zip"), new File(to), progress::add);
        assertEquals(List.of(5L, 10L, 14L), progress);
        Helper.removeDir(new File(to));
    }

    @Test
    public void countOccurenceUsesJavaSplitSemantics() {
        assertEquals(0, TranslationMap.countOccurence(null, "\\%"));
        assertEquals(0, TranslationMap.countOccurence("", "\\%"));
        // Helper.isEmpty trims, so a whitespace-only phrase never reaches the split
        assertEquals(0, TranslationMap.countOccurence("   ", "\\%"));
        // java.lang.String.split drops trailing empty strings ...
        assertEquals(2, TranslationMap.countOccurence("a%b%", "\\%"));
        // ... but keeps leading empty strings
        assertEquals(2, TranslationMap.countOccurence("%a", "\\%"));
        assertEquals(3, TranslationMap.countOccurence("hello %1$s, %2$s", "\\%"));
    }
}
