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

import com.graphhopper.util.EdgeIteratorState;

/**
 * Return a String value from the key-values
 *
 * @author Robin Boldt
 */
public class KVStringDetails extends AbstractPathDetailsBuilder {

    private final String key;
    private String curString;

    public KVStringDetails(String name, String key) {
        super(name);
        this.key = key;
    }

    @Override
    public boolean isEdgeDifferentToLastEdge(EdgeIteratorState edge) {
        if (curString == null) {
            curString = (String) edge.getValue(key);
            return true;
        }
        String val = (String) edge.getValue(key);
        if (!curString.equals(val)) {
            curString = val;
            return true;
        }
        return false;
    }

    @Override
    public Object getCurrentValue() {
        return this.curString;
    }
}
