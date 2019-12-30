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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.util.CmdArgs;

import java.io.IOException;
import java.util.Map;

public class CmdArgsDeserializer extends JsonDeserializer<CmdArgs> {

    private static final TypeReference<Map<String,String>> MAP_STRING_STRING
            = new TypeReference<Map<String,String>>() {};

    @Override
    public CmdArgs deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        jsonParser.setCodec(new ObjectMapper());
        Map<String, String> args = jsonParser.readValueAs(MAP_STRING_STRING);
        return new CmdArgs(args);
    }
}
