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

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntHashSet;
import com.carrotsearch.hppc.IntLongHashMap;
import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Frequency;
import com.conveyal.gtfs.model.StopTime;
import com.conveyal.gtfs.model.Trip;
import com.google.transit.realtime.GtfsRealtime;
import org.mapdb.Fun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.NO_DATA;
import static com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.SKIPPED;
import static java.time.temporal.ChronoUnit.DAYS;

public class RealtimeFeed {
    private static final Logger logger = LoggerFactory.getLogger(RealtimeFeed.class);
    private final IntHashSet blockedEdges;
    private final IntLongHashMap delaysForBoardEdges;
    private final IntLongHashMap delaysForAlightEdges;
    private final TreeSet<PtGraph.PtEdge> additionalEdgesByBaseNode;
    private final TreeSet<PtGraph.PtEdge> additionalEdgesByAdjNode;
    public final Map<String, GtfsRealtime.FeedMessage> feedMessages;

    private RealtimeFeed(Map<String, GtfsRealtime.FeedMessage> feedMessages, IntHashSet blockedEdges,
                         IntLongHashMap delaysForBoardEdges, IntLongHashMap delaysForAlightEdges, List<PtGraph.PtEdge> additionalEdges) {
        this.feedMessages = feedMessages;
        this.blockedEdges = blockedEdges;
        this.delaysForBoardEdges = delaysForBoardEdges;
        this.delaysForAlightEdges = delaysForAlightEdges;
        this.additionalEdgesByBaseNode = new TreeSet<>(Comparator.comparingInt(PtGraph.PtEdge::getBaseNode).thenComparingInt(PtGraph.PtEdge::getId));
        this.additionalEdgesByBaseNode.addAll(additionalEdges);
        this.additionalEdgesByAdjNode = new TreeSet<>(Comparator.comparingInt(PtGraph.PtEdge::getAdjNode).thenComparingInt(PtGraph.PtEdge::getId));
        this.additionalEdgesByAdjNode.addAll(additionalEdges);
    }

    public static RealtimeFeed empty() {
        return new RealtimeFeed(Collections.emptyMap(), new IntHashSet(), new IntLongHashMap(), new IntLongHashMap(), Collections.emptyList());
    }

    public static RealtimeFeed fromProtobuf(GtfsStorage staticGtfs, Map<String, Transfers> transfers, Map<String, GtfsRealtime.FeedMessage> feedMessages) {
        final IntHashSet blockedEdges = new IntHashSet();
        final IntLongHashMap delaysForBoardEdges = new IntLongHashMap();
        final IntLongHashMap delaysForAlightEdges = new IntLongHashMap();
        final LinkedList<PtGraph.PtEdge> additionalEdges = new LinkedList<>();
        final GtfsReader.PtGraphOut overlayGraph = new GtfsReader.PtGraphOut() {
            int nextEdge = staticGtfs.getPtGraph().getEdgeCount();
            int nextNode = staticGtfs.getPtGraph().getNodeCount();

            @Override
            public int createEdge(int src, int dest, PtEdgeAttributes attrs) {
                int edgeId = nextEdge++;
                PtGraph.PtEdge e = new PtGraph.PtEdge(edgeId, src, dest, attrs);
                assert canBeAdded(e);
                additionalEdges.add(e);
                return edgeId;
            }

            private boolean canBeAdded(PtGraph.PtEdge e) {
                if (e.getType() != GtfsStorage.EdgeType.ENTER_PT) {
                    if (staticGtfs.getPtToStreet().containsKey(e.getBaseNode())) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public int createNode() {
                return nextNode++;
            }

        };

        feedMessages.forEach((feedKey, feedMessage) -> {
            GTFSFeed feed = staticGtfs.getGtfsFeeds().get(feedKey);
            ZoneId timezone = ZoneId.of(feed.agency.values().stream().findFirst().get().agency_timezone);
            PtGraph ptGraphNodesAndEdges = staticGtfs.getPtGraph();
            final GtfsReader gtfsReader = new GtfsReader(feedKey, ptGraphNodesAndEdges, overlayGraph, staticGtfs, null, transfers.get(feedKey), null);
            Instant timestamp = Instant.ofEpochSecond(feedMessage.getHeader().getTimestamp());
            LocalDate dateToChange = timestamp.atZone(timezone).toLocalDate(); //FIXME
            BitSet validOnDay = new BitSet();
            LocalDate startDate = feed.getStartDate();
            validOnDay.set((int) DAYS.between(startDate, dateToChange));
            feedMessage.getEntityList().stream()
                    .filter(GtfsRealtime.FeedEntity::hasTripUpdate)
                    .map(GtfsRealtime.FeedEntity::getTripUpdate)
                    .filter(tripUpdate -> tripUpdate.getTrip().getScheduleRelationship() == GtfsRealtime.TripDescriptor.ScheduleRelationship.SCHEDULED)
                    .forEach(tripUpdate -> maybeUpdateScheduledTrip(staticGtfs, feedKey, tripUpdate, feed, blockedEdges, delaysForAlightEdges, ptGraphNodesAndEdges, gtfsReader, timezone, validOnDay, delaysForBoardEdges));
            feedMessage.getEntityList().stream()
                    .filter(GtfsRealtime.FeedEntity::hasTripUpdate)
                    .map(GtfsRealtime.FeedEntity::getTripUpdate)
                    .filter(tripUpdate -> tripUpdate.getTrip().getScheduleRelationship() == GtfsRealtime.TripDescriptor.ScheduleRelationship.ADDED)
                    .forEach(tripUpdate -> maybeAddExtraTrip(staticGtfs, feedKey, tripUpdate, timezone, validOnDay, gtfsReader));
            gtfsReader.wireUpAdditionalDeparturesAndArrivals(timezone);
        });

        return new RealtimeFeed(feedMessages, blockedEdges, delaysForBoardEdges, delaysForAlightEdges, additionalEdges);
    }

    private static void maybeUpdateScheduledTrip(GtfsStorage staticGtfs, String feedKey, GtfsRealtime.TripUpdate tripUpdate, GTFSFeed feed, IntHashSet blockedEdges, IntLongHashMap delaysForAlightEdges, PtGraph ptGraphNodesAndEdges, GtfsReader gtfsReader, ZoneId timezone, BitSet validOnDay, IntLongHashMap delaysForBoardEdges) {
        Collection<Frequency> frequencies = feed.getFrequencies(tripUpdate.getTrip().getTripId());
        int timeOffset = (tripUpdate.getTrip().hasStartTime() && !frequencies.isEmpty()) ? LocalTime.parse(tripUpdate.getTrip().getStartTime()).toSecondOfDay() : 0;
        final int[] boardEdges = findBoardEdgesForTrip(staticGtfs, feedKey, feed, tripUpdate.getTrip());
        final int[] leaveEdges = findAlightEdgesForTrip(staticGtfs, feedKey, feed, tripUpdate.getTrip());
        if (boardEdges == null || leaveEdges == null) {
            logger.warn("Trip not found: {}", tripUpdate.getTrip());
            return;
        }
        tripUpdate.getStopTimeUpdateList().stream()
                .filter(stopTimeUpdate -> stopTimeUpdate.getScheduleRelationship() == SKIPPED)
                .mapToInt(GtfsRealtime.TripUpdate.StopTimeUpdate::getStopSequence)
                .forEach(skippedStopSequenceNumber -> {
                    blockedEdges.add(boardEdges[skippedStopSequenceNumber]);
                    blockedEdges.add(leaveEdges[skippedStopSequenceNumber]);
                });
        GtfsReader.TripWithStopTimes tripWithStopTimes = toTripWithStopTimes(feed, tripUpdate);
        tripWithStopTimes.stopTimes.forEach(stopTime -> {
            if (stopTime.stop_sequence > leaveEdges.length - 1) {
                logger.warn("Stop sequence number too high {} vs {}", stopTime.stop_sequence, leaveEdges.length);
                return;
            }
            final StopTime originalStopTime = feed.stop_times.get(new Fun.Tuple2(tripUpdate.getTrip().getTripId(), stopTime.stop_sequence));
            int arrivalDelay = stopTime.arrival_time - originalStopTime.arrival_time;
            delaysForAlightEdges.put(leaveEdges[stopTime.stop_sequence], arrivalDelay * 1000);
            int departureDelay = stopTime.departure_time - originalStopTime.departure_time;
            if (departureDelay > 0) {
                int boardEdge = boardEdges[stopTime.stop_sequence];
                int departureNode = ptGraphNodesAndEdges.edge(boardEdge).getAdjNode();
                int delayedBoardEdge = gtfsReader.addDelayedBoardEdge(timezone, tripUpdate.getTrip(), stopTime.stop_sequence, stopTime.departure_time + timeOffset, departureNode, validOnDay);
                delaysForBoardEdges.put(delayedBoardEdge, departureDelay * 1000);
            }
        });
    }

    private static void maybeAddExtraTrip(GtfsStorage staticGtfs, String feedKey, GtfsRealtime.TripUpdate tripUpdate, ZoneId timezone, BitSet validOnDay, GtfsReader gtfsReader) {
        GTFSFeed feed = staticGtfs.getGtfsFeeds().get(feedKey);
        Trip trip = new Trip();
        trip.trip_id = tripUpdate.getTrip().getTripId();
        Trip existingTrip = feed.trips.get(trip.trip_id);
        if (existingTrip != null) {
            trip.route_id = existingTrip.route_id;
        } else if (tripUpdate.getTrip().hasRouteId() && feed.routes.containsKey(tripUpdate.getTrip().getRouteId())) {
            trip.route_id = tripUpdate.getTrip().getRouteId();
        } else {
            logger.error("We need to know at least a valid route id for ADDED trip {}", trip.trip_id);
            return;
        }
        final List<StopTime> stopTimes = tripUpdate.getStopTimeUpdateList().stream()
                .map(stopTimeUpdate -> {
                    final StopTime stopTime = new StopTime();
                    stopTime.stop_sequence = stopTimeUpdate.getStopSequence();
                    stopTime.stop_id = stopTimeUpdate.getStopId();
                    stopTime.trip_id = trip.trip_id;
                    final ZonedDateTime arrival_time = Instant.ofEpochSecond(stopTimeUpdate.getArrival().getTime()).atZone(timezone);
                    stopTime.arrival_time = (int) Duration.between(arrival_time.truncatedTo(ChronoUnit.DAYS), arrival_time).getSeconds();
                    final ZonedDateTime departure_time = Instant.ofEpochSecond(stopTimeUpdate.getArrival().getTime()).atZone(timezone);
                    stopTime.departure_time = (int) Duration.between(departure_time.truncatedTo(ChronoUnit.DAYS), departure_time).getSeconds();
                    return stopTime;
                })
                .collect(Collectors.toList());
        if (stopTimes.stream().anyMatch(stopTime -> !feed.stops.containsKey(stopTime.stop_id))) {
            logger.error("ADDED trip {} contains unknown stop id", trip.trip_id);
            return;
        }
        GtfsReader.TripWithStopTimes tripWithStopTimes = new GtfsReader.TripWithStopTimes(trip, stopTimes, validOnDay, Collections.emptySet(), Collections.emptySet());
        gtfsReader.addTrip(timezone, 0, new ArrayList<>(), tripWithStopTimes, tripUpdate.getTrip());
    }

    public static int[] findAlightEdgesForTrip(GtfsStorage staticGtfs, String feedKey, GTFSFeed feed, GtfsRealtime.TripDescriptor tripDescriptor) {
        Trip trip = feed.trips.get(tripDescriptor.getTripId());
        StopTime next = feed.getOrderedStopTimesForTrip(trip.trip_id).iterator().next();
        int station = staticGtfs.getStationNodes().get(new GtfsStorage.FeedIdWithStopId(feedKey, next.stop_id));
        Optional<PtGraph.PtEdge> firstAlighting = StreamSupport.stream(staticGtfs.getPtGraph().backEdgesAround(station).spliterator(), false)
                .flatMap(e -> StreamSupport.stream(staticGtfs.getPtGraph().backEdgesAround(e.getAdjNode()).spliterator(), false))
                .flatMap(e -> StreamSupport.stream(staticGtfs.getPtGraph().backEdgesAround(e.getAdjNode()).spliterator(), false))
                .filter(e -> e.getType() == GtfsStorage.EdgeType.ALIGHT)
                .filter(e -> isDescribedBy(e.getAttrs().tripDescriptor, tripDescriptor))
                .min(Comparator.comparingInt(e -> e.getAttrs().stop_sequence));
        if (firstAlighting.isEmpty()) {
            return null;
        }
        int n = firstAlighting.get().getAdjNode();
        Stream<PtGraph.PtEdge> leaveEdges = evenIndexed(nodes(hopDwellChain(staticGtfs, n)))
                .mapToObj(e -> alightForBaseNode(staticGtfs, e));
        return collectWithPadding(leaveEdges);
    }

    public static int[] findBoardEdgesForTrip(GtfsStorage staticGtfs, String feedKey, GTFSFeed feed, GtfsRealtime.TripDescriptor tripDescriptor) {
        Trip trip = feed.trips.get(tripDescriptor.getTripId());
        StopTime next = feed.getOrderedStopTimesForTrip(trip.trip_id).iterator().next();
        int station = staticGtfs.getStationNodes().get(new GtfsStorage.FeedIdWithStopId(feedKey, next.stop_id));
        Optional<PtGraph.PtEdge> firstBoarding = StreamSupport.stream(staticGtfs.getPtGraph().edgesAround(station).spliterator(), false)
                .flatMap(e -> StreamSupport.stream(staticGtfs.getPtGraph().edgesAround(e.getAdjNode()).spliterator(), false))
                .flatMap(e -> StreamSupport.stream(staticGtfs.getPtGraph().edgesAround(e.getAdjNode()).spliterator(), false))
                .filter(e -> e.getType() == GtfsStorage.EdgeType.BOARD)
                .filter(e -> isDescribedBy(e.getAttrs().tripDescriptor, tripDescriptor))
                .min(Comparator.comparingInt(e -> e.getAttrs().stop_sequence));
        if (firstBoarding.isEmpty()) {
            return null;
        }
        int n = firstBoarding.get().getAdjNode();
        Stream<PtGraph.PtEdge> boardEdges = evenIndexed(nodes(hopDwellChain(staticGtfs, n)))
                .mapToObj(e -> boardForAdjNode(staticGtfs, e));
        return collectWithPadding(boardEdges);
    }

    private static boolean isDescribedBy(GtfsRealtime.TripDescriptor a, GtfsRealtime.TripDescriptor b) {
        // a is a descriptor of a trip in our database, static or realtime
        // b is a descriptor of a trip in a trip update in the literal current rt feed
        if (a.hasTripId() && !a.getTripId().equals(b.getTripId())) {
            return false;
        }
        if (a.hasStartTime() && !a.getStartTime().equals(b.getStartTime())) {
            return false;
        }
        return true;
    }

    public static Stream<PtGraph.PtEdge> findAllBoardings(GtfsStorage staticGtfs, GtfsStorage.FeedIdWithStopId feedIdWithStopId) {
        Integer station = staticGtfs.getStationNodes().get(feedIdWithStopId);
        if (station == null)
            return Stream.empty();
        else
            return StreamSupport.stream(staticGtfs.getPtGraph().edgesAround(station).spliterator(), false)
                    .flatMap(e -> StreamSupport.stream(staticGtfs.getPtGraph().edgesAround(e.getAdjNode()).spliterator(), false))
                    .filter(e -> e.getAttrs().feedIdWithTimezone.feedId.equals(feedIdWithStopId.feedId))
                    .flatMap(e -> StreamSupport.stream(staticGtfs.getPtGraph().edgesAround(e.getAdjNode()).spliterator(), false))
                    .filter(e -> e.getType() == GtfsStorage.EdgeType.BOARD);
    }

    private static int[] collectWithPadding(Stream<PtGraph.PtEdge> boardEdges) {
        IntArrayList result = new IntArrayList();
        boardEdges.forEach(boardEdge -> {
            while (result.size() < boardEdge.getAttrs().stop_sequence) {
                result.add(-1); // Padding, so that index == stop_sequence
            }
            result.add(boardEdge.getId());
        });
        return result.toArray();
    }

    private static PtGraph.PtEdge alightForBaseNode(GtfsStorage staticGtfs, int n) {
        return StreamSupport.stream(staticGtfs.getPtGraph().edgesAround(n).spliterator(), false)
                .filter(e -> e.getType() == GtfsStorage.EdgeType.ALIGHT)
                .findAny()
                .get();
    }

    private static PtGraph.PtEdge boardForAdjNode(GtfsStorage staticGtfs, int n) {
        return StreamSupport.stream(staticGtfs.getPtGraph().backEdgesAround(n).spliterator(), false)
                .filter(e -> e.getType() == GtfsStorage.EdgeType.BOARD)
                .findAny()
                .get();
    }

    private static IntStream evenIndexed(IntStream nodes) {
        int[] ints = nodes.toArray();
        IntStream.Builder builder = IntStream.builder();
        for (int i = 0; i < ints.length; i++) {
            if (i % 2 == 0)
                builder.add(ints[i]);
        }
        return builder.build();
    }

    private static IntStream nodes(Stream<PtGraph.PtEdge> path) {
        List<PtGraph.PtEdge> edges = path.collect(Collectors.toList());
        IntStream.Builder builder = IntStream.builder();
        builder.accept(edges.get(0).getBaseNode());
        for (PtGraph.PtEdge edge : edges) {
            builder.accept(edge.getAdjNode());
        }
        return builder.build();
    }

    private static Stream<PtGraph.PtEdge> hopDwellChain(GtfsStorage staticGtfs, int n) {
        Stream.Builder<PtGraph.PtEdge> builder = Stream.builder();
        Optional<PtGraph.PtEdge> any = StreamSupport.stream(staticGtfs.getPtGraph().edgesAround(n).spliterator(), false)
                .filter(e -> e.getType() == GtfsStorage.EdgeType.HOP || e.getType() == GtfsStorage.EdgeType.DWELL)
                .findAny();
        while (any.isPresent()) {
            builder.accept(any.get());
            any = StreamSupport.stream(staticGtfs.getPtGraph().edgesAround(any.get().getAdjNode()).spliterator(), false)
                    .filter(e -> e.getType() == GtfsStorage.EdgeType.HOP || e.getType() == GtfsStorage.EdgeType.DWELL)
                    .findAny();
        }
        return builder.build();
    }

    boolean isBlocked(int edgeId) {
        return blockedEdges.contains(edgeId);
    }

    SortedSet<PtGraph.PtEdge> getAdditionalEdgesFrom(int node) {
        return additionalEdgesByBaseNode.subSet(new PtGraph.PtEdge(0, node, 0, null), new PtGraph.PtEdge(0, node+1, 0, null));
    }

    SortedSet<PtGraph.PtEdge> getAdditionalEdgesTo(int node) {
        return additionalEdgesByAdjNode.subSet(new PtGraph.PtEdge(0, 0, node, null), new PtGraph.PtEdge(0, 0, node+1, null));
    }

    public Optional<GtfsReader.TripWithStopTimes> getTripUpdate(GTFSFeed staticFeed, GtfsRealtime.TripDescriptor trip, Instant boardTime) {
        try {
            logger.trace("getTripUpdate {}", trip);
            if (!isThisRealtimeUpdateAboutThisLineRun(boardTime)) {
                return Optional.empty();
            } else {
                return feedMessages.values().stream().flatMap(feedMessage -> feedMessage.getEntityList().stream()
                        .filter(e -> e.hasTripUpdate())
                        .map(e -> e.getTripUpdate())
                        .filter(tu -> isDescribedBy(trip, tu.getTrip()))
                        .map(tu -> toTripWithStopTimes(staticFeed, tu)))
                        .findFirst();
            }
        } catch (RuntimeException e) {
            feedMessages.forEach((name, feed) -> {
                try (OutputStream s = new FileOutputStream(name+".gtfsdump")) {
                    feed.writeTo(s);
                } catch (IOException e1) {
                    throw new RuntimeException();
                }
            });
            return Optional.empty();
        }
    }

    public static GtfsReader.TripWithStopTimes toTripWithStopTimes(GTFSFeed feed, GtfsRealtime.TripUpdate tripUpdate) {
        ZoneId timezone = ZoneId.of(feed.agency.values().stream().findFirst().get().agency_timezone);
        logger.trace("{}", tripUpdate.getTrip());
        final List<StopTime> stopTimes = new ArrayList<>();
        Set<Integer> cancelledArrivals = new HashSet<>();
        Set<Integer> cancelledDepartures = new HashSet<>();
        Trip originalTrip = feed.trips.get(tripUpdate.getTrip().getTripId());
        Trip trip = new Trip();
        if (originalTrip != null) {
            trip.trip_id = originalTrip.trip_id;
            trip.route_id = originalTrip.route_id;
        } else {
            trip.trip_id = tripUpdate.getTrip().getTripId();
            trip.route_id = tripUpdate.getTrip().getRouteId();
        }
        int delay = 0;
        int time = -1;
        List<GtfsRealtime.TripUpdate.StopTimeUpdate> stopTimeUpdateListWithSentinel = new ArrayList<>(tripUpdate.getStopTimeUpdateList());
        Iterable<StopTime> interpolatedStopTimesForTrip;
        try {
            interpolatedStopTimesForTrip = feed.getInterpolatedStopTimesForTrip(tripUpdate.getTrip().getTripId());
        } catch (GTFSFeed.FirstAndLastStopsDoNotHaveTimes firstAndLastStopsDoNotHaveTimes) {
            throw new RuntimeException(firstAndLastStopsDoNotHaveTimes);
        }
        int stopSequenceCeiling = Math.max(stopTimeUpdateListWithSentinel.isEmpty() ? 0 : stopTimeUpdateListWithSentinel.get(stopTimeUpdateListWithSentinel.size() - 1).getStopSequence(),
                StreamSupport.stream(interpolatedStopTimesForTrip.spliterator(), false).mapToInt(stopTime -> stopTime.stop_sequence).max().orElse(0)
        ) + 1;
        stopTimeUpdateListWithSentinel.add(GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder().setStopSequence(stopSequenceCeiling).setScheduleRelationship(NO_DATA).build());
        for (GtfsRealtime.TripUpdate.StopTimeUpdate stopTimeUpdate : stopTimeUpdateListWithSentinel) {
            int nextStopSequence = stopTimes.isEmpty() ? 1 : stopTimes.get(stopTimes.size() - 1).stop_sequence + 1;
            for (int i = nextStopSequence; i < stopTimeUpdate.getStopSequence(); i++) {
                StopTime previousOriginalStopTime = feed.stop_times.get(new Fun.Tuple2(tripUpdate.getTrip().getTripId(), i));
                if (previousOriginalStopTime == null) {
                    continue; // This can and does happen. Stop sequence numbers can be left out.
                }
                StopTime updatedPreviousStopTime = previousOriginalStopTime.clone();
                updatedPreviousStopTime.arrival_time = Math.max(previousOriginalStopTime.arrival_time + delay, time);
                logger.trace("stop_sequence {} scheduled arrival {} updated arrival {}", i, previousOriginalStopTime.arrival_time, updatedPreviousStopTime.arrival_time);
                time = updatedPreviousStopTime.arrival_time;
                updatedPreviousStopTime.departure_time = Math.max(previousOriginalStopTime.departure_time + delay, time);
                logger.trace("stop_sequence {} scheduled departure {} updated departure {}", i, previousOriginalStopTime.departure_time, updatedPreviousStopTime.departure_time);
                time = updatedPreviousStopTime.departure_time;
                stopTimes.add(updatedPreviousStopTime);
                logger.trace("Number of stop times: {}", stopTimes.size());
            }

            final StopTime originalStopTime = feed.stop_times.get(new Fun.Tuple2(tripUpdate.getTrip().getTripId(), stopTimeUpdate.getStopSequence()));
            if (originalStopTime != null) {
                StopTime updatedStopTime = originalStopTime.clone();
                if (stopTimeUpdate.getScheduleRelationship() == NO_DATA) {
                    delay = 0;
                }
                if (stopTimeUpdate.hasArrival()) {
                    delay = stopTimeUpdate.getArrival().getDelay();
                }
                updatedStopTime.arrival_time = Math.max(originalStopTime.arrival_time + delay, time);
                logger.trace("stop_sequence {} scheduled arrival {} updated arrival {}", stopTimeUpdate.getStopSequence(), originalStopTime.arrival_time, updatedStopTime.arrival_time);
                time = updatedStopTime.arrival_time;
                if (stopTimeUpdate.hasDeparture()) {
                    delay = stopTimeUpdate.getDeparture().getDelay();
                }
                updatedStopTime.departure_time = Math.max(originalStopTime.departure_time + delay, time);
                logger.trace("stop_sequence {} scheduled departure {} updated departure {}", stopTimeUpdate.getStopSequence(), originalStopTime.departure_time, updatedStopTime.departure_time);
                time = updatedStopTime.departure_time;
                stopTimes.add(updatedStopTime);
                logger.trace("Number of stop times: {}", stopTimes.size());
                if (stopTimeUpdate.getScheduleRelationship() == SKIPPED) {
                    cancelledArrivals.add(stopTimeUpdate.getStopSequence());
                    cancelledDepartures.add(stopTimeUpdate.getStopSequence());
                }
            } else if (stopTimeUpdate.getScheduleRelationship() == NO_DATA) {
            } else if (tripUpdate.getTrip().getScheduleRelationship() == GtfsRealtime.TripDescriptor.ScheduleRelationship.ADDED) {
                final StopTime stopTime = new StopTime();
                stopTime.stop_sequence = stopTimeUpdate.getStopSequence();
                stopTime.stop_id = stopTimeUpdate.getStopId();
                stopTime.trip_id = trip.trip_id;
                final ZonedDateTime arrival_time = Instant.ofEpochSecond(stopTimeUpdate.getArrival().getTime()).atZone(timezone);
                stopTime.arrival_time = (int) Duration.between(arrival_time.truncatedTo(ChronoUnit.DAYS), arrival_time).getSeconds();
                final ZonedDateTime departure_time = Instant.ofEpochSecond(stopTimeUpdate.getArrival().getTime()).atZone(timezone);
                stopTime.departure_time = (int) Duration.between(departure_time.truncatedTo(ChronoUnit.DAYS), departure_time).getSeconds();
                stopTimes.add(stopTime);
                logger.trace("Number of stop times: {}", stopTimes.size());
            } else {
                // http://localhost:3000/route?point=45.51043713898763%2C-122.68381118774415&point=45.522104713562825%2C-122.6455307006836&weighting=fastest&pt.earliest_departure_time=2018-08-24T16%3A56%3A17Z&arrive_by=false&pt.max_walk_distance_per_leg=1000&pt.limit_solutions=5&locale=en-US&profile=pt&elevation=false&use_miles=false&points_encoded=false&pt.profile=true
                // long query:
                // http://localhost:3000/route?point=45.518526513612244%2C-122.68612861633302&point=45.52908004573869%2C-122.6862144470215&weighting=fastest&pt.earliest_departure_time=2018-08-24T16%3A51%3A20Z&arrive_by=false&pt.max_walk_distance_per_leg=10000&pt.limit_solutions=4&locale=en-US&profile=pt&elevation=false&use_miles=false&points_encoded=false&pt.profile=true
                throw new RuntimeException();
            }
        }
        logger.trace("Number of stop times: {}", stopTimes.size());
        BitSet validOnDay = new BitSet(); // Not valid on any day. Just a template.

        return new GtfsReader.TripWithStopTimes(trip, stopTimes, validOnDay, cancelledArrivals, cancelledDepartures);
    }

    public long getDelayForBoardEdge(PtGraph.PtEdge edge, Instant now) {
        if (isThisRealtimeUpdateAboutThisLineRun(now)) {
            return delaysForBoardEdges.getOrDefault(edge.getId(), 0);
        } else {
            return 0;
        }
    }

    public long getDelayForAlightEdge(PtGraph.PtEdge edge, Instant now) {
        if (isThisRealtimeUpdateAboutThisLineRun(now)) {
            return delaysForAlightEdges.getOrDefault(edge.getId(), 0);
        } else {
            return 0;
        }
    }

    boolean isThisRealtimeUpdateAboutThisLineRun(Instant now) {
        if (Duration.between(feedTimestampOrNow(), now).toHours() > 24) {
            return false;
        } else {
            return true;
        }
    }

    private Instant feedTimestampOrNow() {
        return feedMessages.values().stream().map(feedMessage -> {
            if (feedMessage.getHeader().hasTimestamp()) {
                return Instant.ofEpochSecond(feedMessage.getHeader().getTimestamp());
            } else {
                return Instant.now();
            }
        }).findFirst().orElse(Instant.now());
    }

    public StopTime getStopTime(GTFSFeed staticFeed, GtfsRealtime.TripDescriptor tripDescriptor, Instant boardTime, int stopSequence) {
        StopTime stopTime = staticFeed.stop_times.get(new Fun.Tuple2<>(tripDescriptor.getTripId(), stopSequence));
        if (stopTime == null) {
            return getTripUpdate(staticFeed, tripDescriptor, boardTime).get().stopTimes.get(stopSequence - 1);
        } else {
            return stopTime;
        }
    }

}
