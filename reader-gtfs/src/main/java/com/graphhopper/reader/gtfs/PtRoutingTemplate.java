package com.graphhopper.reader.gtfs;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.PathWrapper;
import com.graphhopper.routing.*;
import com.graphhopper.routing.template.RoutingTemplate;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.*;
import com.graphhopper.util.exceptions.CannotFindPointException;
import com.graphhopper.util.shapes.GHPoint;

import java.util.ArrayList;
import java.util.List;

class PtRoutingTemplate implements RoutingTemplate {

	private final GtfsStorage gtfsStorage;
	private final GHRequest ghRequest;
	private final GHResponse ghResponse;
	private final LocationIndex locationIndex;
	private List<QueryResult> snappedWaypoints = new ArrayList<>(2);;
	private List<Path> paths;

	PtRoutingTemplate(GHRequest ghRequest, GHResponse ghRsp, LocationIndex locationIndex, GtfsStorage gtfsStorage) {
		this.gtfsStorage = gtfsStorage;
		this.ghRequest = ghRequest;
		this.ghResponse = ghRsp;
		this.locationIndex = locationIndex;
	}

	@Override
	public List<QueryResult> lookup(List<GHPoint> points, FlagEncoder encoder) {
		if (points.size() != 2)
			throw new IllegalArgumentException("Exactly 2 points have to be specified, but was:" + points.size());

		EdgeFilter enterFilter = new PtEnterPositionLookupEdgeFilter(gtfsStorage);
		EdgeFilter exitFilter = new PtExitPositionLookupEdgeFilter(gtfsStorage);

		GHPoint enter = points.get(0);
		QueryResult res = locationIndex.findClosest(enter.lat, enter.lon, enterFilter);
		if (!res.isValid())
			ghResponse.addError(new CannotFindPointException("Cannot find entry point: " + enter, 0));
		snappedWaypoints.add(res);
		GHPoint exit = points.get(1);
		res = locationIndex.findClosest(exit.lat, exit.lon, exitFilter);
		if (!res.isValid())
			ghResponse.addError(new CannotFindPointException("Cannot find exit point: " + exit, 0));
		snappedWaypoints.add(res);
		return snappedWaypoints;
	}

	@Override
	public List<Path> calcPaths(QueryGraph queryGraph, RoutingAlgorithmFactory algoFactory, AlgorithmOptions algoOpts) {
		long visitedNodesSum = 0L;
		if (ghRequest.getPoints().size() != 2 ) {
			throw new UnsupportedOperationException();
		}
		paths = new ArrayList<>();
		QueryResult fromQResult = snappedWaypoints.get(0);
		QueryResult toQResult = snappedWaypoints.get(1);
		PathWrapper altResponse = new PathWrapper();

		StopWatch sw = new StopWatch().start();
		TimeDependentRoutingAlgorithm algo = (TimeDependentRoutingAlgorithm) algoFactory.createAlgo(queryGraph, algoOpts);
		String debug = ", algoInit:" + sw.stop().getSeconds() + "s";

		sw = new StopWatch().start();
		List<Path> tmpPathList = algo.calcPaths(fromQResult.getClosestNode(), toQResult.getClosestNode(), ghRequest.getHints().getInt(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, 0));
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
				wrappedPath.setRouteWeight(path.getWeight());
				wrappedPath.setDistance(path.getDistance());
				wrappedPath.setTime(path.getTime());
				ghResponse.add(wrappedPath);
			}
		}
		return true;
	}

	private void fillInstructions(Path path, InstructionList outInstructions) {
		PointList points = path.calcPoints();
		List<EdgeIteratorState> edges = path.calcEdges();
		for (EdgeIteratorState edge : edges) {
			outInstructions.add(new Instruction(0, "Kante", new InstructionAnnotation(0, edge.getName()), edge.fetchWayGeometry(1)));
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
}
