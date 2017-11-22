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

package com.graphhopper.reader.gtfs;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Stop;
import com.conveyal.gtfs.model.StopTime;
import com.graphhopper.PathWrapper;
import com.graphhopper.Trip;
import com.graphhopper.gtfs.fare.Fares;
import com.graphhopper.routing.InstructionsFromEdges;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.*;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import org.mapdb.Fun;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.graphhopper.reader.gtfs.Label.reverseEdges;

class TripFromLabel {

    PathWrapper parseSolutionIntoPath(boolean arriveBy, PtFlagEncoder encoder, Translation tr, GraphExplorer queryGraph, PtTravelTimeWeighting weighting, Label solution, PointList waypoints) {
        final List<Trip.Leg> legs = getTrip(arriveBy, encoder, tr, queryGraph, weighting, solution);
        return createPathWrapper(tr, waypoints, legs);
    }

    PathWrapper createPathWrapper(Translation tr, PointList waypoints, List<Trip.Leg> legs) {
        if (legs.size() > 1 && legs.get(0) instanceof Trip.WalkLeg) {
            final Trip.WalkLeg accessLeg = (Trip.WalkLeg) legs.get(0);
            legs.set(0, new Trip.WalkLeg(accessLeg.departureLocation, new Date(legs.get(1).departureTime.getTime() - (accessLeg.arrivalTime.getTime() - accessLeg.departureTime.getTime())), accessLeg.edges, accessLeg.geometry, accessLeg.distance, accessLeg.instructions, legs.get(1).departureTime));
        }
        if (legs.size() > 1 && legs.get(legs.size()-1) instanceof Trip.WalkLeg) {
            final Trip.WalkLeg egressLeg = (Trip.WalkLeg) legs.get(legs.size()-1);
            legs.set(legs.size()-1, new Trip.WalkLeg(egressLeg.departureLocation, legs.get(legs.size()-2).arrivalTime, egressLeg.edges, egressLeg.geometry, egressLeg.distance, egressLeg.instructions, new Date(legs.get(legs.size()-2).arrivalTime.getTime() + (egressLeg.arrivalTime.getTime() - egressLeg.departureTime.getTime()))));
        }

        PathWrapper path = new PathWrapper();
        path.setWaypoints(waypoints);

        path.getLegs().addAll(legs);

        final InstructionList instructions = getInstructions(tr, path.getLegs());
        path.setInstructions(instructions);
        PointList pointsList = new PointList();
        for (Instruction instruction : instructions) {
            pointsList.add(instruction.getPoints());
        }
        path.setPoints(pointsList);
        path.setDistance(path.getLegs().stream().mapToDouble(Trip.Leg::getDistance).sum());
        path.setTime((legs.get(legs.size()-1).arrivalTime.toInstant().toEpochMilli() - legs.get(0).departureTime.toInstant().toEpochMilli()));
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
                    LocalDateTime firstPtDepartureTime = GtfsHelper.localDateTimeFromDate(firstPtLeg.departureTime);
                    path.getLegs().stream()
                            .filter(leg -> leg instanceof Trip.PtLeg)
                            .map(leg -> (Trip.PtLeg) leg)
                            .map(ptLeg -> {
                                final GTFSFeed gtfsFeed = gtfsStorage.getGtfsFeeds().get(ptLeg.feed_id);
                                return new com.graphhopper.gtfs.fare.Trip.Segment(gtfsFeed.trips.get(ptLeg.trip_id).route_id, Duration.between(firstPtDepartureTime, GtfsHelper.localDateTimeFromDate(ptLeg.departureTime)).getSeconds(), gtfsFeed.stops.get(ptLeg.stops.get(0).stop_id).zone_id, gtfsFeed.stops.get(ptLeg.stops.get(ptLeg.stops.size() - 1).stop_id).zone_id, ptLeg.stops.stream().map(s -> gtfsFeed.stops.get(s.stop_id).zone_id).collect(Collectors.toSet()));
                            })
                            .forEach(faresTrip.segments::add);
                    Fares.cheapestFare(gtfsStorage.getFares(), faresTrip)
                            .ifPresent(amount -> path.setFare(amount.getAmount()));
                });
        return path;
    }

    List<Trip.Leg> getTrip(boolean arriveBy, PtFlagEncoder encoder, Translation tr, GraphExplorer queryGraph, PtTravelTimeWeighting weighting, Label solution) {
        List<Label.Transition> transitions = new ArrayList<>();
        if (arriveBy) {
            reverseEdges(solution, queryGraph, encoder, false)
                    .forEach(transitions::add);
        } else {
            reverseEdges(solution, queryGraph, encoder, true)
                    .forEach(transitions::add);
            Collections.reverse(transitions);
        }


        final List<List<Label.Transition>> partitions = getPartitions(transitions);
        final List<Trip.Leg> legs = getLegs(tr, queryGraph, weighting, partitions);
        return legs;
    }

    private List<List<Label.Transition>> getPartitions(List<Label.Transition> transitions) {
        List<List<Label.Transition>> partitions = new ArrayList<>();
        partitions.add(new ArrayList<>());
        final Iterator<Label.Transition> iterator = transitions.iterator();
        partitions.get(partitions.size()-1).add(iterator.next());
        iterator.forEachRemaining(transition -> {
            final List<Label.Transition> previous = partitions.get(partitions.size() - 1);
            final Label.EdgeLabel previousEdge = previous.get(previous.size() - 1).edge;
            if (previousEdge != null && (transition.edge.edgeType == GtfsStorage.EdgeType.ENTER_PT || previousEdge.edgeType == GtfsStorage.EdgeType.EXIT_PT)) {
                final ArrayList<Label.Transition> p = new ArrayList<>();
                p.add(new Label.Transition(previous.get(previous.size()-1).label, null));
                partitions.add(p);
            }
            partitions.get(partitions.size()-1).add(transition);
        });
        return partitions;
    }

    private List<Trip.Leg> getLegs(Translation tr, GraphExplorer queryGraph, PtTravelTimeWeighting weighting, List<List<Label.Transition>> partitions) {
        return partitions.stream().flatMap(partition -> parsePathIntoLegs(partition, queryGraph, weighting, tr).stream()).collect(Collectors.toList());
    }

    private InstructionList getInstructions(Translation tr, List<Trip.Leg> legs) {
        final InstructionList instructions = new InstructionList(tr);
        for (int i = 0; i< legs.size(); ++i) {
            Trip.Leg leg = legs.get(i);
            if (leg instanceof Trip.WalkLeg) {
                final Trip.WalkLeg walkLeg = ((Trip.WalkLeg) leg);
                instructions.addAll(walkLeg.instructions.subList(0, i < legs.size() - 1 ? walkLeg.instructions.size() - 1 : walkLeg.instructions.size()));
            } else if (leg instanceof Trip.PtLeg) {
                final Trip.PtLeg ptLeg = ((Trip.PtLeg) leg);
                final PointList pl;
                if (!ptLeg.isInSameVehicleAsPrevious) {
                    pl = new PointList();
                    final Instruction departureInstruction = new Instruction(Instruction.PT_START_TRIP, ptLeg.trip_headsign, InstructionAnnotation.EMPTY, pl);
                    departureInstruction.setDistance(leg.getDistance());
                    departureInstruction.setTime(ptLeg.travelTime);
                    instructions.add(departureInstruction);
                } else {
                    pl = instructions.get(instructions.size()-2).getPoints();
                }
                pl.add(ptLeg.stops.get(0).geometry.getY(), ptLeg.stops.get(0).geometry.getX());
                for (Trip.Stop stop : ptLeg.stops.subList(0, ptLeg.stops.size()-1)) {
                    pl.add(stop.geometry.getY(), stop.geometry.getX());
                }
                final PointList arrivalPointList = new PointList();
                final Trip.Stop arrivalStop = ptLeg.stops.get(ptLeg.stops.size()-1);
                arrivalPointList.add(arrivalStop.geometry.getY(), arrivalStop.geometry.getX());
                Instruction arrivalInstruction = new Instruction(Instruction.PT_END_TRIP, arrivalStop.stop_name, InstructionAnnotation.EMPTY, arrivalPointList);
                if (ptLeg.isInSameVehicleAsPrevious) {
                    instructions.replaceLast(arrivalInstruction);
                } else {
                    instructions.add(arrivalInstruction);
                }
            }
        }
        return instructions;
    }

    private class StopsFromBoardHopDwellEdges {

        private final String tripId;
        private final List<Trip.Stop> stops = new ArrayList<>();
        private final GTFSFeed gtfsFeed;
        private long arrivalTimeFromHopEdge;
        private Stop stop = null;

        StopsFromBoardHopDwellEdges(String feedId, String tripId) {
            this.tripId = tripId;
            this.gtfsFeed = gtfsStorage.getGtfsFeeds().get(feedId);
        }

        void next(Label.Transition t) {
            long departureTime;
            switch (t.edge.edgeType) {
                case BOARD:
                    stop = findStop(t);
                    departureTime = t.label.currentTime;
                    stops.add(new Trip.Stop(stop.stop_id, stop.stop_name, geometryFactory.createPoint(new Coordinate(stop.stop_lon, stop.stop_lat)), null, Date.from(Instant.ofEpochMilli(departureTime))));
                    break;
                case HOP:
                    stop = findStop(t);
                    arrivalTimeFromHopEdge = t.label.currentTime;
                    break;
                case DWELL:
                    departureTime = t.label.currentTime;
                    stops.add(new Trip.Stop(stop.stop_id, stop.stop_name, geometryFactory.createPoint(new Coordinate(stop.stop_lon, stop.stop_lat)), Date.from(Instant.ofEpochMilli(arrivalTimeFromHopEdge)), Date.from(Instant.ofEpochMilli(departureTime))));
                    break;
                default:
                    throw new RuntimeException();
            }
        }

        private Stop findStop(Label.Transition t) {
            int stopSequence = gtfsStorage.getStopSequences().get(t.edge.edgeIteratorState.getEdge());
            StopTime stopTime = gtfsFeed.stop_times.get(new Fun.Tuple2<>(tripId, stopSequence));
            return gtfsFeed.stops.get(stopTime.stop_id);
        }

        void finish() {
            stops.add(new Trip.Stop(stop.stop_id, stop.stop_name, geometryFactory.createPoint(new Coordinate(stop.stop_lon, stop.stop_lat)), Date.from(Instant.ofEpochMilli(arrivalTimeFromHopEdge)), null));
        }

    }


    private final GtfsStorage gtfsStorage;
    private final GeometryFactory geometryFactory = new GeometryFactory();

    TripFromLabel(GtfsStorage gtfsStorage) {

        this.gtfsStorage = gtfsStorage;
    }

    // We are parsing a string of edges into a hierarchical trip.
    // One could argue that one should never write a parser
    // by hand, because it is always ugly, but use a parser library.
    // The code would then read like a specification of what paths through the graph mean.
    private List<Trip.Leg> parsePathIntoLegs(List<Label.Transition> path, GraphExplorer graph, Weighting weighting, Translation tr) {
        if (path.size() <= 1) {
            return Collections.emptyList();
        }
        if (GtfsStorage.EdgeType.ENTER_PT == path.get(1).edge.edgeType) {
            final GtfsStorage.FeedIdWithTimezone feedIdWithTimezone = gtfsStorage.getTimeZones().get(path.get(1).edge.timeZoneId);
            final GTFSFeed gtfsFeed = gtfsStorage.getGtfsFeeds().get(feedIdWithTimezone.feedId);
            List<Trip.Leg> result = new ArrayList<>();
            long boardTime = -1;
            List<Label.Transition> partition = null;
            for (int i = 1; i < path.size(); i++) {
                Label.Transition transition = path.get(i);
                Label.EdgeLabel edge = path.get(i).edge;
                if (edge.edgeType == GtfsStorage.EdgeType.BOARD) {
                    boardTime = transition.label.currentTime;
                    partition = new ArrayList<>();
                }
                if (partition != null) {
                    partition.add(path.get(i));
                }
                if (EnumSet.of(GtfsStorage.EdgeType.TRANSFER, GtfsStorage.EdgeType.LEAVE_TIME_EXPANDED_NETWORK).contains(edge.edgeType)) {
                    Geometry lineString = lineStringFromEdges(partition);
                    String tripId = gtfsStorage.getExtraStrings().get(partition.get(0).edge.edgeIteratorState.getEdge());
                    final StopsFromBoardHopDwellEdges stopsFromBoardHopDwellEdges = new StopsFromBoardHopDwellEdges(feedIdWithTimezone.feedId, tripId);
                    partition.stream()
                            .filter(e -> EnumSet.of(GtfsStorage.EdgeType.HOP, GtfsStorage.EdgeType.BOARD, GtfsStorage.EdgeType.DWELL).contains(e.edge.edgeType))
                            .forEach(stopsFromBoardHopDwellEdges::next);
                    stopsFromBoardHopDwellEdges.finish();
                    List<Trip.Stop> stops = stopsFromBoardHopDwellEdges.stops;

                    com.conveyal.gtfs.model.Trip trip = gtfsFeed.trips.get(tripId);
                    result.add(new Trip.PtLeg(
                            feedIdWithTimezone.feedId,partition.get(0).edge.nTransfers == 0,
                            tripId,
                            trip.route_id,
                            edges(partition).map(edgeLabel -> edgeLabel.edgeIteratorState).collect(Collectors.toList()),
                            new Date(boardTime),
                            stops,
                            partition.stream().mapToDouble(t -> t.edge.distance).sum(),
                            path.get(i-1).label.currentTime - boardTime,
                            new Date(path.get(i-1).label.currentTime),
                            lineString));
                    partition = null;
                }
            }
            return result;
        } else {
            InstructionList instructions = new InstructionList(tr);
            InstructionsFromEdges instructionsFromEdges = new InstructionsFromEdges(path.get(1).edge.edgeIteratorState.getBaseNode(), graph.getGraph(), weighting, weighting.getFlagEncoder(), graph.getNodeAccess(), tr, instructions);
            int prevEdgeId = -1;
            for (int i=1; i<path.size(); i++) {
                EdgeIteratorState edge = path.get(i).edge.edgeIteratorState;
                instructionsFromEdges.next(edge, i, prevEdgeId);
                prevEdgeId = edge.getEdge();
            }
            instructionsFromEdges.finish();
            final Instant departureTime = Instant.ofEpochMilli(path.get(0).label.currentTime);
            final Instant arrivalTime = Instant.ofEpochMilli(path.get(path.size() - 1).label.currentTime);
            return Collections.singletonList(new Trip.WalkLeg(
                    "Walk",
                    Date.from(departureTime),
                    edges(path).map(edgeLabel -> edgeLabel.edgeIteratorState).collect(Collectors.toList()),
                    lineStringFromEdges(path),
                    edges(path).mapToDouble(edgeLabel -> edgeLabel.distance).sum(),
                    instructions,
                    Date.from(arrivalTime)));
        }
    }

    private Stream<Label.EdgeLabel> edges(List<Label.Transition> path) {
        return path.stream().filter(t -> t.edge != null).map(t -> t.edge);
    }

    private Geometry lineStringFromEdges(List<Label.Transition> transitions) {
        List<Coordinate> coordinates = new ArrayList<>();
        final Iterator<Label.Transition> iterator = transitions.iterator();
        iterator.next();
        coordinates.addAll(toCoordinateArray(iterator.next().edge.edgeIteratorState.fetchWayGeometry(3)));
        iterator.forEachRemaining(transition -> coordinates.addAll(toCoordinateArray(transition.edge.edgeIteratorState.fetchWayGeometry(2))));
        return geometryFactory.createLineString(coordinates.toArray(new Coordinate[coordinates.size()]));
    }


    private static List<Coordinate> toCoordinateArray(PointList pointList) {
        List<Coordinate> coordinates = new ArrayList<>(pointList.size());
        for (int i=0; i<pointList.size(); i++) {
            coordinates.add(pointList.getDimension() == 3 ?
                    new Coordinate(pointList.getLon(i), pointList.getLat(i)) :
                    new Coordinate(pointList.getLon(i), pointList.getLat(i), pointList.getEle(i)));
        }
        return coordinates;
    }



}
