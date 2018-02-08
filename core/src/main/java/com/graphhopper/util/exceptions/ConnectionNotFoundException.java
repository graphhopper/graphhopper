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
package com.graphhopper.util.exceptions;

import java.util.HashMap;
import java.util.Map;

/**
 * If a route cannot be found due to disconnected graphs.
 *
 * @author Peter Karich
 */
public class ConnectionNotFoundException extends PathNotFoundException {

    public static final String DIFFERENT_SUBNETWORKS = "different_subnetworks";

    public ConnectionNotFoundException(String var1, Map<String, Object> details) {
        super(var1, details);
    }

    /**
     * You can add a reason for this exception, to add more details why this happened.
     * The reason will be put to the details map with the key "reason". If there was a value set for reason it will
     * be overwritten.
     */
    public ConnectionNotFoundException(String var1, Map<String, Object> details, String reason) {
        super(var1, details);

        if (details == null) {
            details = new HashMap<>();
        }
        details.put("reason", reason);
    }
}
