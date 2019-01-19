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

import com.carrotsearch.hppc.IntHashSet;
import com.carrotsearch.hppc.IntIntHashMap;
import com.carrotsearch.hppc.IntLongHashMap;
import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Fare;
import com.conveyal.gtfs.model.Frequency;
import com.conveyal.gtfs.model.StopTime;
import com.conveyal.gtfs.model.Trip;
import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.routing.VirtualEdgeIteratorState;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphExtension;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.BBox;
import org.mapdb.Fun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import static com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.NO_DATA;
import static com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.SKIPPED;
import static java.time.temporal.ChronoUnit.DAYS;

public class RealtimeFeed {
    private static final Logger logger = LoggerFactory.getLogger(RealtimeFeed.class);
    private final IntHashSet blockedEdges;
    private final IntLongHashMap delaysForBoardEdges;
    private final IntLongHashMap delaysForAlightEdges;
    private final List<VirtualEdgeIteratorState> additionalEdges;
    public final Map<String, GtfsRealtime.FeedMessage> feedMessages;
    private final GtfsStorage staticGtfs;
    private final Map<Integer, byte[]> additionalTripDescriptors;
    private final Map<Integer, Integer> stopSequences;
    private final Map<Integer, GtfsStorage.Validity> validities;
    private final Map<Integer, GtfsStorage.FeedIdWithTimezone> feedIdWithTimezones;

    private RealtimeFeed(GtfsStorage staticGtfs, Map<String, GtfsRealtime.FeedMessage> feedMessages, IntHashSet blockedEdges,
                         IntLongHashMap delaysForBoardEdges, IntLongHashMap delaysForAlightEdges, List<VirtualEdgeIteratorState> additionalEdges, Map<Integer, byte[]> tripDescriptors, Map<Integer, Integer> stopSequences, Map<GtfsStorage.Validity, Integer> operatingDayPatterns, Map<GtfsStorage.FeedIdWithTimezone, Integer> writableTimeZones) {
        this.staticGtfs = staticGtfs;
        this.feedMessages = feedMessages;
        this.blockedEdges = blockedEdges;
        this.delaysForBoardEdges = delaysForBoardEdges;
        this.delaysForAlightEdges = delaysForAlightEdges;
        this.additionalEdges = additionalEdges;
        this.additionalTripDescriptors = tripDescriptors;
        this.stopSequences = stopSequences;
        Map<Integer, GtfsStorage.Validity> reverseOperatingDayPatterns = new HashMap<>();
        for (Map.Entry<GtfsStorage.Validity, Integer> entry : operatingDayPatterns.entrySet()) {
            reverseOperatingDayPatterns.put(entry.getValue(), entry.getKey());
        }
        this.validities = Collections.unmodifiableMap(reverseOperatingDayPatterns);
        Map<Integer, GtfsStorage.FeedIdWithTimezone> feedIdWithTimezoneMap = new HashMap<>();
        for (Map.Entry<GtfsStorage.FeedIdWithTimezone, Integer> entry : writableTimeZones.entrySet()) {
            feedIdWithTimezoneMap.put(entry.getValue(), entry.getKey());
        }
        this.feedIdWithTimezones = Collections.unmodifiableMap(feedIdWithTimezoneMap);
    }

    public static RealtimeFeed empty(GtfsStorage staticGtfs) {
        return new RealtimeFeed(staticGtfs, Collections.emptyMap(), new IntHashSet(), new IntLongHashMap(), new IntLongHashMap(), Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), staticGtfs.getOperatingDayPatterns(), staticGtfs.getWritableTimeZones());
    }

    public static RealtimeFeed fromProtobuf(GraphHopperStorage graphHopperStorage, GtfsStorage staticGtfs, PtFlagEncoder encoder, Map<String, GtfsRealtime.FeedMessage> feedMessages) {
        final IntHashSet blockedEdges = new IntHashSet();
        final IntLongHashMap delaysForBoardEdges = new IntLongHashMap();
        final IntLongHashMap delaysForAlightEdges = new IntLongHashMap();
        final LinkedList<VirtualEdgeIteratorState> additionalEdges = new LinkedList<>();
        final Graph overlayGraph = new Graph() {
            int firstEdge = graphHopperStorage.getAllEdges().length();
            final NodeAccess nodeAccess = new NodeAccess() {
                IntIntHashMap additionalNodeFields = new IntIntHashMap();

                @Override
                public int getAdditionalNodeField(int nodeId) {
                    return 0;
                }

                @Override
                public void setAdditionalNodeField(int nodeId, int additionalValue) {
                    additionalNodeFields.put(nodeId, additionalValue);
                }

                @Override
                public boolean is3D() {
                    return false;
                }

                @Override
                public int getDimension() {
                    return 0;
                }

                @Override
                public void ensureNode(int nodeId) {

                }

                @Override
                public void setNode(int nodeId, double lat, double lon) {

                }

                @Override
                public void setNode(int nodeId, double lat, double lon, double ele) {

                }

                @Override
                public double getLatitude(int nodeId) {
                    return 0;
                }

                @Override
                public double getLat(int nodeId) {
                    return 0;
                }

                @Override
                public double getLongitude(int nodeId) {
                    return 0;
                }

                @Override
                public double getLon(int nodeId) {
                    return 0;
                }

                @Override
                public double getElevation(int nodeId) {
                    return 0;
                }

                @Override
                public double getEle(int nodeId) {
                    return 0;
                }
            };
            @Override
            public Graph getBaseGraph() {
                return graphHopperStorage;
            }

            @Override
            public int getNodes() {
                return IntStream.concat(
                        IntStream.of(graphHopperStorage.getNodes()-1),
                        additionalEdges.stream().flatMapToInt(edge -> IntStream.of(edge.getBaseNode(), edge.getAdjNode())))
                        .max().getAsInt()+1;
            }

            @Override
            public int getEdges() {
                return getAllEdges().length();
            }

            @Override
            public NodeAccess getNodeAccess() {
                return nodeAccess;
            }

            @Override
            public BBox getBounds() {
                return null;
            }

            @Override
            public EdgeIteratorState edge(int a, int b) {
                int edge = firstEdge++;
                final VirtualEdgeIteratorState newEdge = new VirtualEdgeIteratorState(-1,
                        edge, a, b, 0.0, 0, "", new PointList());
                final VirtualEdgeIteratorState reverseNewEdge = new VirtualEdgeIteratorState(-1,
                        edge, b, a, 0.0, 0, "", new PointList());
                newEdge.setReverseEdge(reverseNewEdge);
                reverseNewEdge.setReverseEdge(newEdge);
                additionalEdges.push(newEdge);
                return newEdge;
            }

            @Override
            public EdgeIteratorState edge(int a, int b, double distance, boolean bothDirections) {
                return null;
            }

            @Override
            public EdgeIteratorState getEdgeIteratorState(int edgeId, int adjNode) {
                return null;
            }

            @Override
            public AllEdgesIterator getAllEdges() {
                return null;
            }

            @Override
            public EdgeExplorer createEdgeExplorer(EdgeFilter filter) {
                return null;
            }

            @Override
            public EdgeExplorer createEdgeExplorer() {
                return graphHopperStorage.createEdgeExplorer();
            }

            @Override
            public Graph copyTo(Graph g) {
                return null;
            }

            @Override
            public GraphExtension getExtension() {
                throw new RuntimeException();
            }
        };

        Map<GtfsStorage.Validity, Integer> operatingDayPatterns = new HashMap<>(staticGtfs.getOperatingDayPatterns());
        Map<Integer, byte[]> tripDescriptors = new HashMap<>();
        Map<Integer, Integer> stopSequences = new HashMap<>();
        Map<String, int[]> boardEdgesForTrip = new HashMap<>();
        Map<String, int[]> alightEdgesForTrip = new HashMap<>();
        Map<GtfsStorage.FeedIdWithTimezone, Integer> writableTimeZones = new HashMap<>(staticGtfs.getWritableTimeZones());


        feedMessages.forEach((feedKey, feedMessage) -> {
            GTFSFeed feed = staticGtfs.getGtfsFeeds().get(feedKey);
            ZoneId timezone = ZoneId.of(feed.agency.values().stream().findFirst().get().agency_timezone);
            GtfsStorageI gtfsStorage = new GtfsStorageI() {
                @Override
                public Map<String, Fare> getFares() {
                    return null;
                }

                @Override
                public Map<GtfsStorage.Validity, Integer> getOperatingDayPatterns() {
                    return operatingDayPatterns;
                }

                @Override
                public Map<GtfsStorage.FeedIdWithTimezone, Integer> getWritableTimeZones() {
                    return writableTimeZones;
                }

                @Override
                public Map<Integer, byte[]> getTripDescriptors() {
                    return tripDescriptors;
                }

                @Override
                public Map<Integer, Integer> getStopSequences() {
                    return stopSequences;
                }

                @Override
                public Map<String, int[]> getBoardEdgesForTrip() {
                    return boardEdgesForTrip;
                }

                @Override
                public Map<String, int[]> getAlightEdgesForTrip() {
                    return alightEdgesForTrip;
                }

                @Override
                public Map<String, GTFSFeed> getGtfsFeeds() {
                    HashMap<String, GTFSFeed> stringGTFSFeedHashMap = new HashMap<>();
                    stringGTFSFeedHashMap.put(feedKey, feed);
                    return stringGTFSFeedHashMap;
                }

                @Override
                public Map<String, Transfers> getTransfers() {
                    return staticGtfs.getTransfers();
                }

                @Override
                public Map<String, Integer> getStationNodes() {
                    return staticGtfs.getStationNodes();
                }

                @Override
                public Map<Integer, PlatformDescriptor> getRoutes() {
                    return staticGtfs.getRoutes();
                }
            };
            final GtfsReader gtfsReader = new GtfsReader(feedKey, overlayGraph, gtfsStorage, encoder, null);
            Instant timestamp = Instant.ofEpochSecond(feedMessage.getHeader().getTimestamp());
            LocalDate dateToChange = timestamp.atZone(timezone).toLocalDate(); //FIXME
            BitSet validOnDay = new BitSet();
            LocalDate startDate = feed.getStartDate();
            validOnDay.set((int) DAYS.between(startDate, dateToChange));
            feedMessage.getEntityList().stream()
                    .filter(GtfsRealtime.FeedEntity::hasTripUpdate)
                    .map(GtfsRealtime.FeedEntity::getTripUpdate)
                    .filter(tripUpdate -> tripUpdate.getTrip().getScheduleRelationship() == GtfsRealtime.TripDescriptor.ScheduleRelationship.SCHEDULED)
                    .forEach(tripUpdate -> {
                        Collection<Frequency> frequencies = feed.getFrequencies(tripUpdate.getTrip().getTripId());
                        int timeOffset = (tripUpdate.getTrip().hasStartTime() && !frequencies.isEmpty()) ? LocalTime.parse(tripUpdate.getTrip().getStartTime()).toSecondOfDay() : 0;
                        String key = GtfsStorage.tripKey(tripUpdate.getTrip(), !frequencies.isEmpty());
                        final int[] boardEdges = staticGtfs.getBoardEdgesForTrip().get(key);
                        final int[] leaveEdges = staticGtfs.getAlightEdgesForTrip().get(key);
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
                            if (stopTime.stop_sequence > leaveEdges.length-1) {
                                logger.warn("Stop sequence number too high {} vs {}",stopTime.stop_sequence, leaveEdges.length);
                                return;
                            }
                            final StopTime originalStopTime = feed.stop_times.get(new Fun.Tuple2(tripUpdate.getTrip().getTripId(), stopTime.stop_sequence));
                            int arrivalDelay = stopTime.arrival_time - originalStopTime.arrival_time;
                            delaysForAlightEdges.put(leaveEdges[stopTime.stop_sequence], arrivalDelay * 1000);
                            int departureDelay = stopTime.departure_time - originalStopTime.departure_time;
                            if (departureDelay > 0) {
                                int boardEdge = boardEdges[stopTime.stop_sequence];
                                int departureNode = graphHopperStorage.getEdgeIteratorState(boardEdge, Integer.MIN_VALUE).getAdjNode();
                                int delayedBoardEdge = gtfsReader.addDelayedBoardEdge(timezone, tripUpdate.getTrip(), stopTime.stop_sequence, stopTime.departure_time + timeOffset, departureNode, validOnDay);
                                delaysForBoardEdges.put(delayedBoardEdge, departureDelay * 1000);
                            }
                        });
                    });
            feedMessage.getEntityList().stream()
                    .filter(GtfsRealtime.FeedEntity::hasTripUpdate)
                    .map(GtfsRealtime.FeedEntity::getTripUpdate)
                    .filter(tripUpdate -> tripUpdate.getTrip().getScheduleRelationship() == GtfsRealtime.TripDescriptor.ScheduleRelationship.ADDED)
                    .forEach(tripUpdate -> {
                        Trip trip = new Trip();
                        trip.trip_id = tripUpdate.getTrip().getTripId();
                        trip.route_id = tripUpdate.getTrip().getRouteId();
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
                        GtfsReader.TripWithStopTimes tripWithStopTimes = new GtfsReader.TripWithStopTimes(trip, stopTimes, validOnDay, Collections.emptySet(), Collections.emptySet());
                        gtfsReader.addTrip(timezone, 0, new ArrayList<>(), tripWithStopTimes, tripUpdate.getTrip(), false);
                    });
            gtfsReader.wireUpAdditionalDeparturesAndArrivals(timezone);
        });

        return new RealtimeFeed(staticGtfs, feedMessages, blockedEdges, delaysForBoardEdges, delaysForAlightEdges, additionalEdges, tripDescriptors, stopSequences, operatingDayPatterns, writableTimeZones);
    }

    boolean isBlocked(int edgeId) {
        return blockedEdges.contains(edgeId);
    }

    List<VirtualEdgeIteratorState> getAdditionalEdges() {
        return additionalEdges;
    }

    public Optional<GtfsReader.TripWithStopTimes> getTripUpdate(GTFSFeed staticFeed, GtfsRealtime.TripDescriptor tripDescriptor, Label.Transition boardEdge, Instant boardTime) {
        logger.trace("getTripUpdate {}", tripDescriptor);
        if (!isThisRealtimeUpdateAboutThisLineRun(boardEdge.edge.edgeIteratorState, boardTime)) {
            return Optional.empty();
        } else {
            GtfsRealtime.TripDescriptor normalizedTripDescriptor = normalize(tripDescriptor);
            return feedMessages.values().stream().flatMap(feedMessage -> feedMessage.getEntityList().stream()
                    .filter(e -> e.hasTripUpdate())
                    .map(e -> e.getTripUpdate())
                    .filter(tu -> normalize(tu.getTrip()).equals(normalizedTripDescriptor))
                    .map(tu -> toTripWithStopTimes(staticFeed, tu)))
                    .findFirst();
        }
    }

    public GtfsRealtime.TripDescriptor normalize(GtfsRealtime.TripDescriptor tripDescriptor) {
        return GtfsRealtime.TripDescriptor.newBuilder(tripDescriptor).clearRouteId().build();
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
            int nextStopSequence = stopTimes.isEmpty() ? 1 : stopTimes.get(stopTimes.size()-1).stop_sequence+1;
            for (int i=nextStopSequence; i<stopTimeUpdate.getStopSequence(); i++) {
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
                throw new RuntimeException();
            }
        }
        logger.trace("Number of stop times: {}", stopTimes.size());
        BitSet validOnDay = new BitSet(); // Not valid on any day. Just a template.

        return new GtfsReader.TripWithStopTimes(trip, stopTimes, validOnDay, cancelledArrivals, cancelledDepartures);
    }

    public long getDelayForBoardEdge(EdgeIteratorState edge, Instant now) {
        if (isThisRealtimeUpdateAboutThisLineRun(edge, now)) {
            return delaysForBoardEdges.getOrDefault(edge.getEdge(), 0);
        } else {
            return 0;
        }
    }

    public long getDelayForAlightEdge(EdgeIteratorState edge, Instant now) {
        if (isThisRealtimeUpdateAboutThisLineRun(edge, now)) {
            return delaysForAlightEdges.getOrDefault(edge.getEdge(), 0);
        } else {
            return 0;
        }
    }

    boolean isThisRealtimeUpdateAboutThisLineRun(EdgeIteratorState edge, Instant now) {
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

    public byte[] getTripDescriptor(int edge) {
        return staticGtfs.getTripDescriptors().getOrDefault(edge, additionalTripDescriptors.get(edge));
    }

    public int getStopSequence(int edge) {
        return staticGtfs.getStopSequences().getOrDefault(edge, stopSequences.get(edge));
    }

    public StopTime getStopTime(GTFSFeed staticFeed, GtfsRealtime.TripDescriptor tripDescriptor, Label.Transition t, Instant boardTime, int stopSequence) {
        StopTime stopTime = staticFeed.stop_times.get(new Fun.Tuple2<>(tripDescriptor.getTripId(), stopSequence));
        if (stopTime == null) {
            return getTripUpdate(staticFeed, tripDescriptor, t, boardTime).get().stopTimes.get(stopSequence-1);
        } else {
            return stopTime;
        }
    }

    public GtfsStorage.Validity getValidity(int validityId) {
        return validities.get(validityId);
    }

}
