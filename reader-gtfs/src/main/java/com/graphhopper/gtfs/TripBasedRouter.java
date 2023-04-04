package com.graphhopper.gtfs;

import com.carrotsearch.hppc.ObjectIntHashMap;
import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.StopTime;
import com.google.common.cache.LoadingCache;
import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.gtfs.analysis.Trips;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Predicate;

public class TripBasedRouter {
    private LoadingCache<Trips.TripAtStopTime, Collection<Trips.TripAtStopTime>> tripTransfers;
    private GtfsStorage gtfsStorage;
    int earliestArrivalTime = Integer.MAX_VALUE;
    private ObjectIntHashMap<GtfsRealtime.TripDescriptor> tripDoneFromIndex = new ObjectIntHashMap();
    private List<ResultLabel> result = new ArrayList<>();
    private List<StopWithTimeDelta> egressStations;

    public TripBasedRouter(GtfsStorage gtfsStorage, LoadingCache<Trips.TripAtStopTime, Collection<Trips.TripAtStopTime>> tripTransfers) {
        this.gtfsStorage = gtfsStorage;
        this.tripTransfers = tripTransfers;
    }

    public static class StopWithTimeDelta {
        GtfsStorage.FeedIdWithStopId stopId;

        long timeDelta;


        public StopWithTimeDelta(GtfsStorage.FeedIdWithStopId stopId, long timeDelta) {
            this.stopId = stopId;
            this.timeDelta = timeDelta;
        }


    }

    public List<ResultLabel> route(List<StopWithTimeDelta> accessStations, List<StopWithTimeDelta> egressStations, Instant initialTime) {
        this.egressStations = egressStations;
        List<EnqueuedTripSegment> queue0 = new ArrayList<>();
        for (StopWithTimeDelta accessStation : accessStations) {
            GTFSFeed gtfsFeed = gtfsStorage.getGtfsFeeds().get(accessStations.iterator().next().stopId.feedId);
            ZonedDateTime earliestDepartureTime = initialTime.atZone(ZoneId.of("America/Los_Angeles")).plus(accessStation.timeDelta, ChronoUnit.MILLIS);
            Map<String, List<Trips.TripAtStopTime>> boardingsByPattern = Trips.boardingsForStopByPattern(gtfsFeed, gtfsStorage, earliestDepartureTime.toLocalDate(), accessStation.stopId);

            boardingsByPattern.forEach((pattern, boardings) -> {
                boardings.stream()
                        .filter(reachable(gtfsFeed, earliestDepartureTime))
                        .findFirst()
                        .ifPresent(t -> {
                            tripDoneFromIndex.put(t.tripDescriptor, Math.min(tripDoneFromIndex.getOrDefault(t.tripDescriptor, Integer.MAX_VALUE), t.stop_sequence));
                            queue0.add(new EnqueuedTripSegment(t, Integer.MAX_VALUE, 0, null, null));
                        });
            });

        }
        System.out.println();
        System.out.println("0: "+queue0.size());
        List<EnqueuedTripSegment> queue1 = round(queue0);
        System.out.println("1: "+queue1.size());
        List<EnqueuedTripSegment> queue2 = round(queue1);
        System.out.println("2: "+queue2.size());
        List<EnqueuedTripSegment> queue3 = round(queue2);
        System.out.println("3: "+queue3.size());
        List<EnqueuedTripSegment> queue4 = round(queue3);
        System.out.println("4: "+queue4.size());
        List<EnqueuedTripSegment> queue5 = round(queue4);
        System.out.println("5: "+queue5.size());
        List<EnqueuedTripSegment> queue6 = round(queue5);
        System.out.println("6: "+queue6.size());
        List<EnqueuedTripSegment> queue7 = round(queue6);
        System.out.println("7: "+queue7.size());
        List<EnqueuedTripSegment> queue8 = round(queue7);
        System.out.println("8: "+queue8.size());
        List<EnqueuedTripSegment> queue9 = round(queue8);
        System.out.println("9: "+queue9.size());

        return result;

    }

    public static Predicate<? super Trips.TripAtStopTime> reachable(GTFSFeed gtfsFeed, ZonedDateTime earliestDepartureTime) {
        return boarding -> {
            StopTime stopTime = gtfsFeed.stopTimes.getUnchecked(boarding.tripDescriptor).stopTimes.stream().filter(s -> s.stop_sequence == boarding.stop_sequence).findFirst().get();
            return stopTime.departure_time >= earliestDepartureTime.toLocalTime().toSecondOfDay();
        };
    }


    static class EnqueuedTripSegment {
        Trips.TripAtStopTime tripAtStopTime;
        int toStopSequence;
        int plusDays;
        Trips.TripAtStopTime transferOrigin;
        EnqueuedTripSegment parent;

        public EnqueuedTripSegment(Trips.TripAtStopTime tripAtStopTime, int toStopSequence, int plusDays, Trips.TripAtStopTime transferOrigin, EnqueuedTripSegment parent) {
            this.tripAtStopTime = tripAtStopTime;
            this.toStopSequence = toStopSequence;
            this.plusDays = plusDays;
            this.transferOrigin = transferOrigin;
            this.parent = parent;
        }
    }

    private List<EnqueuedTripSegment> round(List<EnqueuedTripSegment> queue0) {
        List<EnqueuedTripSegment> queue1 = new ArrayList<>();
        for (EnqueuedTripSegment enqueuedTripSegment : queue0) {
            String feedId = enqueuedTripSegment.tripAtStopTime.feedId;
            GTFSFeed gtfsFeed = gtfsStorage.getGtfsFeeds().get(feedId);
            Trips.TripAtStopTime tripAtStopTime = enqueuedTripSegment.tripAtStopTime;
            Iterator<StopTime> iterator = gtfsFeed.stopTimes.getUnchecked(tripAtStopTime.tripDescriptor).stopTimes.iterator();
            while (iterator.hasNext()) {
                StopTime stopTime = iterator.next();
                if (stopTime.stop_sequence > tripAtStopTime.stop_sequence && stopTime.stop_sequence < enqueuedTripSegment.toStopSequence && stopTime.arrival_time < earliestArrivalTime) {
                    Trips.TripAtStopTime transferOrigin = new Trips.TripAtStopTime("gtfs_0", tripAtStopTime.tripDescriptor, stopTime.stop_sequence);
                    for (StopWithTimeDelta destination : egressStations) {
                        if (destination.stopId.stopId.equals(stopTime.stop_id) && destination.stopId.feedId.equals(feedId)) {
                            earliestArrivalTime = stopTime.arrival_time;
                            result.add(new ResultLabel(transferOrigin, enqueuedTripSegment));
                            System.out.printf("%s+%d\n", LocalTime.ofSecondOfDay(stopTime.arrival_time % (60 * 60 * 24)), stopTime.arrival_time / (60 * 60 * 24));
                        }
                    }
                    Collection<Trips.TripAtStopTime> transferDestinations = tripTransfers.getUnchecked(transferOrigin);
                    for (Trips.TripAtStopTime transferDestination : transferDestinations) {
                        GTFSFeed.StopTimesForTripWithTripPatternKey stopTimes = gtfsFeed.stopTimes.getUnchecked(transferDestination.tripDescriptor);
                        StopTime transferStopTime = stopTimes.stopTimes.stream().filter(s -> s.stop_sequence == transferDestination.stop_sequence).findFirst().get();
                        if (transferStopTime.departure_time < stopTime.arrival_time) {
                            enqueue(queue1, transferDestination, transferOrigin, enqueuedTripSegment, gtfsFeed, 1);
                        } else {
                            enqueue(queue1, transferDestination, transferOrigin, enqueuedTripSegment, gtfsFeed, 0);
                        }
                    }
                }
            }
        }
        return queue1;
    }

    private void enqueue(List<EnqueuedTripSegment> queue1, Trips.TripAtStopTime transferDestination, Trips.TripAtStopTime transferOrigin, EnqueuedTripSegment parent, GTFSFeed gtfsFeed, int plusDays) {
        if (plusDays > 0)
            return;
        GtfsRealtime.TripDescriptor tripId = transferDestination.tripDescriptor;
        int thisTripDoneFromIndex = tripDoneFromIndex.getOrDefault(tripId, Integer.MAX_VALUE);
        if (transferDestination.stop_sequence < thisTripDoneFromIndex) {
            queue1.add(new EnqueuedTripSegment(transferDestination, thisTripDoneFromIndex, plusDays, transferOrigin, parent));
            GTFSFeed.StopTimesForTripWithTripPatternKey stopTimes = gtfsFeed.stopTimes.getUnchecked(tripId);
            boolean seenMyself = false;
            for (GtfsRealtime.TripDescriptor otherTrip : stopTimes.pattern.trips) {
                // Trips within a pattern are sorted by start time. All that come after me can be marked as done.
                if (tripId.equals(otherTrip))
                    seenMyself = true;
                if (seenMyself) {
                    tripDoneFromIndex.put(otherTrip, transferDestination.stop_sequence);
                }
            }
        }
    }

    private String toString(Trips.TripAtStopTime t) {
        return t.tripDescriptor.getTripId() + " " + t.stop_sequence;
    }

    public static class ResultLabel {
        Trips.TripAtStopTime t;
        EnqueuedTripSegment enqueuedTripSegment;

        public ResultLabel(Trips.TripAtStopTime t, EnqueuedTripSegment enqueuedTripSegment) {
            this.t = t;
            this.enqueuedTripSegment = enqueuedTripSegment;
        }
    }
}
