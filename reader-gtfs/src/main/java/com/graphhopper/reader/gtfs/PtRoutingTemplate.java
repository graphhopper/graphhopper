package com.graphhopper.reader.gtfs;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.routing.template.ViaRoutingTemplate;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
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
}
