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
}
