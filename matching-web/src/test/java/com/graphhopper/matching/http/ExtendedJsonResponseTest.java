package com.graphhopper.matching.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.graphhopper.matching.EdgeMatch;
import com.graphhopper.matching.MatchResult;
import com.graphhopper.matching.Observation;
import com.graphhopper.matching.State;
import com.graphhopper.routing.querygraph.VirtualEdgeIteratorState;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;
import com.graphhopper.util.shapes.GHPoint3D;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ExtendedJsonResponseTest {

    @Test
    public void shouldCreateBasicStructure() {
        JsonNode jsonObject = MapMatchingResource.convertToTree(new MatchResult(getEdgeMatch()), false, false);
        JsonNode route = jsonObject.get("diary").get("entries").get(0);
        JsonNode link = route.get("links").get(0);
        JsonNode geometry = link.get("geometry");
        assertEquals("geometry should have type", "LineString", geometry.get("type").asText());
        assertEquals("geometry should have coordinates", "LINESTRING (-38.999 -3.4445, -38.799 -3.555)", geometry.get("coordinates").asText());

        assertEquals("wpts[0].y should exists", "-3.4446", link.get("wpts").get(0).get("y").asText());
        assertEquals("wpts[0].x should exists", "-38.9996", link.get("wpts").get(0).get("x").asText());

        assertEquals("wpts[1].y should exists", "-3.4449", link.get("wpts").get(1).get("y").asText());
        assertEquals("wpts[1].x should exists", "-38.9999", link.get("wpts").get(1).get("x").asText());
    }

    private List<EdgeMatch> getEdgeMatch() {
        List<EdgeMatch> list = new ArrayList<>();
        list.add(new EdgeMatch(getEdgeIterator(), getGpxExtension()));
        return list;
    }

    private List<State> getGpxExtension() {
        List<State> list = new ArrayList<>();
        Snap snap1 = new Snap(-3.4445, -38.9990) {
            @Override
            public GHPoint3D getSnappedPoint() {
                return new GHPoint3D(-3.4446, -38.9996, 0);
            }
        };
        Snap snap2 = new Snap(-3.4445, -38.9990) {
            @Override
            public GHPoint3D getSnappedPoint() {
                return new GHPoint3D(-3.4449, -38.9999, 0);
            }
        };

        list.add(new State(new Observation(new GHPoint(-3.4446, -38.9996)), snap1));
        list.add(new State(new Observation(new GHPoint(-3.4448, -38.9999)), snap2));
        return list;
    }

    private EdgeIteratorState getEdgeIterator() {
        PointList pointList = new PointList();
        pointList.add(-3.4445, -38.9990);
        pointList.add(-3.5550, -38.7990);
        return new VirtualEdgeIteratorState(0, 0, 0, 1, 10,  new IntsRef(1), "test of iterator", pointList, false);
    }

}
