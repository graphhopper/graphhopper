package com.graphhopper.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

public class InstructionListRepresentationTest {

    @Mock
    Translation usTR;

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Test
    public void testRoundaboutJsonIntegrity() {
        when(usTR.tr("roundabout_exit_onto", 2, "streetname")).thenReturn("At roundabout, take exit 2 onto streetname");
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
        when(usTR.tr("roundabout_exit_onto", 2, "streetname")).thenReturn("At roundabout, take exit 2 onto streetname");
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


}
