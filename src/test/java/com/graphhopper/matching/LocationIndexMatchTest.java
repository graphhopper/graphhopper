package com.graphhopper.matching;

import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.EdgeIteratorState;
import java.util.*;
import org.junit.*;
import static org.junit.Assert.assertEquals;

/**
 *
 * @author Peter Karich
 */
public class LocationIndexMatchTest
{
    @Test
    public void testFindNClosest()
    {
        RAMDirectory dir = new RAMDirectory();
        FlagEncoder encoder = new CarFlagEncoder();
        EncodingManager em = new EncodingManager(encoder);
        GraphStorage graph = new GraphHopperStorage(dir, em, false);
        graph.create(1000);
        // 0---1---2
        // |   |   |
        // |10 |   |
        // | | |   |
        // 3-9-4---5
        // |   |   |
        // 6---7---8
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 0.10, 0.00);
        na.setNode(1, 0.10, 0.05);
        na.setNode(2, 0.10, 0.10);
        na.setNode(3, 0.05, 0.00);
        na.setNode(4, 0.05, 0.05);
        na.setNode(5, 0.05, 0.10);
        na.setNode(6, 0.00, 0.00);
        na.setNode(7, 0.00, 0.05);
        na.setNode(8, 0.00, 0.10);
        na.setNode(9, 0.05, 0.02);
        na.setNode(10, 0.07, 0.02);
        graph.edge(0, 1);
        graph.edge(1, 2);
        graph.edge(0, 3);
        EdgeIteratorState edge1_4 = graph.edge(1, 4);
        graph.edge(2, 5);
        graph.edge(3, 9);
        EdgeIteratorState edge9_4 = graph.edge(9, 4);
        EdgeIteratorState edge4_5 = graph.edge(4, 5);
        graph.edge(10, 9);
        graph.edge(3, 6);
        EdgeIteratorState edge4_7 = graph.edge(4, 7);
        graph.edge(5, 8);
        graph.edge(6, 7);
        graph.edge(7, 8);

        LocationIndexMatch index = new LocationIndexMatch(graph, new RAMDirectory());
        index.prepareIndex();
        List<QueryResult> result = index.findNClosest(4, 0.04, 0.7, EdgeFilter.ALL_EDGES);
        assertEquals(4, result.size());
        List<Integer> ids = new ArrayList<Integer>();
        for(QueryResult qr : result) {
            ids.add(qr.getClosestEdge().getEdge());
        }
        Collections.sort(ids);
        assertEquals(Arrays.asList(edge1_4.getEdge(), edge9_4.getEdge(), edge4_5.getEdge(), edge4_7.getEdge()),
                ids);
    }

}
