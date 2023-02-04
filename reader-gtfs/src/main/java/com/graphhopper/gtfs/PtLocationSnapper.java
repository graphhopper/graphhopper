package com.graphhopper.gtfs;

import com.carrotsearch.hppc.IntHashSet;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Stop;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.core.util.PointList;
import com.graphhopper.core.util.exceptions.PointNotFoundException;
import com.graphhopper.core.util.shapes.GHPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class PtLocationSnapper {

    public static class Result {
        public final QueryGraph queryGraph;
        public final List<Label.NodeId> nodes;
        public final PointList points;

        public Result(QueryGraph queryGraph, List<Label.NodeId> nodes, PointList points) {
            this.queryGraph = queryGraph;
            this.nodes = nodes;
            this.points = points;
        }
    }

    BaseGraph baseGraph;
    LocationIndex locationIndex;
    GtfsStorage gtfsStorage;

    public PtLocationSnapper(BaseGraph baseGraph, LocationIndex locationIndex, GtfsStorage gtfsStorage) {
        this.baseGraph = baseGraph;
        this.locationIndex = locationIndex;
        this.gtfsStorage = gtfsStorage;
    }

    public Result snapAll(List<GHLocation> locations, List<EdgeFilter> snapFilters) {
        PointList points = new PointList(2, false);
        ArrayList<Snap> pointSnaps = new ArrayList<>();
        ArrayList<Supplier<Label.NodeId>> allSnaps = new ArrayList<>();
        for (int i = 0; i < locations.size(); i++) {
            GHLocation location = locations.get(i);
            if (location instanceof GHPointLocation) {
                GHPoint point = ((GHPointLocation) location).ghPoint;
                final Snap closest = locationIndex.findClosest(point.lat, point.lon, snapFilters.get(i));
                if (!closest.isValid()) {
                    IntHashSet result = new IntHashSet();
                    gtfsStorage.getStopIndex().findEdgeIdsInNeighborhood(point.lat, point.lon, 0, result::add);
                    gtfsStorage.getStopIndex().findEdgeIdsInNeighborhood(point.lat, point.lon, 1, result::add);
                    if (result.isEmpty()) {
                        throw new PointNotFoundException("Cannot find point: " + point, i);
                    }
                    IntCursor stopNodeId = result.iterator().next();
                    for (Map.Entry<GtfsStorage.FeedIdWithStopId, Integer> e : gtfsStorage.getStationNodes().entrySet()) {
                        if (e.getValue() == stopNodeId.value) {
                            Stop stop = gtfsStorage.getGtfsFeeds().get(e.getKey().feedId).stops.get(e.getKey().stopId);
                            final Snap stopSnap = new Snap(stop.stop_lat, stop.stop_lon);
                            stopSnap.setClosestNode(stopNodeId.value);
                            allSnaps.add(() -> new Label.NodeId(gtfsStorage.getPtToStreet().getOrDefault(stopSnap.getClosestNode(), -1), stopSnap.getClosestNode()));
                            points.add(stopSnap.getQueryPoint().lat, stopSnap.getQueryPoint().lon);
                        }
                    }
                } else {
                    pointSnaps.add(closest);
                    allSnaps.add(() -> new Label.NodeId(closest.getClosestNode(), gtfsStorage.getStreetToPt().getOrDefault(closest.getClosestNode(), -1)));
                    points.add(closest.getSnappedPoint());
                }
            } else if (location instanceof GHStationLocation) {
                final Snap stopSnap = findByStopId((GHStationLocation) location, i);
                allSnaps.add(() -> new Label.NodeId(gtfsStorage.getPtToStreet().getOrDefault(stopSnap.getClosestNode(), -1), stopSnap.getClosestNode()));
                points.add(stopSnap.getQueryPoint().lat, stopSnap.getQueryPoint().lon);
            }
        }
        QueryGraph queryGraph = QueryGraph.create(baseGraph.getBaseGraph(), pointSnaps); // modifies pointSnaps!

        List<Label.NodeId> nodes = new ArrayList<>();
        for (Supplier<Label.NodeId> supplier : allSnaps) {
            nodes.add(supplier.get());
        }
        return new Result(queryGraph, nodes, points);
    }

    private Snap findByStopId(GHStationLocation station, int indexForErrorMessage) {
        for (Map.Entry<String, GTFSFeed> entry : gtfsStorage.getGtfsFeeds().entrySet()) {
            final Integer node = gtfsStorage.getStationNodes().get(new GtfsStorage.FeedIdWithStopId(entry.getKey(), station.stop_id));
            if (node != null) {
                Stop stop = gtfsStorage.getGtfsFeeds().get(entry.getKey()).stops.get(station.stop_id);
                final Snap stationSnap = new Snap(stop.stop_lat, stop.stop_lon);
                stationSnap.setClosestNode(node);
                return stationSnap;
            }
        }
        throw new PointNotFoundException("Cannot find station: " + station.stop_id, indexForErrorMessage);
    }


}
