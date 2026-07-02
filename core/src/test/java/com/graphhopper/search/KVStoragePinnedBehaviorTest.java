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

import com.graphhopper.search.KVStorage.KValue;
import com.graphhopper.storage.DAType;
import com.graphhopper.storage.GHDirectory;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins behavior discovered during the Kotlin conversion, verified against the pre-migration
 * java implementation. See docs/pinned-behavior.md.
 *
 * KVStorage.get() skips non-matching dynamic-length values with '1 + b & 0xFF' which parses as
 * '(1 + b) & 0xFF' - for a stored byte[] of exactly 255 bytes the pointer advances by 0 and
 * parsing derails. Strings are capped at 250 bytes by cutString, so only byte[] values of
 * length 251-255 can trigger it.
 */
public class KVStoragePinnedBehaviorTest {

    private long addBlobAndName(KVStorage kv, int blobLen) {
        byte[] big = new byte[blobLen];
        Arrays.fill(big, (byte) 7);
        Map<String, KValue> m = new LinkedHashMap<>();
        m.put("blob", new KValue(big));
        m.put("name", new KValue("hello"));
        return kv.add(m);
    }

    @Test
    public void valuesUpTo250BytesRoundTrip() {
        KVStorage kv = new KVStorage(new GHDirectory("", DAType.RAM), true).create(1000);
        long p = addBlobAndName(kv, 250);
        assertEquals("hello", kv.get(p, "name", false));
        assertEquals(250, ((byte[]) kv.get(p, "blob", false)).length);
    }

    @Test
    public void keyAfter255ByteValueDerails() {
        KVStorage kv = new KVStorage(new GHDirectory("", DAType.RAM), true).create(1000);
        long p = addBlobAndName(kv, 255);
        // the length-skip wraps to 0 and get() misparses - like the original java version:
        // AssertionError under -ea (tests), IndexOutOfBoundsException without assertions
        AssertionError e = assertThrows(AssertionError.class, () -> kv.get(p, "name", false));
        assertTrue(e.getMessage().startsWith("invalid key index"));
    }
}
