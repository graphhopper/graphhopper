/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.graphhopper.gtfs;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Stop;
import com.conveyal.gtfs.model.StopTime;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.ResponsePath;
import com.graphhopper.Trip;
import com.graphhopper.gtfs.fare.Fares;
import com.graphhopper.routing.InstructionsFromEdges;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.*;
import com.graphhopper.util.details.PathDetail;
import com.graphhopper.util.details.PathDetailsBuilderFactory;
import com.graphhopper.util.details.PathDetailsFromEdges;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.time.temporal.ChronoUnit.SECONDS;

class TripFromLabel {

    private static final Logger logger = LoggerFactory.getLogger(TripFromLabel.class);

    private final Graph graph;
    private final EncodedValueLookup encodedValueLookup;
    private final GtfsStorage gtfsStorage;
    private final RealtimeFeed realtimeFeed;
    private final GeometryFactory geometryFactory = new GeometryFactory();
    private final PathDetailsBuilderFactory pathDetailsBuilderFactory;
    private final double walkSpeedKmH;

    TripFromLabel(Graph graph, EncodedValueLookup encodedValueLookup, GtfsStorage gtfsStorage, RealtimeFeed realtimeFeed, PathDetailsBuilderFactory pathDetailsBuilderFactory, double walkSpeedKmH) {
        this.graph = graph;
        this.encodedValueLookup = encodedValueLookup;
        this.gtfsStorage = gtfsStorage;
        this.realtimeFeed = realtimeFeed;
        this.pathDetailsBuilderFactory = pathDetailsBuilderFactory;
        this.walkSpeedKmH = walkSpeedKmH;
    }

    ResponsePath createResponsePath(Translation tr, PointList waypoints, Graph queryGraph, Weighting accessWeighting, Weighting egressWeighting, List<Label.Transition> solution, List<String> requestedPathDetails) {
        final List<List<Label.Transition>> partitions = parsePathToPartitions(solution);

        final List<Trip.Leg> legs = new ArrayList<>();
        for (int i = 0; i < partitions.size(); i++) {
            legs.addAll(parsePartitionToLegs(partitions.get(i), queryGraph, encodedValueLookup, i == partitions.size() - 1 ? egressWeighting : accessWeighting, tr, requestedPathDetails));
        }

        if (legs.size() > 1 && legs.get(0) instanceof Trip.WalkLeg) {
            final Trip.WalkLeg accessLeg = (Trip.WalkLeg) legs.get(0);
            legs.set(0, new Trip.WalkLeg(accessLeg.departureLocation, new Date(legs.get(1).getDepartureTime().getTime() - (accessLeg.getArrivalTime().getTime() - accessLeg.getDepartureTime().getTime())),
                    accessLeg.geometry, accessLeg.distance, accessLeg.instructions, accessLeg.details, legs.get(1).getDepartureTime()));
        }
        if (legs.size() > 1 && legs.get(legs.size() - 1) instanceof Trip.WalkLeg) {
            final Trip.WalkLeg egressLeg = (Trip.WalkLeg) legs.get(legs.size() - 1);
            legs.set(legs.size() - 1, new Trip.WalkLeg(egressLeg.departureLocation, legs.get(legs.size() - 2).getArrivalTime(),
                    egressLeg.geometry, egressLeg.distance, egressLeg.instructions,
                    egressLeg.details, new Date(legs.get(legs.size() - 2).getArrivalTime().getTime() + (egressLeg.getArrivalTime().getTime() - egressLeg.getDepartureTime().getTime()))));
        }

        ResponsePath path = new ResponsePath();
        path.setWaypoints(waypoints);
        path.getLegs().addAll(legs);

        final InstructionList instructions = new InstructionList(tr);
        final PointList pointsList = new PointList();
        Map<String, List<PathDetail>> pathDetails = new HashMap<>();
        for (int i = 0; i < path.getLegs().size(); ++i) {
            Trip.Leg leg = path.getLegs().get(i);
            if (leg instanceof Trip.WalkLeg) {
                final Trip.WalkLeg walkLeg = ((Trip.WalkLeg) leg);
                List<Instruction> theseInstructions = walkLeg.instructions.subList(0, i < path.getLegs().size() - 1 ? walkLeg.instructions.size() - 1 : walkLeg.instructions.size());
                int previousPointsCount = pointsList.size();
                for (Instruction instruction : theseInstructions) {
                    pointsList.add(instruction.getPoints());
                }
                instructions.addAll(theseInstructions);
                Map<String, List<PathDetail>> shiftedLegPathDetails = shift(((Trip.WalkLeg) leg).details, previousPointsCount);
                shiftedLegPathDetails.forEach((k, v) -> pathDetails.merge(k, shiftedLegPathDetails.get(k), (a, b) -> Lists.newArrayList(Iterables.concat(a, b))));
            } else if (leg instanceof Trip.PtLeg) {
                final Trip.PtLeg ptLeg = ((Trip.PtLeg) leg);
                final PointList pl;
                if (!ptLeg.isInSameVehicleAsPrevious) {
                    pl = new PointList();
                    final Instruction departureInstruction = new Instruction(Instruction.PT_START_TRIP, ptLeg.trip_headsign, pl);
                    departureInstruction.setDistance(leg.getDistance());
                    departureInstruction.setTime(ptLeg.travelTime);
                    instructions.add(departureInstruction);
                } else {
                    pl = instructions.get(instructions.size() - 2).getPoints();
                }
                pl.add(ptLeg.stops.get(0).geometry.getY(), ptLeg.stops.get(0).geometry.getX());
                pointsList.add(ptLeg.stops.get(0).geometry.getY(), ptLeg.stops.get(0).geometry.getX());
                for (Trip.Stop stop : ptLeg.stops.subList(0, ptLeg.stops.size() - 1)) {
                    pl.add(stop.geometry.getY(), stop.geometry.getX());
                    pointsList.add(stop.geometry.getY(), stop.geometry.getX());
                }
                final PointList arrivalPointList = new PointList();
                final Trip.Stop arrivalStop = ptLeg.stops.get(ptLeg.stops.size() - 1);
                arrivalPointList.add(arrivalStop.geometry.getY(), arrivalStop.geometry.getX());
                pointsList.add(arrivalStop.geometry.getY(), arrivalStop.geometry.getX());
                Instruction arrivalInstruction = new Instruction(Instruction.PT_END_TRIP, arrivalStop.stop_name, arrivalPointList);
                if (ptLeg.isInSameVehicleAsPrevious) {
                    instructions.set(instructions.size() - 1, arrivalInstruction);
                } else {
                    instructions.add(arrivalInstruction);
                }
            }
        }
        path.setInstructions(instructions);
        path.setPoints(pointsList);
        path.addPathDetails(pathDetails);
        path.setDistance(path.getLegs().stream().mapToDouble(Trip.Leg::getDistance).sum());
        path.setTime((legs.get(legs.size() - 1).getArrivalTime().toInstant().toEpochMilli() - legs.get(0).getDepartureTime().toInstant().toEpochMilli()));
        path.setNumChanges((int) path.getLegs().stream()
                .filter(l -> l instanceof Trip.PtLeg)
                .filter(l -> !((Trip.PtLeg) l).isInSameVehicleAsPrevious)
                .count() - 1);
        com.graphhopper.gtfs.fare.Trip faresTrip = new com.graphhopper.gtfs.fare.Trip();
        path.getLegs().stream()
                .filter(leg -> leg instanceof Trip.PtLeg)
                .map(leg -> (Trip.PtLeg) leg)
                .findFirst()
                .ifPresent(firstPtLeg -> {
                    LocalDateTime firstPtDepartureTime = GtfsHelper.localDateTimeFromDate(firstPtLeg.getDepartureTime());
                    path.getLegs().stream()
                            .filter(leg -> leg instanceof Trip.PtLeg)
                            .map(leg -> (Trip.PtLeg) leg)
                            .map(ptLeg -> {
                                final GTFSFeed gtfsFeed = gtfsStorage.getGtfsFeeds().get(ptLeg.feed_id);
                                return new com.graphhopper.gtfs.fare.Trip.Segment(ptLeg.feed_id, ptLeg.route_id,
                                        Duration.between(firstPtDepartureTime, GtfsHelper.localDateTimeFromDate(ptLeg.getDepartureTime())).getSeconds(),
                                        gtfsFeed.stops.get(ptLeg.stops.get(0).stop_id).zone_id, gtfsFeed.stops.get(ptLeg.stops.get(ptLeg.stops.size() - 1).stop_id).zone_id,
                                        ptLeg.stops.stream().map(s -> gtfsFeed.stops.get(s.stop_id).zone_id).collect(Collectors.toSet()));
                            })
                            .forEach(faresTrip.segments::add);
                    Fares.cheapestFare(gtfsStorage.getFares(), faresTrip)
                            .ifPresent(amount -> path.setFare(amount.getAmount()));
                });
        return path;
    }

    private Map<String, List<PathDetail>> shift(Map<String, List<PathDetail>> pathDetailss, int previousPointsCount) {
        return Maps.transformEntries(pathDetailss, (s, pathDetails) -> pathDetails.stream().map(p -> {
            PathDetail pathDetail = new PathDetail(p.getValue());
            pathDetail.setFirst(p.getFirst() + previousPointsCount);
            pathDetail.setLast(p.getLast() + previousPointsCount);
            return pathDetail;
        }).collect(Collectors.toList()));
    }

    private List<List<Label.Transition>> parsePathToPartitions(List<Label.Transition> path) {
        List<List<Label.Transition>> partitions = new ArrayList<>();
        partitions.add(new ArrayList<>());
        final Iterator<Label.Transition> iterator = path.iterator();
        partitions.get(partitions.size() - 1).add(iterator.next());
        iterator.forEachRemaining(transition -> {
            final List<Label.Transition> previous = partitions.get(partitions.size() - 1);
            final GraphExplorer.MultiModalEdge previousEdge = previous.get(previous.size() - 1).edge;
            if (previousEdge != null && (transition.edge.getType() == GtfsStorage.EdgeType.ENTER_PT || previousEdge.getType() == GtfsStorage.EdgeType.EXIT_PT)) {
                final ArrayList<Label.Transition> p = new ArrayList<>();
                p.add(new Label.Transition(previous.get(previous.size() - 1).label, null));
                partitions.add(p);
            }
            partitions.get(partitions.size() - 1).add(transition);
        });
        return partitions;
    }

    private class StopsFromBoardHopDwellEdges {

        private final GtfsRealtime.TripDescriptor tripDescriptor;
        private final List<Trip.Stop> stops = new ArrayList<>();
        private final GTFSFeed gtfsFeed;
        private Instant boardTime;
        private Instant arrivalTimeFromHopEdge;
        private Optional<Instant> updatedArrival;
        private StopTime stopTime = null;
        private GtfsReader.TripWithStopTimes tripUpdate = null;
        private int stopSequence = 0;

        StopsFromBoardHopDwellEdges(String feedId, GtfsRealtime.TripDescriptor tripDescriptor) {
            this.tripDescriptor = tripDescriptor;
            this.gtfsFeed = gtfsStorage.getGtfsFeeds().get(feedId);
            if (this.tripUpdate != null) {
                validateTripUpdate(this.tripUpdate);
            }
        }

        void next(Label.Transition t) {
            switch (t.edge.getType()) {
                case BOARD: {
                    boardTime = Instant.ofEpochMilli(t.label.currentTime);
                    stopSequence = t.edge.getStopSequence();
                    stopTime = realtimeFeed.getStopTime(gtfsFeed, tripDescriptor, t, boardTime, stopSequence);
                    tripUpdate = realtimeFeed.getTripUpdate(gtfsFeed, tripDescriptor, boardTime).orElse(null);
                    Instant plannedDeparture = Instant.ofEpochMilli(t.label.currentTime);
                    Optional<Instant> updatedDeparture = getDepartureDelay(stopSequence).map(delay -> plannedDeparture.plus(delay, SECONDS));
                    Stop stop = gtfsFeed.stops.get(stopTime.stop_id);
                    stops.add(new Trip.Stop(stop.stop_id, stop.stop_name, geometryFactory.createPoint(new Coordinate(stop.stop_lon, stop.stop_lat)),
                            null, null, null, isArrivalCancelled(stopSequence),
                            updatedDeparture.map(Date::from).orElse(Date.from(plannedDeparture)), Date.from(plannedDeparture),
                            updatedDeparture.map(Date::from).orElse(null), isDepartureCancelled(stopSequence)));
                    break;
                }
                case HOP: {
                    stopSequence = t.edge.getStopSequence();
                    stopTime = realtimeFeed.getStopTime(gtfsFeed, tripDescriptor, t, boardTime, stopSequence);
                    arrivalTimeFromHopEdge = Instant.ofEpochMilli(t.label.currentTime);
                    updatedArrival = getArrivalDelay(stopSequence).map(delay -> arrivalTimeFromHopEdge.plus(delay, SECONDS));
                    break;
                }
                case DWELL: {
                    Instant plannedDeparture = Instant.ofEpochMilli(t.label.currentTime);
                    Optional<Instant> updatedDeparture = getDepartureDelay(stopTime.stop_sequence).map(delay -> plannedDeparture.plus(delay, SECONDS));
                    Stop stop = gtfsFeed.stops.get(stopTime.stop_id);
                    stops.add(new Trip.Stop(stop.stop_id, stop.stop_name, geometryFactory.createPoint(new Coordinate(stop.stop_lon, stop.stop_lat)),
                            updatedArrival.map(Date::from).orElse(Date.from(arrivalTimeFromHopEdge)), Date.from(arrivalTimeFromHopEdge),
                            updatedArrival.map(Date::from).orElse(null), isArrivalCancelled(stopSequence),
                            updatedDeparture.map(Date::from).orElse(Date.from(plannedDeparture)), Date.from(plannedDeparture),
                            updatedDeparture.map(Date::from).orElse(null), isDepartureCancelled(stopSequence)));
                    break;
                }
                default: {
                    throw new RuntimeException();
                }
            }
        }

        private Optional<Integer> getArrivalDelay(int stopSequence) {
            if (tripUpdate != null) {
                int arrival_time = tripUpdate.stopTimes.stream().filter(st -> st.stop_sequence == stopSequence).findFirst().orElseThrow(() -> new RuntimeException("Stop time not found.")).arrival_time;
                logger.trace("stop_sequence {} scheduled arrival {} updated arrival {}", stopSequence, stopTime.arrival_time, arrival_time);
                return Optional.of(arrival_time - stopTime.arrival_time);
            } else {
                return Optional.empty();
            }
        }

        private boolean isArrivalCancelled(int stopSequence) {
            if (tripUpdate != null) {
                return tripUpdate.cancelledArrivals.contains(stopSequence);
            } else {
                return false;
            }
        }

        private Optional<Integer> getDepartureDelay(int stopSequence) {
            if (tripUpdate != null) {
                int departure_time = tripUpdate.stopTimes.stream().filter(st -> st.stop_sequence == stopSequence).findFirst().orElseThrow(() -> new RuntimeException("Stop time not found.")).departure_time;
                logger.trace("stop_sequence {} scheduled departure {} updated departure {}", stopSequence, stopTime.departure_time, departure_time);
                return Optional.of(departure_time - stopTime.departure_time);
            } else {
                return Optional.empty();
            }
        }

        private boolean isDepartureCancelled(int stopSequence) {
            if (tripUpdate != null) {
                return tripUpdate.cancelledDeparture.contains(stopSequence);
            } else {
                return false;
            }
        }

        void finish() {
            Stop stop = gtfsFeed.stops.get(stopTime.stop_id);
            stops.add(new Trip.Stop(stop.stop_id, stop.stop_name, geometryFactory.createPoint(new Coordinate(stop.stop_lon, stop.stop_lat)),
                    updatedArrival.map(Date::from).orElse(Date.from(arrivalTimeFromHopEdge)), Date.from(arrivalTimeFromHopEdge),
                    updatedArrival.map(Date::from).orElse(null), isArrivalCancelled(stopSequence), null,
                    null, null, isDepartureCancelled(stopSequence)));
            for (Trip.Stop tripStop : stops) {
                logger.trace("{}", tripStop);
            }
        }

        private void validateTripUpdate(GtfsReader.TripWithStopTimes tripUpdate) {
            try {
                Iterable<StopTime> interpolatedStopTimesForTrip = gtfsFeed.getInterpolatedStopTimesForTrip(tripUpdate.trip.trip_id);
                long nStopTimes = StreamSupport.stream(interpolatedStopTimesForTrip.spliterator(), false).count();
                logger.trace("Original stop times: {} Updated stop times: {}", nStopTimes, tripUpdate.stopTimes.size());
                if (nStopTimes != tripUpdate.stopTimes.size()) {
                    logger.error("Original stop times: {} Updated stop times: {}", nStopTimes, tripUpdate.stopTimes.size());
                }
            } catch (GTFSFeed.FirstAndLastStopsDoNotHaveTimes firstAndLastStopsDoNotHaveTimes) {
                throw new RuntimeException(firstAndLastStopsDoNotHaveTimes);
            }
        }

    }

    // We are parsing a string of edges into a hierarchical trip.
    // One could argue that one should never write a parser
    // by hand, because it is always ugly, but use a parser library.
    // The code would then read like a specification of what paths through the graph mean.
    private List<Trip.Leg> parsePartitionToLegs(List<Label.Transition> path, Graph graph, EncodedValueLookup encodedValueLookup, Weighting weighting, Translation tr, List<String> requestedPathDetails) {
        if (path.size() <= 1) {
            return Collections.emptyList();
        }
        if (GtfsStorage.EdgeType.ENTER_PT == path.get(1).edge.getType()) {
            String feedId = path.get(1).edge.getPlatformDescriptor().feed_id;
            List<Trip.Leg> result = new ArrayList<>();
            long boardTime = -1;
            List<Label.Transition> partition = null;
            for (int i = 1; i < path.size(); i++) {
                Label.Transition transition = path.get(i);
                GraphExplorer.MultiModalEdge edge = path.get(i).edge;
                if (edge.getType() == GtfsStorage.EdgeType.BOARD) {
                    boardTime = transition.label.currentTime;
                    partition = new ArrayList<>();
                }
                if (partition != null) {
                    partition.add(path.get(i));
                }
                if (EnumSet.of(GtfsStorage.EdgeType.TRANSFER, GtfsStorage.EdgeType.LEAVE_TIME_EXPANDED_NETWORK).contains(edge.getType())) {
                    GtfsRealtime.TripDescriptor tripDescriptor = partition.get(0).edge.getTripDescriptor();
                    final StopsFromBoardHopDwellEdges stopsFromBoardHopDwellEdges = new StopsFromBoardHopDwellEdges(feedId, tripDescriptor);
                    partition.stream()
                            .filter(e -> EnumSet.of(GtfsStorage.EdgeType.HOP, GtfsStorage.EdgeType.BOARD, GtfsStorage.EdgeType.DWELL).contains(e.edge.getType()))
                            .forEach(stopsFromBoardHopDwellEdges::next);
                    stopsFromBoardHopDwellEdges.finish();
                    List<Trip.Stop> stops = stopsFromBoardHopDwellEdges.stops;

                    result.add(new Trip.PtLeg(
                            feedId, partition.get(0).edge.getTransfers() == 0,
                            tripDescriptor.getTripId(),
                            tripDescriptor.getRouteId(),
                            Optional.ofNullable(gtfsStorage.getGtfsFeeds().get(feedId).trips.get(tripDescriptor.getTripId())).map(t -> t.trip_headsign).orElse("extra"),
                            stops,
                            partition.stream().mapToDouble(t -> t.edge.getDistance()).sum(),
                            path.get(i - 1).label.currentTime - boardTime,
                            geometryFactory.createLineString(stops.stream().map(s -> s.geometry.getCoordinate()).toArray(Coordinate[]::new))));
                    partition = null;
                    if (edge.getType() == GtfsStorage.EdgeType.TRANSFER) {
                        feedId = edge.getPlatformDescriptor().feed_id;
                        int[] skippedEdgesForTransfer = gtfsStorage.getSkippedEdgesForTransfer().get(edge.getId());
                        if (skippedEdgesForTransfer != null) {
                            List<Trip.Leg> legs = parsePartitionToLegs(transferPath(skippedEdgesForTransfer, weighting, path.get(i - 1).label.currentTime), graph, encodedValueLookup, weighting, tr, requestedPathDetails);
                            result.add(legs.get(0));
                        }
                    }
                }
            }
            return result;
        } else {
            InstructionList instructions = new InstructionList(tr);
            InstructionsFromEdges instructionsFromEdges = new InstructionsFromEdges(graph,
                    weighting, encodedValueLookup, instructions);
            int prevEdgeId = -1;
            for (int i = 1; i < path.size(); i++) {
                if (path.get(i).edge.getType() != GtfsStorage.EdgeType.HIGHWAY) {
                    throw new IllegalStateException("Got a transit edge where I think I must be on a road.");
                }
                EdgeIteratorState edge = graph.getEdgeIteratorState(path.get(i).edge.getId(), path.get(i).label.node.streetNode);
                instructionsFromEdges.next(edge, i, prevEdgeId);
                prevEdgeId = edge.getEdge();
            }
            instructionsFromEdges.finish();

            Path pathh = new Path(graph);
            for (Label.Transition transition : path) {
                if (transition.edge != null)
                    pathh.addEdge(transition.edge.getId());
            }
            pathh.setFromNode(path.get(0).label.node.streetNode);
            pathh.setEndNode(path.get(path.size() - 1).label.node.streetNode);
            pathh.setFound(true);
            Map<String, List<PathDetail>> pathDetails = PathDetailsFromEdges.calcDetails(pathh, encodedValueLookup, weighting, requestedPathDetails, pathDetailsBuilderFactory, 0);

            final Instant departureTime = Instant.ofEpochMilli(path.get(0).label.currentTime);
            final Instant arrivalTime = Instant.ofEpochMilli(path.get(path.size() - 1).label.currentTime);
            return Collections.singletonList(new Trip.WalkLeg(
                    "Walk",
                    Date.from(departureTime),
                    lineStringFromInstructions(instructions),
                    edges(path).mapToDouble(edgeLabel -> edgeLabel.getDistance()).sum(),
                    instructions,
                    pathDetails,
                    Date.from(arrivalTime)));
        }
    }

    private List<Label.Transition> transferPath(int[] skippedEdgesForTransfer, Weighting accessEgressWeighting, long currentTime) {
        GraphExplorer graphExplorer = new GraphExplorer(graph, gtfsStorage.getPtGraph(), accessEgressWeighting, gtfsStorage, realtimeFeed, false, true, false, walkSpeedKmH, false, 0);
        return graphExplorer.walkPath(skippedEdgesForTransfer, currentTime);
    }

    private Stream<GraphExplorer.MultiModalEdge> edges(List<Label.Transition> path) {
        return path.stream().filter(t -> t.edge != null).map(t -> t.edge);
    }

    private Geometry lineStringFromInstructions(InstructionList instructions) {
        final PointList pointsList = new PointList();
        for (Instruction instruction : instructions) {
            pointsList.add(instruction.getPoints());
        }
        return pointsList.toLineString(false);
    }

}
