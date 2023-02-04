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

import com.graphhopper.core.util.Helper;

/**
 * This enum defines the road access of an edge. Most edges are accessible from everyone and so the default value is
 * YES. But some have restrictions like "accessible only for customers" or when delivering. Unknown tags will get the
 * value OTHER. The NO value does not permit any access.
 */
public enum Hgv {
    MISSING("missing"), YES("yes"), DESIGNATED("designated"), DESTINATION("destination"),
    DELIVERY("delivery"), DISCOURAGED("discouraged"), AGRICULTURAL("agricultural"), NO("no");

    public static final String KEY = "hgv";

    private final String name;

    Hgv(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    public static Hgv find(String name) {
        if (name == null)
            return MISSING;
        try {
            return Hgv.valueOf(Helper.toUpperCase(name));
        } catch (IllegalArgumentException ex) {
            return MISSING;
        }
    }
}
