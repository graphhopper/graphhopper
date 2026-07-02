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
package com.graphhopper.coll;

import androidx.collection.MutableIntIntMap;
import androidx.collection.MutableIntList;
import androidx.collection.MutableIntSet;
import androidx.collection.MutableLongObjectMap;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * GraphHopper relies on reproducible iteration order of its hash containers across JVM runs
 * (stored graphs and several tests depend on it). With hppc this was pinned via
 * HashOrderMixing.constant(...). androidx.collection uses a fixed hash constant without a
 * per-instance or per-run seed, so iteration order is deterministic - but that is an
 * implementation property of the pinned version, NOT an API guarantee.
 *
 * This canary asserts a known iteration order. If it fails after a version upgrade of
 * androidx.collection, iteration-order dependent results (e.g. stored graph reproducibility)
 * must be re-baselined deliberately - do NOT just fix the expected values without checking
 * the consequences, see KOTLIN_MIGRATION.md.
 */
public class AndroidxCollectionDeterminismTest {

    @Test
    public void intSetIterationOrderIsStable() {
        // observed with androidx.collection 1.6.0 - a changed order after an upgrade is not a
        // test bug, it invalidates iteration-order dependent results, see class javadoc
        List<Integer> expected = List.of(0, 9000027, 17000051, 8000024, 18000054, 7000021,
                16000048, 15000045, 6000018, 5000015, 14000042, 4000012, 13000039, 12000036,
                3000009, 2000006, 11000033, 10000030, 1000003, 19000057);
        assertEquals(expected, intSetOrder());
        assertEquals(expected, intSetOrder(), "same insertions must yield the same iteration order");
    }

    @Test
    public void intIntMapIterationOrderIsStable() {
        List<Integer> expected = List.of(0, 155, 310, 465, 124, 279, 434, 589, 93, 248, 403,
                558, 62, 217, 372, 527, 31, 186, 341, 496);
        assertEquals(expected, intIntMapKeyOrder());
        assertEquals(expected, intIntMapKeyOrder(), "same insertions must yield the same iteration order");
    }

    @Test
    public void longObjectMapIterationOrderIsStable() {
        List<Long> expected = List.of(0L, 864197523L, 1728395046L, 493827156L, 1358024679L,
                2222222202L, 123456789L, 987654312L, 1851851835L, 617283945L, 1481481468L,
                2345678991L, 246913578L, 1111111101L, 1975308624L, 740740734L, 1604938257L,
                370370367L, 1234567890L, 2098765413L);
        assertEquals(expected, longObjectMapKeyOrder());
        assertEquals(expected, longObjectMapKeyOrder(), "same insertions must yield the same iteration order");
    }

    @Test
    public void intListKeepsInsertionOrder() {
        MutableIntList list = new MutableIntList();
        for (int i = 10; i > 0; i--)
            list.add(i * 7);
        List<Integer> result = new ArrayList<>();
        list.forEach(i -> {
            result.add(i);
            return kotlin.Unit.INSTANCE;
        });
        assertEquals(List.of(70, 63, 56, 49, 42, 35, 28, 21, 14, 7), result);
    }

    private List<Integer> intSetOrder() {
        MutableIntSet set = new MutableIntSet();
        for (int i = 0; i < 20; i++)
            set.add(i * 1_000_003);
        List<Integer> order = new ArrayList<>();
        set.forEach(i -> {
            order.add(i);
            return kotlin.Unit.INSTANCE;
        });
        return order;
    }

    private List<Integer> intIntMapKeyOrder() {
        MutableIntIntMap map = new MutableIntIntMap();
        for (int i = 0; i < 20; i++)
            map.put(i * 31, i);
        List<Integer> order = new ArrayList<>();
        map.forEachKey(k -> {
            order.add(k);
            return kotlin.Unit.INSTANCE;
        });
        return order;
    }

    private List<Long> longObjectMapKeyOrder() {
        MutableLongObjectMap<String> map = new MutableLongObjectMap<>();
        for (long i = 0; i < 20; i++)
            map.put(i * 123_456_789L, "v" + i);
        List<Long> order = new ArrayList<>();
        map.forEachKey(k -> {
            order.add(k);
            return kotlin.Unit.INSTANCE;
        });
        return order;
    }
}
