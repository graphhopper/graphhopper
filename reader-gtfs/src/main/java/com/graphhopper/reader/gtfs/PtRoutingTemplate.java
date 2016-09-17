package com.graphhopper.reader.gtfs;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.routing.*;
import com.graphhopper.routing.template.ViaRoutingTemplate;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.exceptions.CannotFindPointException;
import com.graphhopper.util.shapes.GHPoint;

import java.util.ArrayList;
import java.util.List;

class PtRoutingTemplate extends ViaRoutingTemplate {

	private final GtfsStorage gtfsStorage;

	PtRoutingTemplate(GHRequest ghRequest, GHResponse ghRsp, LocationIndex locationIndex, GtfsStorage gtfsStorage) {
		super(ghRequest, ghRsp, locationIndex);
		this.gtfsStorage = gtfsStorage;
	}

	@Override
	public List<QueryResult> lookup(List<GHPoint> points, FlagEncoder encoder) {
		if (points.size() < 2)
			throw new IllegalArgumentException("At least 2 points have to be specified, but was:" + points.size());

		EdgeFilter edgeFilter = new PtPositionLookupEdgeFilter(gtfsStorage);
		queryResults = new ArrayList<>(points.size());
		for (int placeIndex = 0; placeIndex < points.size(); placeIndex++) {
			GHPoint point = points.get(placeIndex);
			QueryResult res = locationIndex.findClosest(point.lat, point.lon, edgeFilter);
			if (!res.isValid())
				ghResponse.addError(new CannotFindPointException("Cannot find point " + placeIndex + ": " + point, placeIndex));

			queryResults.add(res);
		}

		return queryResults;
	}

	@Override
	public List<Path> calcPaths(QueryGraph queryGraph, RoutingAlgorithmFactory algoFactory, AlgorithmOptions algoOpts) {
		long visitedNodesSum = 0L;
		boolean viaTurnPenalty = ghRequest.getHints().getBool(Parameters.Routing.PASS_THROUGH, false);
		int pointCounts = ghRequest.getPoints().size();
		pathList = new ArrayList<>(pointCounts - 1);
		QueryResult fromQResult = queryResults.get(0);
		StopWatch sw;
		for (int placeIndex = 1; placeIndex < pointCounts; placeIndex++) {
			if (placeIndex == 1) {
				// enforce start direction
				queryGraph.enforceHeading(fromQResult.getClosestNode(), ghRequest.getFavoredHeading(0), false);
			} else if (viaTurnPenalty) {
				// enforce straight start after via stop
				Path prevRoute = pathList.get(placeIndex - 2);
				if (prevRoute.getEdgeCount() > 0) {
					EdgeIteratorState incomingVirtualEdge = prevRoute.getFinalEdge();
					queryGraph.enforceHeadingByEdgeId(fromQResult.getClosestNode(), incomingVirtualEdge.getEdge(), false);
				}
			}

			QueryResult toQResult = queryResults.get(placeIndex);

			// enforce end direction
			queryGraph.enforceHeading(toQResult.getClosestNode(), ghRequest.getFavoredHeading(placeIndex), true);

			sw = new StopWatch().start();
			TimeDependentRoutingAlgorithm algo = (TimeDependentRoutingAlgorithm) algoFactory.createAlgo(queryGraph, algoOpts);
			String debug = ", algoInit:" + sw.stop().getSeconds() + "s";

			sw = new StopWatch().start();
			List<Path> tmpPathList = algo.calcPaths(fromQResult.getClosestNode(), toQResult.getClosestNode(), ghRequest.getHints().getInt(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, 0));
			debug += ", " + algo.getName() + "-routing:" + sw.stop().getSeconds() + "s";
			if (tmpPathList.isEmpty())
				throw new IllegalStateException("At least one path has to be returned for " + fromQResult + " -> " + toQResult);

			for (Path path : tmpPathList) {
				if (path.getTime() < 0)
					throw new RuntimeException("Time was negative. Please report as bug and include:" + ghRequest);

				pathList.add(path);
				debug += ", " + path.getDebugInfo();
			}

			altResponse.addDebugInfo(debug);

			// reset all direction enforcements in queryGraph to avoid influencing next path
			queryGraph.clearUnfavoredStatus();

			if (algo.getVisitedNodes() >= algoOpts.getMaxVisitedNodes())
				throw new IllegalArgumentException("No path found due to maximum nodes exceeded " + algoOpts.getMaxVisitedNodes());

			visitedNodesSum += algo.getVisitedNodes();
			fromQResult = toQResult;
		}

		ghResponse.getHints().put("visited_nodes.sum", visitedNodesSum);
		ghResponse.getHints().put("visited_nodes.average", (float) visitedNodesSum / (pointCounts - 1));

		return pathList;
	}

}
