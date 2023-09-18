package com.graphhopper.gtfs.analysis;

import com.carrotsearch.hppc.ObjectIntHashMap;
import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.PatternFinder;
import com.conveyal.gtfs.model.StopTime;
import com.conveyal.gtfs.model.Transfer;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.gtfs.*;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Trips {

    private static final int MAXIMUM_TRANSFER_DURATION = 15 * 60;
    private final Map<String, Transfers> transfers;
    public final Map<String, Map<GtfsRealtime.TripDescriptor, GTFSFeed.StopTimesForTripWithTripPatternKey>> tripsByFeed;
    public final List<GTFSFeed.StopTimesForTripWithTripPatternKey> trips;
    private Map<GtfsStorage.FeedIdWithStopId, Map<String, List<TripAtStopTime>>> boardingsForStopByPattern = new ConcurrentHashMap<>();
    private Map<LocalDate, Map<Trips.TripAtStopTime, Collection<Trips.TripAtStopTime>>> tripTransfersPerDay = new ConcurrentHashMap<>();
    public int idx;

    public Trips(GtfsStorage gtfsStorage) {
        this.gtfsStorage = gtfsStorage;

        transfers = new HashMap<>();
        for (Map.Entry<String, GTFSFeed> entry : this.gtfsStorage.getGtfsFeeds().entrySet()) {
            transfers.put(entry.getKey(), new Transfers(entry.getValue()));
        }
        tripsByFeed = new HashMap<>();
        trips = new ArrayList<>();
        idx = 0;
        for (Map.Entry<String, GTFSFeed> entry : this.gtfsStorage.getGtfsFeeds().entrySet()) {
            GTFSFeed feed = entry.getValue();
            Map<GtfsRealtime.TripDescriptor, GTFSFeed.StopTimesForTripWithTripPatternKey> tripsForFeed = new HashMap<>();
            for (PatternFinder.Pattern pattern : feed.patterns.values()) {
                int endIdxOfPattern = idx + pattern.trips.size();
                for (GtfsRealtime.TripDescriptor tripDescriptor : pattern.trips) {
                    GTFSFeed.StopTimesForTripWithTripPatternKey tripPointer = feed.getStopTimesForTripWithTripPatternKey(entry.getKey(), tripDescriptor);
                    tripPointer.idx = idx++;
                    tripPointer.endIdxOfPattern = endIdxOfPattern;
                    if (tripPointer.idx == Integer.MAX_VALUE)
                        throw new RuntimeException();
                    tripsForFeed.put(tripDescriptor, tripPointer);
                    trips.add(tripPointer);
                }
            }
            tripsByFeed.put(entry.getKey(), tripsForFeed);
        }
    }

    public Map<String, List<TripAtStopTime>> getPatternBoardings(GtfsStorage.FeedIdWithStopId k) {
        return boardingsForStopByPattern.computeIfAbsent(k, key -> {
            Stream<PtEdgeAttributes> sorted = RealtimeFeed.findAllBoardings(gtfsStorage, key)
                    .map(PtGraph.PtEdge::getAttrs)
                    .sorted(Comparator.comparingInt(boarding -> tripsByFeed.get(key.feedId).get(boarding.tripDescriptor).stopTimes.get(boarding.stop_sequence).departure_time));
            Map<String, List<TripAtStopTime>> collect = sorted
                    .collect(Collectors.groupingBy(boarding -> tripsByFeed.get(key.feedId).get(boarding.tripDescriptor).pattern.pattern_id,
                            Collectors.mapping(boarding -> new TripAtStopTime(tripsByFeed.get(key.feedId).get(boarding.tripDescriptor).idx, boarding.stop_sequence),
                                    Collectors.toList())));
            return collect;
        });
    }

    GtfsStorage gtfsStorage;

    public Map<TripAtStopTime, Collection<TripAtStopTime>> findTripTransfers(GTFSFeed.StopTimesForTripWithTripPatternKey tripPointer, String feedKey, LocalDate trafficDay) {
        Transfers transfersForFeed = transfers.get(feedKey);
        Map<TripAtStopTime, Collection<TripAtStopTime>> result = new HashMap<>();
        List<StopTime> stopTimesExceptFirst = tripPointer.stopTimes.subList(1, tripPointer.stopTimes.size());
        ObjectIntHashMap<GtfsStorage.FeedIdWithStopId> arrivalTimes = new ObjectIntHashMap<>();
        for (StopTime stopTime : Lists.reverse(stopTimesExceptFirst)) {
            if (stopTime == null)
                continue;
            GtfsStorage.FeedIdWithStopId stopId = new GtfsStorage.FeedIdWithStopId(feedKey, stopTime.stop_id);
            arrivalTimes.put(stopId, Math.min(stopTime.arrival_time, arrivalTimes.getOrDefault(stopId, Integer.MAX_VALUE)));
            for (GtfsStorage.InterpolatedTransfer it : gtfsStorage.interpolatedTransfers.get(stopId)) {
                GtfsStorage.FeedIdWithStopId boardingStop = new GtfsStorage.FeedIdWithStopId(it.toPlatformDescriptor.feed_id, it.toPlatformDescriptor.stop_id);
                int arrivalTimePlusTransferTime = stopTime.arrival_time + it.streetTime;
                arrivalTimes.put(boardingStop, Math.min(arrivalTimePlusTransferTime, arrivalTimes.getOrDefault(boardingStop, Integer.MAX_VALUE)));
            }
        }
        for (StopTime stopTime : Lists.reverse(stopTimesExceptFirst)) {
            if (stopTime == null)
                continue;
            TripAtStopTime origin = new TripAtStopTime(tripPointer.idx, stopTime.stop_sequence);
            List<TripAtStopTime> destinations = new ArrayList<>();
            GtfsStorage.FeedIdWithStopId stopId = new GtfsStorage.FeedIdWithStopId(feedKey, stopTime.stop_id);
            List<Transfer> transfersFromStop = transfersForFeed.getTransfersFromStop(stopId.stopId, tripPointer.trip.route_id);
            ListMultimap<String, Transfer> multimap = ArrayListMultimap.create();
            for (Transfer transfer : transfersFromStop) {
                multimap.put(transfer.to_stop_id, transfer);
            }
            if (!multimap.containsKey(stopTime.stop_id)) {
                insertTripTransfers(trafficDay, arrivalTimes, stopTime, destinations, new GtfsStorage.FeedIdWithStopId(feedKey, stopTime.stop_id), 0, multimap.get(stopTime.stop_id));
            }
            for (String toStopId : multimap.keySet()) {
                insertTripTransfers(trafficDay, arrivalTimes, stopTime, destinations, new GtfsStorage.FeedIdWithStopId(feedKey, toStopId), 0, multimap.get(toStopId));
            }
            for (GtfsStorage.InterpolatedTransfer it : gtfsStorage.interpolatedTransfers.get(stopId)) {
                insertTripTransfers(trafficDay, arrivalTimes, stopTime, destinations, new GtfsStorage.FeedIdWithStopId(it.toPlatformDescriptor.feed_id, it.toPlatformDescriptor.stop_id), it.streetTime, Collections.emptyList());
            }
            result.put(origin, destinations);
        }
        return result;
    }

    private void insertTripTransfers(LocalDate trafficDay, ObjectIntHashMap<GtfsStorage.FeedIdWithStopId> arrivalTimes, StopTime arrivalStopTime, List<TripAtStopTime> destinations, GtfsStorage.FeedIdWithStopId boardingStop, int streetTime, List<Transfer> transfers) {
        int earliestDepartureTime = arrivalStopTime.arrival_time + streetTime;
        Collection<List<TripAtStopTime>> boardingsForPattern = getPatternBoardings(boardingStop).values();
        for (List<TripAtStopTime> boardings : boardingsForPattern) {
            for (TripAtStopTime candidate : boardings) {
                GTFSFeed.StopTimesForTripWithTripPatternKey trip = getTrip(candidate.tripIdx);
                int earliestDepatureTimeForThisDestination = earliestDepartureTime;
                for (Transfer transfer : transfers) {
                    if (trip.trip.route_id.equals(transfer.to_route_id)) {
                        earliestDepatureTimeForThisDestination += transfer.min_transfer_time;
                    }
                }
                StopTime departureStopTime = trip.stopTimes.get(candidate.stop_sequence);
                if (departureStopTime.departure_time >= arrivalStopTime.arrival_time + MAXIMUM_TRANSFER_DURATION) {
                    break; // next pattern
                }
                if (trip.service.activeOn(trafficDay)) {
                    if (departureStopTime.departure_time >= earliestDepatureTimeForThisDestination) {
                        boolean keep = false;
                        boolean overnight = false;
                        for (int i = candidate.stop_sequence; i < trip.stopTimes.size(); i++) {
                            StopTime destinationStopTime = trip.stopTimes.get(i);
                            if (destinationStopTime == null)
                                continue;
                            int destinationArrivalTime = destinationStopTime.arrival_time;
                            if (i == candidate.stop_sequence) {
                                if (destinationArrivalTime < earliestDepartureTime) {
                                    overnight = true;
                                }
                            } else {
                                if (overnight) {
                                    destinationArrivalTime += 24 * 60 * 60;
                                }
                                GtfsStorage.FeedIdWithStopId destinationStopId = new GtfsStorage.FeedIdWithStopId(trip.trip.feed_id, destinationStopTime.stop_id);
                                int oldArrivalTime = arrivalTimes.getOrDefault(destinationStopId, Integer.MAX_VALUE);
                                keep = keep || destinationArrivalTime < oldArrivalTime;
                                arrivalTimes.put(destinationStopId, Math.min(oldArrivalTime, destinationArrivalTime));
                            }
                        }
                        if (keep) {
                            destinations.add(candidate);
                        }
                        break; // next pattern
                    }
                }
            }
        }
    }

    public void findAllTripTransfersInto(Map<TripAtStopTime, Collection<TripAtStopTime>> result, LocalDate trafficDay) {
        Map<TripAtStopTime, Collection<TripAtStopTime>> r = Collections.synchronizedMap(result);
        for (Map.Entry<String, Map<GtfsRealtime.TripDescriptor, GTFSFeed.StopTimesForTripWithTripPatternKey>> e : tripsByFeed.entrySet()) {
            String feedKey = e.getKey();
            Map<GtfsRealtime.TripDescriptor, GTFSFeed.StopTimesForTripWithTripPatternKey> tripsForFeed = e.getValue();
            tripsForFeed.values().stream()
                    .filter(trip -> trip.service.activeOn(trafficDay))
                    .parallel()
                    .forEach(tripPointer -> {
                        Map<TripAtStopTime, Collection<TripAtStopTime>> reducedTripTransfers = findTripTransfers(tripPointer, feedKey, trafficDay);
                        r.putAll(reducedTripTransfers);
                    });
        }
    }

    public Map<LocalDate, Map<TripAtStopTime, Collection<TripAtStopTime>>> getTripTransfers() {
        return tripTransfersPerDay;
    }

    public Map<Trips.TripAtStopTime, Collection<Trips.TripAtStopTime>> getTripTransfers(LocalDate trafficDay) {
        return tripTransfersPerDay.computeIfAbsent(trafficDay, k -> new TreeMap<>());
    }

    public GTFSFeed.StopTimesForTripWithTripPatternKey getTrip(int tripIdx) {
        return trips.get(tripIdx);
    }


    public static class TripAtStopTime implements Serializable, Comparable<TripAtStopTime> {

        public static final Comparator<TripAtStopTime> TRIP_AT_STOP_TIME_COMPARATOR = Comparator.<TripAtStopTime>comparingInt(tst1 -> tst1.tripIdx).thenComparingInt(tst -> tst.stop_sequence);
        public int tripIdx;
        public int stop_sequence;

        public TripAtStopTime(int tripIdx, int stop_sequence) {
            this.tripIdx = tripIdx;
            this.stop_sequence = stop_sequence;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TripAtStopTime that = (TripAtStopTime) o;
            return tripIdx == that.tripIdx && stop_sequence == that.stop_sequence;
        }

        @Override
        public String toString() {
            return "TripAtStopTime{" +
                    "tripIdx=" + tripIdx +
                    ", stop_sequence=" + stop_sequence +
                    '}';
        }

        @Override
        public int compareTo(TripAtStopTime o) {
            return TRIP_AT_STOP_TIME_COMPARATOR.compare(this, o);
        }
    }

}
