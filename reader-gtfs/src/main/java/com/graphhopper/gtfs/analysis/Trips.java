package com.graphhopper.gtfs.analysis;

import com.carrotsearch.hppc.ObjectIntHashMap;
import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Frequency;
import com.conveyal.gtfs.model.StopTime;
import com.conveyal.gtfs.model.Transfer;
import com.conveyal.gtfs.model.Trip;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.gtfs.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.conveyal.gtfs.model.Entity.Writer.convertToGtfsTime;

public class Trips {

    private final Map<String, Transfers> transfers;

    public Trips(GtfsStorage gtfsStorage) {
        this.gtfsStorage = gtfsStorage;
        transfers = new HashMap<>();
        for (Map.Entry<String, GTFSFeed> entry : this.gtfsStorage.getGtfsFeeds().entrySet()) {
            transfers.put(entry.getKey(), new Transfers(entry.getValue()));
        }
    }

    GtfsStorage gtfsStorage;

    public Map<TripAtStopTime, Collection<TripAtStopTime>> findTripTransfers(GTFSFeed feed, GtfsRealtime.TripDescriptor tripDescriptor, String feedKey, LocalDate trafficDay) {
        Transfers transfersForFeed = transfers.get(feedKey);
        Map<TripAtStopTime, Collection<TripAtStopTime>> result = new HashMap<>();
        GTFSFeed.StopTimesForTripWithTripPatternKey orderedStopTimesForTrip = feed.stopTimes.getUnchecked(tripDescriptor);
        List<StopTime> stopTimesExceptFirst = orderedStopTimesForTrip.stopTimes.subList(1, orderedStopTimesForTrip.stopTimes.size());
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
            TripAtStopTime origin = new TripAtStopTime(feedKey, tripDescriptor, stopTime.stop_sequence);
            List<TripAtStopTime> destinations = new ArrayList<>();
            GtfsStorage.FeedIdWithStopId stopId = new GtfsStorage.FeedIdWithStopId(feedKey, stopTime.stop_id);
            List<Transfer> transfersFromStop = transfersForFeed.getTransfersFromStop(stopId.stopId, orderedStopTimesForTrip.trip.route_id);
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
//            System.out.printf("%s %s %d %s\n", origin.tripDescriptor.getTripId(), origin.tripDescriptor.hasStartTime() ? origin.tripDescriptor.getStartTime() : "", origin.stop_sequence, stopTime.stop_id);
//            System.out.printf("  %d filtered transfers\n", destinations.size());
//            for (TripAtStopTime destination : destinations) {
//                System.out.printf("    %s %s %d\n", destination.tripDescriptor.getTripId(), destination.tripDescriptor.hasStartTime() ? destination.tripDescriptor.getStartTime() : "", destination.stop_sequence);
//            }
        }
        return result;
    }

    private void insertTripTransfers(LocalDate trafficDay, ObjectIntHashMap<GtfsStorage.FeedIdWithStopId> arrivalTimes, StopTime arrivalStopTime, List<TripAtStopTime> destinations, GtfsStorage.FeedIdWithStopId boardingStop, int streetTime, List<Transfer> transfers) {
        int earliestDepartureTime = arrivalStopTime.arrival_time + streetTime;
        Collection<List<TripAtStopTime>> boardingsForPattern = boardingsForStopByPattern.getUnchecked(boardingStop).values();
        for (List<TripAtStopTime> boardings : boardingsForPattern) {
            for (TripAtStopTime candidate : boardings) {
                GTFSFeed destinationFeed = gtfsStorage.getGtfsFeeds().get(candidate.feedId);
                GTFSFeed.StopTimesForTripWithTripPatternKey trip = destinationFeed.stopTimes.getUnchecked(candidate.tripDescriptor);
                int earliestDepatureTimeForThisDestination = earliestDepartureTime;
                for (Transfer transfer : transfers) {
                    if (trip.trip.route_id.equals(transfer.to_route_id)) {
                        earliestDepatureTimeForThisDestination += transfer.min_transfer_time;
                    }
                }
                if (trip.service.activeOn(trafficDay)) {
                    StopTime departureStopTime = trip.stopTimes.get(candidate.stop_sequence);
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
                                GtfsStorage.FeedIdWithStopId destinationStopId = new GtfsStorage.FeedIdWithStopId(candidate.feedId, destinationStopTime.stop_id);
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

    public LoadingCache<GtfsStorage.FeedIdWithStopId, Map<String, List<TripAtStopTime>>> boardingsForStopByPattern = CacheBuilder.newBuilder().maximumSize(200000).build(new CacheLoader<GtfsStorage.FeedIdWithStopId, Map<String, List<TripAtStopTime>>>() {
        @Override
        public Map<String, List<TripAtStopTime>> load(GtfsStorage.FeedIdWithStopId key) {
            GTFSFeed feed = gtfsStorage.getGtfsFeeds().get(key.feedId);
            return RealtimeFeed.findAllBoardings(gtfsStorage, key)
                    .map(PtGraph.PtEdge::getAttrs)
                    .map(boarding -> new TripAtStopTime(key.feedId, boarding.tripDescriptor, boarding.stop_sequence))
                    .sorted(Comparator.comparingInt(boarding -> {
                        List<StopTime> stopTimes = feed.stopTimes.getUnchecked(boarding.tripDescriptor).stopTimes;
                        return stopTimes.get(boarding.stop_sequence).departure_time;
                    }))
                    .collect(Collectors.groupingBy(boarding -> feed.stopTimes.getUnchecked(boarding.tripDescriptor).pattern.pattern_id));
        }
    });

    public static void findAllTripTransfersInto(Map<TripAtStopTime, Collection<TripAtStopTime>> result, GtfsStorage gtfsStorage, LocalDate trafficDay) {
        Trips trips = new Trips(gtfsStorage);
        for (Map.Entry<String, GTFSFeed> e : gtfsStorage.getGtfsFeeds().entrySet()) {
            String feedKey = e.getKey();
            GTFSFeed feed = e.getValue();
            int total = feed.trips.size();
            AtomicInteger i = new AtomicInteger();
            feed.trips.values().parallelStream().forEach(trip -> {
                try {
                    Collection<Frequency> frequencies = feed.getFrequencies(trip.trip_id);
                    List<GtfsRealtime.TripDescriptor> actualTrips = new ArrayList<>();
                    GtfsRealtime.TripDescriptor.Builder builder = GtfsRealtime.TripDescriptor.newBuilder().setTripId(trip.trip_id).setRouteId(trip.route_id);
                    if (frequencies.isEmpty()) {
                        actualTrips.add(builder.build());
                    } else {
                        for (Frequency frequency : frequencies) {
                            for (int time = frequency.start_time; time < frequency.end_time; time += frequency.headway_secs) {
                                actualTrips.add(builder.setStartTime(convertToGtfsTime(time)).build());
                            }
                        }
                    }
                    for (GtfsRealtime.TripDescriptor tripDescriptor : actualTrips) {
                        Map<TripAtStopTime, Collection<TripAtStopTime>> reducedTripTransfers = trips.findTripTransfers(feed, tripDescriptor, feedKey, trafficDay);
                        System.out.println(reducedTripTransfers.size());
                        result.putAll(reducedTripTransfers);
                    }
                    System.out.printf("%d / %d trips processed\n", i.incrementAndGet(), total);
                } catch (Exception ex) {
                    throw new RuntimeException(trip.trip_id, ex);
                }
            });
        }
    }

    public static class TripAtStopTime implements Serializable, Comparable<TripAtStopTime> {

        public String feedId;
        public GtfsRealtime.TripDescriptor tripDescriptor;
        public int stop_sequence;

        public TripAtStopTime(String feedId, GtfsRealtime.TripDescriptor tripDescriptor, int stop_sequence) {
            this.feedId = feedId;
            this.tripDescriptor = tripDescriptor;
            this.stop_sequence = stop_sequence;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TripAtStopTime that = (TripAtStopTime) o;
            return stop_sequence == that.stop_sequence && Objects.equals(feedId, that.feedId) && Objects.equals(tripDescriptor, that.tripDescriptor);
        }

        @Override
        public int hashCode() {
            return Objects.hash(feedId, tripDescriptor, stop_sequence);
        }

        private void readObject(ObjectInputStream aInputStream) throws ClassNotFoundException, IOException {
            feedId = aInputStream.readUTF();
            int size = aInputStream.readInt();
            byte[] bytes = new byte[size];
            aInputStream.read(bytes);
            tripDescriptor = GtfsRealtime.TripDescriptor.parseFrom(bytes);
            stop_sequence = aInputStream.readInt();
        }

        /**
         * This is the default implementation of writeObject. Customize as necessary.
         */
        private void writeObject(ObjectOutputStream aOutputStream) throws IOException {
            aOutputStream.writeUTF(feedId);
            byte[] bytes = tripDescriptor.toByteArray();
            aOutputStream.writeInt(bytes.length);
            aOutputStream.write(bytes);
            aOutputStream.writeInt(stop_sequence);
        }

        @Override
        public String toString() {
            return "TripAtStopTime{" +
                    "feedId='" + feedId + '\'' +
                    ", tripDescriptor=" + tripDescriptor +
                    ", stop_sequence=" + stop_sequence +
                    '}';
        }

        @Override
        public int compareTo(TripAtStopTime o) {
            return Comparator.<TripAtStopTime, String>comparing(t1 -> t1.feedId)
                    .thenComparing(t1 -> t1.tripDescriptor.getTripId())
                    .thenComparing(t1 -> t1.tripDescriptor.hasStartTime() ? t1.tripDescriptor.getStartTime() : "")
                    .thenComparingInt(t -> t.stop_sequence).compare(this, o);
        }
    }

}
