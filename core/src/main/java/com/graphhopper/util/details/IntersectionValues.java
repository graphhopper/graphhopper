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
package com.graphhopper.util.details;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class IntersectionValues {
    public int bearing;
    public boolean entry;
    public boolean in;
    public boolean out;

    /**
     *  create a List of IntersectionValues from a PathDetail
     */
    public static List<IntersectionValues> createList(Map<String, Object> intersectionMap) {
        List<IntersectionValues> list = new ArrayList<>();

        List<Integer> bearings = (List<Integer>) intersectionMap.get("bearings");
        Integer in = (Integer) intersectionMap.getOrDefault("in", -1);
        Integer out = (Integer) intersectionMap.getOrDefault("out", -1);
        List<Boolean> entry = (List<Boolean>) intersectionMap.get("entries");

        if (bearings.size() != entry.size()) {
            throw new IllegalStateException("Bearings and entry array sizes different");
        }
        int numEntries = bearings.size();

        for (int i = 0; i < numEntries; i++) {
            IntersectionValues iv = new IntersectionValues();
            iv.bearing = bearings.get(i);
            iv.entry = entry.get(i);
            iv.in = (in == i);
            iv.out = (out == i);

            list.add(iv);
        }
        return list;
    }

    /**
     * create a PathDetail from a List of IntersectionValues
     */
    public static Map<String, Object> createIntersection(List<IntersectionValues> list) {
        Map<String, Object> intersection = new HashMap<>();

        intersection.put("bearings",
                list.stream().map(x -> x.bearing).collect(Collectors.toList()));
        intersection.put("entries",
                list.stream().map(x -> x.entry).collect(Collectors.toList()));

        for (int m = 0; m < list.size(); m++) {
            IntersectionValues intersectionValues = list.get(m);
            if (intersectionValues.in) {
                intersection.put("in", m);
            }
            if (intersectionValues.out) {
                intersection.put("out", m);
            }
        }
        return intersection;
    }
}
