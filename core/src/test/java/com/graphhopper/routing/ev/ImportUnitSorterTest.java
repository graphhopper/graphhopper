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

package com.graphhopper.routing.ev;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ImportUnitSorterTest {

    @Test
    public void simple() {
        ImportUnit a = create("a");
        ImportUnit b = create("b", "a");
        ImportUnit c = create("c", "b");

        Map<String, ImportUnit> importUnits = new HashMap<>();
        importUnits.put("a", a);
        importUnits.put("b", b);
        importUnits.put("c", c);

        ImportUnitSorter sorter = new ImportUnitSorter(importUnits);
        List<String> sorted = sorter.sort();

        assertEquals(importUnits.size(), sorted.size());
        assertEquals(List.of("a", "b", "c"), sorted);
    }

    @Test
    public void cycle() {
        ImportUnit a = create("a", "b");
        ImportUnit b = create("b", "a");

        Map<String, ImportUnit> importUnits = new HashMap<>();
        importUnits.put("a", a);
        importUnits.put("b", b);

        ImportUnitSorter sorter = new ImportUnitSorter(importUnits);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, sorter::sort);
        assertTrue(e.getMessage().contains("import units with cyclic dependencies are not allowed"));
    }

    private ImportUnit create(String name, String... required) {
        return ImportUnit.create(name, null, null, required);
    }

}
