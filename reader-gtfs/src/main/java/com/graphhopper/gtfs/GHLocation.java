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

package com.graphhopper.gtfs;

import com.graphhopper.core.util.shapes.GHPoint;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GHLocation {

    private static final Pattern PATTERN = Pattern.compile("^Stop\\((.*)\\)$");

    public static GHLocation fromString(String s) {
        final Matcher matcher = PATTERN.matcher(s);
        if (matcher.find()) {
            return new GHStationLocation(matcher.group(1));
        } else {
            return new GHPointLocation(GHPoint.fromString(s));
        }
    }
}
