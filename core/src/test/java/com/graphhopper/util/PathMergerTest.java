package com.graphhopper.util;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.QueryGraph;
import com.graphhopper.routing.RoutingAlgorithmFactorySimple;
import com.graphhopper.routing.template.ViaRoutingTemplate;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.details.PathDetailsBuilderFactory;
import com.graphhopper.util.exceptions.ConnectionNotFoundException;
import com.graphhopper.util.exceptions.ConnectionNotFoundException.Keys;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.Test;

import java.util.*;

import static com.graphhopper.util.Parameters.Algorithms.DIJKSTRA_BI;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Oliver Schlüter
 */
public class PathMergerTest {
    private final FlagEncoder carFE = new CarFlagEncoder();
    private final EncodingManager em = new EncodingManager(carFE);
    private final TraversalMode tMode = TraversalMode.NODE_BASED;
    private final TranslationMap trMap = TranslationMapTest.SINGLETON;
    private final Translation usTR = trMap.getWithFallBack(Locale.US);
    private PathDetailsBuilderFactory pathBuilderFactory = new PathDetailsBuilderFactory();

    @Test
    public void testConnectionNotFoundDetails() {
        Graph g = createGraph();
        LocationIndex locationIndex = new LocationIndexTree(g, new RAMDirectory()).prepareIndex();

        GHRequest ghRequest = new GHRequest();
        GHResponse ghRsp = new GHResponse();
        ghRequest.addPoint(new GHPoint(52.0, 8.0));
        ghRequest.addPoint(new GHPoint(52.2, 8.2));
        ghRequest.addPoint(new GHPoint(52.4, 8.4));
        ghRequest.addPoint(new GHPoint(52.5, 8.5));
        ghRequest.addPoint(new GHPoint(52.6, 8.6));
        ghRequest.addPoint(new GHPoint(52.8, 8.8));

        ViaRoutingTemplate vrt = new ViaRoutingTemplate(ghRequest, ghRsp, locationIndex);

        List<QueryResult> stagePoints = vrt.lookup(ghRequest.getPoints(), carFE);
        assertEquals(6, stagePoints.size());
        assertEquals(0, stagePoints.get(0).getClosestNode());
        assertEquals(2, stagePoints.get(1).getClosestNode());
        assertEquals(4, stagePoints.get(2).getClosestNode());
        assertEquals(5, stagePoints.get(3).getClosestNode());
        assertEquals(6, stagePoints.get(4).getClosestNode());
        assertEquals(8, stagePoints.get(5).getClosestNode());

        QueryGraph qg = new QueryGraph(g);
        qg.lookup(stagePoints);
        List<Path> paths = vrt.calcPaths(qg, new RoutingAlgorithmFactorySimple(),
                new AlgorithmOptions(DIJKSTRA_BI, new FastestWeighting(carFE), tMode));
        assertEquals(5, paths.size());
        assertEquals(Helper.createTList(0, 1, 2), paths.get(0).calcNodes());
        assertEquals(Helper.createTList(), paths.get(1).calcNodes());
        assertEquals(Helper.createTList(4, 5), paths.get(2).calcNodes());
        assertEquals(Helper.createTList(5, 6), paths.get(3).calcNodes());
        assertEquals(Helper.createTList(), paths.get(4).calcNodes());

        vrt.isReady(pathMerger(ghRequest), usTR);
        assertEquals(1, ghRsp.getErrors().size());
        Throwable error = ghRsp.getErrors().get(0);
        assertTrue(error instanceof ConnectionNotFoundException);
        String errorMessage = error.getMessage();
        assertTrue(errorMessage, errorMessage.
                equals("Connection between point 1 and point 2, and point 4 and point 5 not found"));
        Map<String, Object> details = ((ConnectionNotFoundException) error).getDetails();
        verifyDetails((Map<?, ?>)details.get("connection-not-found-0"), 1, 52.2, 8.2, 2, 52.4, 8.4);
        verifyDetails((Map<?, ?>)details.get("connection-not-found-1"), 4, 52.6, 8.6, 5, 52.8, 8.8);
    }

    private void verifyDetails(Map<?, ?> detailEntry, int idFrom, double latFrom,
                               double lonFrom, int idTo, double latTo, double lonTo) {

        assertEquals(detailEntry.get(Keys.idFrom.name()), idFrom);
        assertEquals((Double)detailEntry.get(Keys.latFrom.name()), latFrom, 0.01);
        assertEquals((Double)detailEntry.get(Keys.lonFrom.name()), lonFrom, 0.01);
        assertEquals(detailEntry.get(Keys.idTo.name()), idTo);
        assertEquals((Double)detailEntry.get(Keys.latTo.name()), latTo, 0.01);
        assertEquals((Double)detailEntry.get(Keys.lonTo.name()), lonTo, 0.01);
    }

    private Graph createGraph() {
        Graph g = new GraphBuilder(em).create();
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 52.0, 8.0);
        na.setNode(1, 52.1, 8.1);
        na.setNode(2, 52.2, 8.2);
        na.setNode(3, 52.3, 8.3);
        na.setNode(4, 52.4, 8.4);
        na.setNode(5, 52.5, 8.5);
        na.setNode(6, 52.6, 8.6);
        na.setNode(7, 52.7, 8.7);
        na.setNode(8, 52.8, 8.8);
        na.setNode(9, 52.9, 8.9);

        g.edge(0, 1, 1, true);
        g.edge(1, 2, 1, true);
        g.edge(2, 3, 1, true);
        // remove connection from node 3 to 4
        //g.edge(3, 4, 1, true);
        g.edge(4, 5, 1, true);
        g.edge(5, 6, 1, true);
        g.edge(6, 7, 1, true);
        // remove connection from node 7 to 8
        //g.edge(7, 8, 1, true);
        g.edge(8, 9, 1, true);

        return g;
    }

    private PathMerger pathMerger(GHRequest ghRequest) {
        return new PathMerger().
                setCalcPoints(true).
                setDouglasPeucker(new DouglasPeucker().setMaxDistance(2d)).
                setEnableInstructions(true).
                setPathDetailsBuilders(pathBuilderFactory, ghRequest.getPathDetails()).
                setSimplifyResponse(true);
    }
}
