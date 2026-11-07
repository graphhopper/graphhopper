package com.graphhopper.routing;

import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.SpeedWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.search.KVStorage;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.Instruction;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.TranslationMap;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.graphhopper.util.Parameters.Details.STREET_DESTINATION;
import static com.graphhopper.util.Parameters.Details.STREET_NAME;

public class InstructionsFromEdgesTest {

    @Test
    public void testRoundaboutWithUnnamedExit() {
        BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue("car_access", true);
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("car_average_speed", 5, 5, true);
        BooleanEncodedValue roundaboutEnc = new SimpleBooleanEncodedValue("roundabout", true);
        EnumEncodedValue<RoadEnvironment> roadEnvEnc = new EnumEncodedValue<>("road_environment", RoadEnvironment.class);
        EnumEncodedValue<RoadClass> roadClassEnc = new EnumEncodedValue<>("road_class", RoadClass.class);
        BooleanEncodedValue roadClassLinkEnc = new SimpleBooleanEncodedValue("road_class_link", true);
        DecimalEncodedValue maxSpeedEnc = new DecimalEncodedValueImpl("max_speed", 7, 2, true);

        EncodingManager em = EncodingManager.start()
                .add(accessEnc).add(speedEnc).add(roundaboutEnc)
                .add(roadEnvEnc).add(roadClassEnc)
                .add(roadClassLinkEnc).add(maxSpeedEnc)
                .build();

        BaseGraph graph = new BaseGraph.Builder(em).create();
        Weighting weighting = new SpeedWeighting(speedEnc);
        graph.edge(0, 1).set(accessEnc, true).set(speedEnc, 50);
        graph.edge(1, 2).set(accessEnc, true).set(speedEnc, 30).set(roundaboutEnc, true)
                .setKeyValues(Map.of(STREET_NAME, new KVStorage.KValue("Magic Circle")));
        graph.edge(2, 3).set(accessEnc, true).set(speedEnc, 50)
                .setKeyValues(Map.of(STREET_DESTINATION, new KVStorage.KValue("Euskirchen")));

        Path path = new Path(graph);
        path.setWeight(100);
        path.setFromNode(0);
        path.setEndNode(3);
        path.getEdges().add(0);
        path.getEdges().add(1);
        path.getEdges().add(2);
        path.setFound(true);

        TranslationMap trMap = new TranslationMap().doImport();
        InstructionList instructions = InstructionsFromEdges.calcInstructions(path, graph, weighting, em, trMap.get("en_US"));
        Instruction roundaboutInstruction = instructions.get(1);
        String description = roundaboutInstruction.getTurnDescription(trMap.getWithFallBack(Locale.US));

        assertEquals("at roundabout, take exit 1 toward Euskirchen", description,
                "Unnamed Roundabout: Destination tag was ignored!");
    }

    private Instruction getForkInstruction(RoadClass mainClass, boolean mainLink, RoadClass altClass, boolean altLink, double altLat, double altLon) {
        BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue("car_access", true);
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("car_average_speed", 5, 5, true);
        BooleanEncodedValue roundaboutEnc = new SimpleBooleanEncodedValue("roundabout", true);
        EnumEncodedValue<RoadEnvironment> roadEnvEnc = new EnumEncodedValue<>("road_environment", RoadEnvironment.class);
        EnumEncodedValue<RoadClass> roadClassEnc = new EnumEncodedValue<>("road_class", RoadClass.class);
        BooleanEncodedValue roadClassLinkEnc = new SimpleBooleanEncodedValue("road_class_link", true);
        DecimalEncodedValue maxSpeedEnc = new DecimalEncodedValueImpl("max_speed", 7, 2, true);

        EncodingManager em = EncodingManager.start()
                .add(accessEnc).add(speedEnc).add(roundaboutEnc)
                .add(roadEnvEnc).add(roadClassEnc)
                .add(roadClassLinkEnc).add(maxSpeedEnc)
                .build();

        BaseGraph graph = new BaseGraph.Builder(em).create();
        Weighting weighting = new SpeedWeighting(speedEnc);

        graph.getNodeAccess().setNode(0, 50.0000, 10.0000);
        graph.getNodeAccess().setNode(1, 50.0100, 10.0000);
        graph.getNodeAccess().setNode(2, 50.0200, 10.0000);
        graph.getNodeAccess().setNode(3, altLat, altLon);

        graph.edge(0, 1).set(accessEnc, true).set(speedEnc, 100).set(roadClassEnc, mainClass).set(roadClassLinkEnc, mainLink)
                .setKeyValues(Map.of(STREET_NAME, new KVStorage.KValue("A 565")));
        graph.edge(1, 2).set(accessEnc, true).set(speedEnc, 100).set(roadClassEnc, mainClass).set(roadClassLinkEnc, mainLink)
                .setKeyValues(Map.of(STREET_NAME, new KVStorage.KValue("A 565"))); // Main path
        graph.edge(1, 3).set(accessEnc, true).set(speedEnc, 100).set(roadClassEnc, altClass).set(roadClassLinkEnc, altLink)
                .setKeyValues(Map.of(STREET_NAME, new KVStorage.KValue("A 565"))); // Alternative path

        Path path = new Path(graph);
        path.setWeight(100);
        path.setFromNode(0);
        path.setEndNode(2);
        path.getEdges().add(0);
        path.getEdges().add(1);
        path.setFound(true);

        TranslationMap trMap = new TranslationMap().doImport();
        InstructionList instructions = InstructionsFromEdges.calcInstructions(path, graph, weighting, em, trMap.get("en_US"));

        return instructions.get(1);
    }

    @Test
    public void testMotorwayShallowOffRamp_generatesKeepLeftInstruction() {
        Instruction instruction = getForkInstruction(RoadClass.MOTORWAY, false, RoadClass.MOTORWAY, true, 50.0200, 10.0015);
        assertEquals(Instruction.KEEP_LEFT, instruction.getSign(), "Motorway with shallow right off-ramp should generate a Keep Left instruction");
    }

    @Test
    public void testMotorwayShallowOffRamp_generatesKeepRightInstruction() {
        Instruction instruction = getForkInstruction(RoadClass.MOTORWAY, false, RoadClass.MOTORWAY, true, 50.0200, 9.9985);
        assertEquals(Instruction.KEEP_RIGHT, instruction.getSign(), "Motorway with shallow left off-ramp should generate a Keep Right instruction");
    }

    @Test
    public void testMotorwaySharpOffRamp_isIgnored() {
        Instruction instruction = getForkInstruction(RoadClass.MOTORWAY, false, RoadClass.MOTORWAY, true, 50.0150, 10.0200);
        assertEquals(Instruction.FINISH, instruction.getSign(), "Sharp off-ramps on motorways should be ignored");
    }

    @Test
    public void testTrunkShallowOffRamp_generatesKeepLeftInstruction() {
        Instruction instruction = getForkInstruction(RoadClass.TRUNK, false, RoadClass.TRUNK, true, 50.0200, 10.0015);
        assertEquals(Instruction.KEEP_LEFT, instruction.getSign(), "Trunk with shallow right off-ramp should generate a Keep Left instruction");
    }

    @Test
    public void testTrunkSharpOffRamp_isIgnored() {
        Instruction instruction = getForkInstruction(RoadClass.TRUNK, false, RoadClass.TRUNK, true, 50.0150, 10.0200);
        assertEquals(Instruction.FINISH, instruction.getSign(), "Sharp off-ramps on trunks should be ignored");
    }

    @Test
    public void testPrimaryShallowOffRamp_isIgnored() {
        Instruction instruction = getForkInstruction(RoadClass.PRIMARY, false, RoadClass.PRIMARY, true, 50.0200, 10.0015);
        assertEquals(Instruction.FINISH, instruction.getSign(), "Shallow off-ramps on primary roads should be ignored");
    }

    @Test
    public void testPrimarySharpOffRamp_isIgnored() {
        Instruction instruction = getForkInstruction(RoadClass.PRIMARY, false, RoadClass.PRIMARY, true, 50.0150, 10.0200);
        assertEquals(Instruction.FINISH, instruction.getSign(), "Sharp off-ramps on primary roads should be ignored");
    }

    @Test
    public void testMotorwayFork_bothMain_generatesKeepInstruction() {
        Instruction instruction = getForkInstruction(RoadClass.MOTORWAY, false, RoadClass.MOTORWAY, false, 50.0200, 10.0015);
        assertEquals(Instruction.KEEP_LEFT, instruction.getSign(), "Motorway fork (both main roads) should generate a Keep instruction");

        Instruction sharpInstruction = getForkInstruction(RoadClass.MOTORWAY, false, RoadClass.MOTORWAY, false, 50.0150, 10.0200);
        assertEquals(Instruction.FINISH, sharpInstruction.getSign(), "Motorway fork (both main roads) but sharp off-ramp style creates continue instruction or ignored");
    }

    @Test
    public void testPrimaryFork_bothMain_generatesKeepInstruction() {
        Instruction instruction = getForkInstruction(RoadClass.PRIMARY, false, RoadClass.PRIMARY, false, 50.0200, 10.0015);
        assertEquals(Instruction.KEEP_LEFT, instruction.getSign(), "Primary fork (both main roads) should generate a Keep instruction");
    }

    @Test
    public void testMotorwayBoundary_justBelowThreshold_generatesKeepInstruction() {
        Instruction instruction = getForkInstruction(RoadClass.MOTORWAY, false, RoadClass.MOTORWAY, true, 50.0200, 10.00332);
        assertEquals(Instruction.KEEP_LEFT, instruction.getSign(),"Fork at 0.21 radians should generate a Keep instruction");
    }

    @Test
    public void testMotorwayBoundary_justAboveThreshold_isIgnored() {
        Instruction instruction = getForkInstruction(RoadClass.MOTORWAY, false, RoadClass.MOTORWAY, true, 50.0200, 10.00364);
        assertEquals(Instruction.FINISH, instruction.getSign(),"Fork at 0.23 radians should be ignored as an obvious exit");
    }

    @Test
    public void testMotorwayExtremelyShallow_generatesKeepInstruction() {
        Instruction instruction = getForkInstruction(RoadClass.MOTORWAY, false, RoadClass.MOTORWAY, true, 50.0200, 10.00078);
        assertEquals(Instruction.KEEP_LEFT, instruction.getSign(),"Extremely shallow parallel fork should generate Keep Left");
    }

    @Test
    public void testMotorwayExtremelySharp_isIgnored() {
        Instruction instruction = getForkInstruction(RoadClass.MOTORWAY, false, RoadClass.MOTORWAY, true, 50.0200, 10.01602);
        assertEquals(Instruction.FINISH, instruction.getSign(),"Extremely sharp fork should be ignored");
    }

    @Test
    public void testMotorwayLeftBoundary_justBelowThreshold_generatesKeepRight() {
        Instruction instruction = getForkInstruction(RoadClass.MOTORWAY, false, RoadClass.MOTORWAY, true, 50.0200, 9.99668);
        assertEquals(Instruction.KEEP_RIGHT, instruction.getSign(),"Fork at -0.21 radians to the left should generate Keep Right");
    }

    @Test
    public void testMotorwayLeftBoundary_justAboveThreshold_isIgnored() {
        Instruction instruction = getForkInstruction(RoadClass.MOTORWAY, false, RoadClass.MOTORWAY, true, 50.0200, 9.99636);
        assertEquals(Instruction.FINISH, instruction.getSign(),"Fork at -0.23 radians to the left should be ignored as an obvious exit");
    }

    @Test
    public void testMotorwayCurvingMainPath_bypassesContinueTrap() {
        BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue("car_access", true);
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("car_average_speed", 5, 5, true);
        EnumEncodedValue<RoadClass> roadClassEnc = new EnumEncodedValue<>("road_class", RoadClass.class);
        BooleanEncodedValue roadClassLinkEnc = new SimpleBooleanEncodedValue("road_class_link", true);

        BooleanEncodedValue roundaboutEnc = new SimpleBooleanEncodedValue("roundabout", true);
        EnumEncodedValue<RoadEnvironment> roadEnvEnc = new EnumEncodedValue<>("road_environment", RoadEnvironment.class);
        DecimalEncodedValue maxSpeedEnc = new DecimalEncodedValueImpl("max_speed", 7, 2, true);

        EncodingManager em = EncodingManager.start()
                .add(accessEnc).add(speedEnc).add(roadClassEnc).add(roadClassLinkEnc)
                .add(roundaboutEnc).add(roadEnvEnc).add(maxSpeedEnc).build();

        BaseGraph graph = new BaseGraph.Builder(em).create();
        Weighting weighting = new SpeedWeighting(speedEnc);

        graph.getNodeAccess().setNode(0, 50.0000, 10.0000);
        graph.getNodeAccess().setNode(1, 50.0100, 10.0000);
        graph.getNodeAccess().setNode(2, 50.0200, 10.00187);
        graph.getNodeAccess().setNode(3, 50.0200, 10.00314);

        graph.edge(0, 1).set(accessEnc, true).set(speedEnc, 100).set(roadClassEnc, RoadClass.MOTORWAY)
                .setKeyValues(Map.of(STREET_NAME, new KVStorage.KValue("A 565")));
        graph.edge(1, 2).set(accessEnc, true).set(speedEnc, 100).set(roadClassEnc, RoadClass.MOTORWAY)
                .setKeyValues(Map.of(STREET_NAME, new KVStorage.KValue("A 565"))); // Main path
        graph.edge(1, 3).set(accessEnc, true).set(speedEnc, 100).set(roadClassEnc, RoadClass.MOTORWAY).set(roadClassLinkEnc, true)
                .setKeyValues(Map.of(STREET_NAME, new KVStorage.KValue("A 565"))); // Alt path

        Path path = new Path(graph);
        path.setWeight(100);
        path.setFromNode(0);
        path.setEndNode(2);
        path.getEdges().add(0);
        path.getEdges().add(1);
        path.setFound(true);

        TranslationMap trMap = new TranslationMap().doImport();
        InstructionList instructions = InstructionsFromEdges.calcInstructions(path, graph, weighting, em, trMap.get("en_US"));

        assertEquals(Instruction.KEEP_LEFT, instructions.get(1).getSign(),"Curved main path with shallow off-ramp should still generate Keep Left");
    }

    @Test
    public void testMotorwayNameChange_bypassesContinueTrap() {
        BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue("car_access", true);
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("car_average_speed", 5, 5, true);
        EnumEncodedValue<RoadClass> roadClassEnc = new EnumEncodedValue<>("road_class", RoadClass.class);
        BooleanEncodedValue roadClassLinkEnc = new SimpleBooleanEncodedValue("road_class_link", true);

        BooleanEncodedValue roundaboutEnc = new SimpleBooleanEncodedValue("roundabout", true);
        EnumEncodedValue<RoadEnvironment> roadEnvEnc = new EnumEncodedValue<>("road_environment", RoadEnvironment.class);
        DecimalEncodedValue maxSpeedEnc = new DecimalEncodedValueImpl("max_speed", 7, 2, true);

        EncodingManager em = EncodingManager.start()
                .add(accessEnc).add(speedEnc).add(roadClassEnc).add(roadClassLinkEnc)
                .add(roundaboutEnc).add(roadEnvEnc).add(maxSpeedEnc).build();

        BaseGraph graph = new BaseGraph.Builder(em).create();
        Weighting weighting = new SpeedWeighting(speedEnc);

        graph.getNodeAccess().setNode(0, 50.0000, 10.0000);
        graph.getNodeAccess().setNode(1, 50.0100, 10.0000);
        graph.getNodeAccess().setNode(2, 50.0200, 10.0000);
        graph.getNodeAccess().setNode(3, 50.0200, 10.00332);

        graph.edge(0, 1).set(accessEnc, true).set(speedEnc, 100).set(roadClassEnc, RoadClass.MOTORWAY)
                .setKeyValues(Map.of(STREET_NAME, new KVStorage.KValue("A 565")));

        graph.edge(1, 2).set(accessEnc, true).set(speedEnc, 100).set(roadClassEnc, RoadClass.MOTORWAY)
                .setKeyValues(Map.of(STREET_NAME, new KVStorage.KValue("A 59")));

        graph.edge(1, 3).set(accessEnc, true).set(speedEnc, 100).set(roadClassEnc, RoadClass.MOTORWAY).set(roadClassLinkEnc, true)
                .setKeyValues(Map.of(STREET_NAME, new KVStorage.KValue("A 565")));

        Path path = new Path(graph);
        path.setWeight(100);
        path.setFromNode(0);
        path.setEndNode(2);
        path.getEdges().add(0);
        path.getEdges().add(1);
        path.setFound(true);

        TranslationMap trMap = new TranslationMap().doImport();
        InstructionList instructions = InstructionsFromEdges.calcInstructions(path, graph, weighting, em, trMap.get("en_US"));

        assertEquals(Instruction.KEEP_LEFT, instructions.get(1).getSign(),"Name change on the main path should force a Keep instruction, skipping the continue check");
    }

    @Test
    public void testMotorwayFork_otherIsLowerClass_isIgnored() {
        Instruction instruction = getForkInstruction(RoadClass.MOTORWAY, false, RoadClass.PRIMARY, false, 50.0200, 10.0015);
        assertEquals(Instruction.FINISH, instruction.getSign(),"Fork where the alternative is a lower road class (not a link) should be ignored safely");
    }

    @Test
    public void testMotorwayLinkFork_bothAreLinks_generatesKeepInstruction() {
        Instruction instruction = getForkInstruction(RoadClass.MOTORWAY, true, RoadClass.MOTORWAY, true, 50.0200, 10.0015);
        assertEquals(Instruction.KEEP_LEFT, instruction.getSign(),"Forking off-ramps (a Y-split on an interchange) should generate Keep instructions");
    }
}
