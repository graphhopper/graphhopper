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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.ResponsePath;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RouteResourceRepresentationTest {

    @Test
    public void testUnknownInstructionSign() throws IOException {
        // Modified the sign though
        ObjectMapper objectMapper = Jackson.newObjectMapper();
        JsonNode json = objectMapper.readTree("{\"instructions\":[{\"distance\":1.073,\"sign\":741,\"interval\":[0,1],\"text\":\"Continue onto A 81\",\"time\":32,\"street_name\":\"A 81\"},{\"distance\":0,\"sign\":4,\"interval\":[1,1],\"text\":\"Finish!\",\"time\":0,\"street_name\":\"\"}],\"descend\":0,\"ascend\":0,\"distance\":1.073,\"bbox\":[8.676286,48.354446,8.676297,48.354453],\"weight\":0.032179,\"time\":32,\"points_encoded\":true,\"points\":\"gfcfHwq}s@}c~AAA?\",\"snapped_waypoints\":\"gfcfHwq}s@}c~AAA?\"}");
        ResponsePath responsePath = ResponsePathDeserializer.createResponsePath(objectMapper, json, true, true);

        assertEquals(741, responsePath.getInstructions().get(0).getSign());
        assertEquals("Continue onto A 81", responsePath.getInstructions().get(0).getName());
    }
}
