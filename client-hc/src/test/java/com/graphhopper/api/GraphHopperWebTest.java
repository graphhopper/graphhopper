package com.graphhopper.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.GHRequest;
import com.graphhopper.PathWrapper;
import com.graphhopper.jackson.Jackson;
import com.graphhopper.jackson.PathWrapperDeserializer;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;

@RunWith(Parameterized.class)
public class GraphHopperWebTest {

    private final GraphHopperWeb gh;

    public GraphHopperWebTest(boolean usePost, int maxUnzippedLength) {
        gh = new GraphHopperWeb(null).setPostRequest(usePost).setMaxUnzippedLength(maxUnzippedLength);
    }

    /**
     * Runs the same test with each of the supported traversal modes
     */
    @Parameterized.Parameters(name = "POST: {0}, maxUnzippedLength: {1}")
    public static Collection<Object[]> configs() {
        return Arrays.asList(new Object[][]{
                {false, -1},
                {true, 0},
                {true, 1000}
        });
    }

    @Test
    public void getClientForRequest() {
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(42.509225, 1.534728)).
                addPoint(new GHPoint(42.512602, 1.551558)).
                putHint("vehicle", "car");
        req.putHint(GraphHopperWeb.TIMEOUT, 5);

        assertEquals(5, gh.getClientForRequest(req).connectTimeoutMillis());
    }

    @Test
    public void testPutPOJO() {
        ObjectNode requestJson = new ObjectMapper().createObjectNode();
        requestJson.putPOJO("double", 1.0);
        requestJson.putPOJO("int", 1);
        requestJson.putPOJO("boolean", true);
        // does not work requestJson.putPOJO("string", "test");
        assertEquals("{\"double\":1.0,\"int\":1,\"boolean\":true}", requestJson.toString());
    }

    @Test
    public void testUnknownInstructionSign() throws IOException {
        // Modified the sign though
        ObjectMapper objectMapper = Jackson.newObjectMapper();
        JsonNode json = objectMapper.readTree("{\"instructions\":[{\"distance\":1.073,\"sign\":741,\"interval\":[0,1],\"text\":\"Continue onto A 81\",\"time\":32,\"street_name\":\"A 81\"},{\"distance\":0,\"sign\":4,\"interval\":[1,1],\"text\":\"Finish!\",\"time\":0,\"street_name\":\"\"}],\"descend\":0,\"ascend\":0,\"distance\":1.073,\"bbox\":[8.676286,48.354446,8.676297,48.354453],\"weight\":0.032179,\"time\":32,\"points_encoded\":true,\"points\":\"gfcfHwq}s@}c~AAA?\",\"snapped_waypoints\":\"gfcfHwq}s@}c~AAA?\"}");
        PathWrapper wrapper = PathWrapperDeserializer.createPathWrapper(objectMapper, json, true, true);

        assertEquals(741, wrapper.getInstructions().get(0).getSign());
        assertEquals("Continue onto A 81", wrapper.getInstructions().get(0).getName());
    }
}