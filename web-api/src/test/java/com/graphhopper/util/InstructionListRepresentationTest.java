package com.graphhopper.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.jackson.Jackson;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

import static io.dropwizard.testing.FixtureHelpers.fixture;
import static org.junit.Assert.assertEquals;

public class InstructionListRepresentationTest {

    @Test
    public void testRoundaboutJsonIntegrity() throws IOException {
        ObjectMapper objectMapper = Jackson.newObjectMapper();
        InstructionList il = new InstructionList(usTR);

        PointList pl = new PointList();
        pl.add(52.514, 13.349);
        pl.add(52.5135, 13.35);
        pl.add(52.514, 13.351);
        RoundaboutInstruction instr = new RoundaboutInstruction(Instruction.USE_ROUNDABOUT, "streetname",
                new InstructionAnnotation(0, ""), pl)
                .setDirOfRotation(-0.1)
                .setRadian(-Math.PI + 1)
                .setExitNumber(2)
                .setExited();
        il.add(instr);
        assertEquals(objectMapper.readTree(fixture("fixtures/roundabout1.json")), objectMapper.readTree(objectMapper.writeValueAsString(il)));
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
        RoundaboutInstruction instr = new RoundaboutInstruction(Instruction.USE_ROUNDABOUT, "streetname",
                new InstructionAnnotation(0, ""), pl)
                .setRadian(-Math.PI + 1)
                .setExitNumber(2)
                .setExited();
        il.add(instr);
        assertEquals(objectMapper.readTree(fixture("fixtures/roundabout2.json")),objectMapper.readTree(objectMapper.writeValueAsString(il)));
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
