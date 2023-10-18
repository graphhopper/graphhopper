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

package com.graphhopper.http;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.util.exceptions.GHException;

import java.util.List;

public class JsonErrorEntity {

    private final List<String> errors;

    public JsonErrorEntity(List<String> t) {
        this.errors = t;
    }

    @JsonValue
    ObjectNode jsonErrorResponse() {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        json.put("message", errors.get(0));
        ArrayNode errorHintList = json.putArray("hints");
        for (String t : errors) {
            ObjectNode error = errorHintList.addObject();
            error.put("message", t);
        }
        return json;
    }
}
