package com.graphhopper.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.*;

public class InstructionListRepresentationTest {

    @Test
    public void testRoundaboutJsonIntegrity() {
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

        Map<String, Object> json = il.createJson().get(0);
        // assert that all information is present in map for JSON
        assertEquals("At roundabout, take exit 2 onto streetname", json.get("text").toString());
        assertEquals(-1, (Double) json.get("turn_angle"), 0.01);
        assertEquals("2", json.get("exit_number").toString());
        // assert that a valid JSON object can be written
        assertNotNull(write(json));
    }

    private String write(Map<String, Object> json) {
        try {
            return new ObjectMapper().writeValueAsString(json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    // Roundabout with unknown dir of rotation
    @Test
    public void testRoundaboutJsonNaN() {
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

        Map<String, Object> json = il.createJson().get(0);
        assertEquals("At roundabout, take exit 2 onto streetname", json.get("text").toString());
        assertNull(json.get("turn_angle"));
        // assert that a valid JSON object can be written
        assertNotNull(write(json));
    }

    static Translation usTR = new Translation() {
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
