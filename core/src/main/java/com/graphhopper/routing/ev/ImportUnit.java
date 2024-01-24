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

import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.util.PMap;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

public class ImportUnit {
    private final String name;
    private final Function<PMap, EncodedValue> createEncodedValue;
    private final BiFunction<EncodedValueLookup, PMap, TagParser> createTagParser;
    private final List<String> requiredImportUnits;

    public static ImportUnit create(String name, Function<PMap, EncodedValue> createEncodedValue, BiFunction<EncodedValueLookup, PMap, TagParser> createTagParser, String... requiredImportUnits) {
        return new ImportUnit(name, createEncodedValue, createTagParser, List.of(requiredImportUnits));
    }

    private ImportUnit(String name, Function<PMap, EncodedValue> createEncodedValue, BiFunction<EncodedValueLookup, PMap, TagParser> createTagParser, List<String> requiredImportUnits) {
        this.name = name;
        this.createEncodedValue = createEncodedValue;
        this.createTagParser = createTagParser;
        this.requiredImportUnits = requiredImportUnits;
    }

    public Function<PMap, EncodedValue> getCreateEncodedValue() {
        return createEncodedValue;
    }

    public BiFunction<EncodedValueLookup, PMap, TagParser> getCreateTagParser() {
        return createTagParser;
    }

    public List<String> getRequiredImportUnits() {
        return requiredImportUnits;
    }

    @Override
    public String toString() {
        return "ImportUnit: " + name + " (requires: " + requiredImportUnits + ")";
    }
}
