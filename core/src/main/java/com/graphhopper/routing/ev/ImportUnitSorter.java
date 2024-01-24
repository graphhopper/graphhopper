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

import java.util.*;

// topological sort with a depth first search
public class ImportUnitSorter {
    Set<String> permanentMarked = new HashSet<>();
    Set<String> temporaryMarked = new HashSet<>();
    List<String> result = new ArrayList<>();
    final Map<String, ImportUnit> map;

    public ImportUnitSorter(Map<String, ImportUnit> map) {
        this.map = map;
    }

    public List<String> sort() {
        for (String strN : map.keySet()) {
            visit(strN);
        }
        return result;
    }

    private void visit(String strN) {
        if (permanentMarked.contains(strN)) return;
        ImportUnit ImportUnit = map.get(strN);
        if (ImportUnit == null)
            throw new IllegalArgumentException("cannot find reg " + strN);
        if (temporaryMarked.contains(strN))
            throw new IllegalArgumentException("cyclic required parsers are not allowed: " + ImportUnit + " " + ImportUnit.getRequiredImportUnits());

        temporaryMarked.add(strN);
        for (String strM : ImportUnit.getRequiredImportUnits()) {
            visit(strM);
        }
        temporaryMarked.remove(strN);
        permanentMarked.add(strN);
        result.add(strN);
    }
}
