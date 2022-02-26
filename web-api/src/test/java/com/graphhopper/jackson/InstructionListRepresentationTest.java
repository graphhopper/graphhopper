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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.util.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InstructionListRepresentationTest {

    @Test
    public void testRoundaboutJsonIntegrity() throws IOException {
        ObjectMapper objectMapper = Jackson.newObjectMapper();
        InstructionList il = new InstructionList(usTR);

        PointList pl = new PointList();
        pl.add(52.514, 13.349);
        pl.add(52.5135, 13.35);
        pl.add(52.514, 13.351);
        RoundaboutInstruction instr = new RoundaboutInstruction(Instruction.USE_ROUNDABOUT, "streetname", pl)
                .setDirOfRotation(-0.1)
                .setRadian(-Math.PI + 1)
                .setExitNumber(2)
                .setExited();
        il.add(instr);
        assertEquals(objectMapper.readTree(getClass().getClassLoader().getResourceAsStream("fixtures/roundabout1.json")).toString(), objectMapper.valueToTree(il).toString());
    }


    // Roundabout with unknown dir of rotation
    @Test
    public void testRoundaboutJsonNaN() throws IOException {
        ObjectMapper objectMapper = Jackson.newObjectMapper();
        InstructionList il = new InstructionList(usTR);

        PointList pl = new PointList();
        pl.add(52.514, 13.349);
        pl.add(52.5135, 13.35);
        pl.add(52.514, 13.351);
        RoundaboutInstruction instr = new RoundaboutInstruction(Instruction.USE_ROUNDABOUT, "streetname", pl)
                .setRadian(-Math.PI + 1)
                .setExitNumber(2)
                .setExited();
        il.add(instr);
        assertEquals(objectMapper.readTree(getClass().getClassLoader().getResourceAsStream("fixtures/roundabout2.json")).toString(), objectMapper.valueToTree(il).toString());
    }

    private static Translation usTR = new Translation() {
        @Override
        public String tr(String key, Object... params) {
            if (key.equals("roundabout_exit_onto"))
                return "At roundabout, take exit 2 onto streetname";
            return key;
        }

        @Override
        public Map<String, String> asMap() {
            return Collections.emptyMap();
        }

        @Override
        public Locale getLocale() {
            return Locale.US;
        }

        @Override
        public String getLanguage() {
            return "en";
        }
    };
}
