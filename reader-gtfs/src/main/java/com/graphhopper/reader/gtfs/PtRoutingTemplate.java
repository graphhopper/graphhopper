package com.graphhopper.reader.gtfs;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.PathWrapper;
import com.graphhopper.routing.*;
import com.graphhopper.routing.template.RoutingTemplate;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.*;
import com.graphhopper.util.exceptions.PointNotFoundException;
import com.graphhopper.util.shapes.GHPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

class PtRoutingTemplate implements RoutingTemplate {

	private final GtfsStorage gtfsStorage;
	private final GHRequest ghRequest;
	private final GHResponse ghResponse;
	private final LocationIndex locationIndex;
	private final GraphHopperStorage graphHopperStorage;
	private List<QueryResult> snappedWaypoints = new ArrayList<>(2);
	private List<Path> paths;
    private List<Integer> toNodes;
    private int startNode = -1;
    private int initialTime;
    private FlagEncoder flagEncoder;

    PtRoutingTemplate(GHRequest ghRequest, GHResponse ghRsp, LocationIndex locationIndex, GtfsStorage gtfsStorage, GraphHopperStorage graphHopperStorage) {
		this.gtfsStorage = gtfsStorage;
		this.ghRequest = ghRequest;
		this.ghResponse = ghRsp;
		this.locationIndex = locationIndex;
		this.graphHopperStorage = graphHopperStorage;
	}

	@Override
	public List<QueryResult> lookup(List<GHPoint> points, FlagEncoder encoder) {
        this.flagEncoder = encoder;
		if (points.size() != 2)
			throw new IllegalArgumentException("Exactly 2 points have to be specified, but was:" + points.size());

		EdgeFilter enterFilter = new PtEnterPositionLookupEdgeFilter(gtfsStorage);
		EdgeFilter exitFilter = new PtExitPositionLookupEdgeFilter(gtfsStorage);

		GHPoint enter = points.get(0);
		QueryResult source = locationIndex.findClosest(enter.lat, enter.lon, enterFilter);
		if (!source.isValid()) {
			ghResponse.addError(new PointNotFoundException("Cannot find entry point: " + enter, 0));
		} else {
            ForwardInTime forwardInTime = new ForwardInTime(source);
            int requestedTimeOfDay = ghRequest.getHints().getInt(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, 0) % (24 * 60 * 60);
            int requestedDay = ghRequest.getHints().getInt(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, 0) / (24 * 60 * 60);
            startNode = forwardInTime.find(requestedTimeOfDay);
            initialTime = forwardInTime.getTime() + requestedDay * (24 * 60 * 60);
        }
		snappedWaypoints.add(source);
		GHPoint exit = points.get(1);
        QueryResult dest = locationIndex.findClosest(exit.lat, exit.lon, exitFilter);
		toNodes = new ArrayList<>();
		if (!dest.isValid()) {
			ghResponse.addError(new PointNotFoundException("Cannot find exit point: " + exit, 0));
		} else {
			new DepthFirstSearch() {
				@Override
				protected boolean goFurther(int nodeId) {
					toNodes.add(nodeId);
					return true;
				}
			}.start(graphHopperStorage.createEdgeExplorer(new EdgeFilter() {
				@Override
				public boolean accept(EdgeIteratorState edgeState) {
					return gtfsStorage.getEdges().get(edgeState.getEdge()) instanceof ExitFindingDummyEdge;
				}
			}), dest.getClosestNode());
		}
		snappedWaypoints.add(dest);
		return snappedWaypoints;
	}

	@Override
	public List<Path> calcPaths(QueryGraph queryGraph, RoutingAlgorithmFactory algoFactory, AlgorithmOptions algoOpts) {
		long visitedNodesSum = 0L;
		if (ghRequest.getPoints().size() != 2 ) {
			throw new UnsupportedOperationException();
		}
		paths = new ArrayList<>();
        PathWrapper altResponse = new PathWrapper();

		StopWatch sw = new StopWatch().start();
		TimeDependentRoutingAlgorithm algo = (TimeDependentRoutingAlgorithm) algoFactory.createAlgo(queryGraph, algoOpts);
		String debug = ", algoInit:" + sw.stop().getSeconds() + "s";

		sw = new StopWatch().start();

		List<Path> tmpPathList = ((MultiCriteriaLabelSetting) algo).calcPaths(startNode, new HashSet(toNodes));
		debug += ", " + algo.getName() + "-routing:" + sw.stop().getSeconds() + "s";

		for (Path path : tmpPathList) {
			if (path.getTime() < 0)
				throw new RuntimeException("Time was negative. Please report as bug and include:" + ghRequest);

			paths.add(path);
			debug += ", " + path.getDebugInfo();
		}

		altResponse.addDebugInfo(debug);

		// reset all direction enforcements in queryGraph to avoid influencing next path
		queryGraph.clearUnfavoredStatus();

		if (algo.getVisitedNodes() >= algoOpts.getMaxVisitedNodes())
			throw new IllegalArgumentException("No path found due to maximum nodes exceeded " + algoOpts.getMaxVisitedNodes());

		visitedNodesSum += algo.getVisitedNodes();

		ghResponse.getHints().put("visited_nodes.sum", visitedNodesSum);
		ghResponse.getHints().put("visited_nodes.average", (float) visitedNodesSum);

		return paths;
	}

	@Override
	public boolean isReady(PathMerger pathMerger, Translation tr) {
		for (Path path : paths) {
			if (path.isFound()) {
				PathWrapper wrappedPath = new PathWrapper();
				PointList waypoints = new PointList(snappedWaypoints.size(), true);
				for (QueryResult qr : snappedWaypoints) {
					waypoints.add(qr.getSnappedPoint());
				}
				wrappedPath.setWaypoints(waypoints);
				InstructionList instructions = new InstructionList(tr);
				fillInstructions(path, instructions);
				wrappedPath.setInstructions(instructions);
				wrappedPath.setDescription(path.getDescription());
				PointList points = new PointList();
				for (Instruction instruction : instructions) {
					points.add(instruction.getPoints());
				}
				wrappedPath.setPoints(points);
				wrappedPath.setRouteWeight(path.getWeight() + initialTime);
				wrappedPath.setDistance(path.getDistance());
				wrappedPath.setTime(path.getTime() + initialTime * 1000);
				ghResponse.add(wrappedPath);
			}
		}
		Collections.sort(ghResponse.getAll(), (p1, p2) -> Double.compare(p1.getRouteWeight(), p2.getRouteWeight()));
		return true;
	}

	private void fillInstructions(Path path, InstructionList outInstructions) {
		PointList points = path.calcPoints();
		List<EdgeIteratorState> edges = path.calcEdges();
		for (EdgeIteratorState edge : edges) {
			outInstructions.add(new Instruction(0, edge.getName(), new InstructionAnnotation(0, edge.getName()), edge.fetchWayGeometry(1)));
		}
		if (!points.isEmpty()) {
			PointList end = new PointList();
			end.add(points, points.size()-1);
			outInstructions.add(new Instruction(0, "Angekommen", new InstructionAnnotation(0, "Vermutlich am Ziel"), end));
		}
	}

	@Override
	public int getMaxRetries() {
		return 1;
	}

    private class ForwardInTime extends DepthFirstSearch {
        private final QueryResult source;
        int time;
        int lastNode;
        private int requestedTime;

        ForwardInTime(QueryResult source) {
            this.source = source;
            time = 0;
            lastNode = source.getClosestNode();
        }

        int find(int requestedTime) {
            this.requestedTime = requestedTime;
            start(graphHopperStorage.createEdgeExplorer(new DefaultEdgeFilter(flagEncoder, false, true)), source.getClosestNode());
            if (time >= requestedTime) {
                return lastNode;
            } else {
                return -1;
            }
        }

        public int getTime() {
            return time;
        }

        @Override
        protected boolean goFurther(int nodeId) {
lastNode = nodeId;
            return time < requestedTime;
        }

        @Override
        protected boolean checkAdjacent(EdgeIteratorState edgeState) {
            AbstractPtEdge abstractPtEdge = gtfsStorage.getEdges().get(edgeState.getEdge());
            if (abstractPtEdge instanceof WaitInStationEdge) {
                int deltaTime = ((WaitInStationEdge) abstractPtEdge).deltaTime;
                time += deltaTime;
                return true;
            }
            return false;
        }
    }
}
