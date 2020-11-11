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
package com.graphhopper.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.graphhopper.farmy.IdentifiedGHPoint3D;

import java.io.IOException;
import java.util.HashMap;

;

class IdentifiedGHPointSerializer extends JsonSerializer<IdentifiedGHPoint3D> {

    @Override
    public void serialize(IdentifiedGHPoint3D ghPoint, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException, JsonProcessingException {
        jsonGenerator.writeStartObject();
        HashMap<String, Object> map = ghPoint.toGeoJsonWithId();
        for (Object key : map.keySet()) {
            jsonGenerator.writeStringField((String) key, (String) map.get(key));
        }
        jsonGenerator.writeEndObject();
    }
}
