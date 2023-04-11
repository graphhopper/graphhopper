package com.graphhopper.gtfs.analysis;

import com.carrotsearch.hppc.ObjectIntHashMap;
import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Frequency;
import com.conveyal.gtfs.model.Service;
import com.conveyal.gtfs.model.StopTime;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.gtfs.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.conveyal.gtfs.model.Entity.Writer.convertToGtfsTime;

public class Trips {

    public Trips(GtfsStorage gtfsStorage) {
        this.gtfsStorage = gtfsStorage;
    }

    GtfsStorage gtfsStorage;


    public Map<TripAtStopTime, Collection<TripAtStopTime>> findTripTransfers(GTFSFeed feed, GtfsRealtime.TripDescriptor tripDescriptor, String feedKey) {
        Map<TripAtStopTime, Collection<TripAtStopTime>> result = new HashMap<>();
        Iterable<StopTime> orderedStopTimesForTrip = feed.getOrderedStopTimesForTrip(tripDescriptor.getTripId());
        List<StopTime> stopTimesExceptFirst = StreamSupport.stream(orderedStopTimesForTrip.spliterator(), false).skip(1).collect(Collectors.toList());
        Collections.reverse(stopTimesExceptFirst);
        ObjectIntHashMap<GtfsStorage.FeedIdWithStopId> arrivalTimes = new ObjectIntHashMap<>();
        ZoneId timeZone = ZoneId.of("America/Los_Angeles");
        for (StopTime stopTime : stopTimesExceptFirst) {
            GtfsStorage.FeedIdWithStopId stopId = new GtfsStorage.FeedIdWithStopId(feedKey, stopTime.stop_id);
            Set<GtfsStorage.InterpolatedTransfer> interpolatedTransfers = gtfsStorage.interpolatedTransfers.get(stopId);
            int arrivalTime = stopTime.arrival_time + (tripDescriptor.hasStartTime() ? LocalTime.parse(tripDescriptor.getStartTime()).toSecondOfDay() : 0);
            arrivalTimes.put(stopId, Math.min(arrivalTime, arrivalTimes.getOrDefault(stopId, Integer.MAX_VALUE)));
            for (GtfsStorage.InterpolatedTransfer it : interpolatedTransfers) {
                GtfsStorage.FeedIdWithStopId boardingStop = new GtfsStorage.FeedIdWithStopId(it.toPlatformDescriptor.feed_id, it.toPlatformDescriptor.stop_id);
                int arrivalTimePlusTransferTime = arrivalTime + it.streetTime;
                arrivalTimes.put(boardingStop, Math.min(arrivalTimePlusTransferTime, arrivalTimes.getOrDefault(boardingStop, Integer.MAX_VALUE)));
            }
        }
        for (StopTime stopTime : stopTimesExceptFirst) {
            TripAtStopTime origin = new TripAtStopTime(feedKey, tripDescriptor, stopTime.stop_sequence);
            List<TripAtStopTime> destinations = new ArrayList<>();
            GtfsStorage.FeedIdWithStopId stopId = new GtfsStorage.FeedIdWithStopId(feedKey, stopTime.stop_id);
            Set<GtfsStorage.InterpolatedTransfer> interpolatedTransfers = gtfsStorage.interpolatedTransfers.get(stopId);
            extracted(feed, tripDescriptor, arrivalTimes, timeZone, stopTime, destinations, stopId, 0);
            for (GtfsStorage.InterpolatedTransfer it : interpolatedTransfers) {
                extracted(feed, tripDescriptor, arrivalTimes, timeZone, stopTime, destinations, new GtfsStorage.FeedIdWithStopId(it.toPlatformDescriptor.feed_id, it.toPlatformDescriptor.stop_id), it.streetTime);
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

    private void extracted(GTFSFeed feed, GtfsRealtime.TripDescriptor tripDescriptor, ObjectIntHashMap<GtfsStorage.FeedIdWithStopId> arrivalTimes, ZoneId timeZone, StopTime stopTime, List<TripAtStopTime> destinations, GtfsStorage.FeedIdWithStopId boardingStop, int streetTime) {
        int arrivalTime = stopTime.arrival_time + (tripDescriptor.hasStartTime() ? LocalTime.parse(tripDescriptor.getStartTime()).toSecondOfDay() : 0) + streetTime;
        boardingsForStopByPattern.getUnchecked(boardingStop)
                .forEach((pattern, boardings) -> boardings.stream()
                        .filter(TripBasedRouter.reachable(feed, trafficDay.atStartOfDay(timeZone).plusSeconds(arrivalTime)))
                        .findFirst()
                        .ifPresent(destination -> {
                            boolean overnight = false;
                            boolean keep = false;
                            GTFSFeed destinationFeed = gtfsStorage.getGtfsFeeds().get(destination.feedId);
                            GTFSFeed.StopTimesForTripWithTripPatternKey stopTimesForTripWithTripPatternKey = destinationFeed.stopTimes.getUnchecked(destination.tripDescriptor);
                            for (int i = destination.stop_sequence; i < stopTimesForTripWithTripPatternKey.stopTimes.size(); i++) {
                                StopTime destinationStopTime = stopTimesForTripWithTripPatternKey.stopTimes.get(i);
                                int destinationArrivalTime = destinationStopTime.arrival_time + (destination.tripDescriptor.hasStartTime() ? LocalTime.parse(destination.tripDescriptor.getStartTime()).toSecondOfDay() : 0);
                                if (destinationStopTime.stop_sequence == destination.stop_sequence) {
                                    if (destinationArrivalTime < arrivalTime) {
                                        overnight = true;
                                    }
                                } else {
                                    if (overnight) {
                                        destinationArrivalTime += 24 * 60 * 60;
                                    }
                                    GtfsStorage.FeedIdWithStopId destinationStopId = new GtfsStorage.FeedIdWithStopId(destination.feedId, destinationStopTime.stop_id);
                                    int oldArrivalTime = arrivalTimes.getOrDefault(destinationStopId, Integer.MAX_VALUE);
                                    keep = keep || destinationArrivalTime < oldArrivalTime;
                                    arrivalTimes.put(destinationStopId, Math.min(oldArrivalTime, destinationArrivalTime));
                                }
                            }
                            if (keep) {
                                destinations.add(destination);
                            }
                        }));
    }


    public void setTrafficDay(LocalDate trafficDay) {
        this.trafficDay = trafficDay;
    }

    private LocalDate trafficDay;
    public LoadingCache<GtfsStorage.FeedIdWithStopId, Map<String, List<TripAtStopTime>>> boardingsForStopByPattern = CacheBuilder.newBuilder().maximumSize(200000).build(new CacheLoader<GtfsStorage.FeedIdWithStopId, Map<String, List<TripAtStopTime>>>() {
        @Override
        public Map<String, List<TripAtStopTime>> load(GtfsStorage.FeedIdWithStopId key) throws Exception {
            return boardingsForStopByPattern(gtfsStorage.getGtfsFeeds().get(key.feedId), gtfsStorage, trafficDay, key);
        }
    });

    public static Map<String, List<TripAtStopTime>> boardingsForStopByPattern(GTFSFeed feed, GtfsStorage gtfsStorage, LocalDate trafficDay, GtfsStorage.FeedIdWithStopId feedIdWithStopId) {
        return RealtimeFeed.findAllBoardings(gtfsStorage, feedIdWithStopId)
                .filter(boarding -> {
                    String serviceId = feed.trips.get(boarding.getAttrs().tripDescriptor.getTripId()).service_id;
                    Service service = feed.services.get(serviceId);
                    return service.activeOn(trafficDay);
                })
                .map(boarding -> new TripAtStopTime(feedIdWithStopId.feedId, boarding.getAttrs().tripDescriptor, boarding.getAttrs().stop_sequence))
                .sorted(Comparator.comparingInt(boarding -> feed.stopTimes.getUnchecked(boarding.tripDescriptor).stopTimes.get(boarding.stop_sequence).departure_time))
                .collect(Collectors.groupingBy(boarding -> feed.stopTimes.getUnchecked(boarding.tripDescriptor).pattern.pattern_id));
    }

    public static void findAllTripTransfersInto(GraphHopperGtfs graphHopperGtfs, Map<TripAtStopTime, Collection<TripAtStopTime>> result, LocalDate trafficDay) {
        Trips trips = new Trips(graphHopperGtfs.getGtfsStorage());
        trips.setTrafficDay(trafficDay);
        for (Map.Entry<String, GTFSFeed> e : graphHopperGtfs.getGtfsStorage().getGtfsFeeds().entrySet()) {
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
                        Map<TripAtStopTime, Collection<TripAtStopTime>> reducedTripTransfers = trips.findTripTransfers(feed, tripDescriptor, feedKey);
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
