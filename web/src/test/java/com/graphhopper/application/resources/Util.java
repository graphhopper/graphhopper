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

package com.graphhopper.application.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.graphhopper.util.BodyAndStatus;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;

public class Util {
    public static BodyAndStatus getWithStatus(WebTarget webTarget) {
        try (Response response = webTarget.request().get()) {
            return new BodyAndStatus(response.readEntity(JsonNode.class), response.getStatus());
        }
    }

    public static BodyAndStatus postWithStatus(WebTarget webTarget, String json) {
        try (Response response = webTarget.request().post(Entity.json(json))) {
            return new BodyAndStatus(response.readEntity(JsonNode.class), response.getStatus());
        }
    }

}
