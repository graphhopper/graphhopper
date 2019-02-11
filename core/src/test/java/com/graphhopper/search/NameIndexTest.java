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
package com.graphhopper.search;

import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.Helper;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Peter Karich
 */
public class NameIndexTest {
    @Test
    public void testNoErrorOnLargeName() {
        NameIndex index = new NameIndex(new RAMDirectory()).create(1000);
        // 127 => bytes.length == 254
        String str = "";
        for (int i = 0; i < 127; i++) {
            str += "ß";
        }
        long result = index.put(str);
        assertEquals(127, index.get(result).length());
    }

    @Test
    public void testPut() {
        NameIndex index = new NameIndex(new RAMDirectory()).create(1000);
        long result = index.put("Something Streetä");
        assertEquals("Something Streetä", index.get(result));

        long existing = index.put("Something Streetä");
        assertEquals(result, existing);

        result = index.put("testing");
        assertEquals("testing", index.get(result));

        assertEquals(0, index.put(""));
        assertEquals(0, index.put(null));
        assertEquals("", index.get(0));
        index.close();
    }

    @Test
    public void testCreate() {
        NameIndex index = new NameIndex(new RAMDirectory()).create(1000);
        String str1 = "nice";
        long pointer1 = index.put(str1);

        String str2 = "nice work äöß";
        long pointer2 = index.put(str2);

        assertEquals(str2, index.get(pointer2));
        assertEquals(str1, index.get(pointer1));
        index.close();
    }

    @Test
    public void testTooLongNameNoError() {
        NameIndex index = new NameIndex(new RAMDirectory()).create(1000);
        // WTH are they doing in OSM? There are exactly two names in the full planet export which violates this limitation!
        index.put("Бухарестская улица (http://ru.wikipedia.org/wiki/%D0%91%D1%83%D1%85%D0%B0%D1%80%D0%B5%D1%81%D1%82%D1%81%D0%BA%D0%B0%D1%8F_%D1%83%D0%BB%D0%B8%D1%86%D0%B0_(%D0%A1%D0%B0%D0%BD%D0%BA%D1%82-%D0%9F%D0%B5%D1%82%D0%B5%D1%80%D0%B1%D1%83%D1%80%D0%B3))");

        String str = "sdfsdfds";
        for (int i = 0; i < 256 * 3; i++) {
            str += "Б";
        }
        index.put(str);
        index.close();
    }

    @Test
    public void testFlush() {
        String location = "./target/nameindex-store";
        Helper.removeDir(new File(location));

        NameIndex index = new NameIndex(new RAMDirectory(location, true).create()).create(1000);
        long pointer = index.put("test");
        index.flush();
        index.close();

        index = new NameIndex(new RAMDirectory(location, true));
        assertTrue(index.loadExisting());
        assertEquals("test", index.get(pointer));
        // make sure bytePointer is correctly set after loadExisting
        long newPointer = index.put("testing");
        assertEquals(newPointer + ">" + pointer, pointer + "test".getBytes().length + 1, newPointer);
        index.close();

        Helper.removeDir(new File(location));
    }
}
