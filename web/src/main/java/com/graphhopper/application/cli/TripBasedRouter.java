package com.graphhopper.application.cli;

import com.carrotsearch.hppc.ObjectIntHashMap;
import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Service;
import com.conveyal.gtfs.model.StopTime;
import com.google.common.cache.LoadingCache;
import com.graphhopper.gtfs.*;
import com.graphhopper.gtfs.analysis.Trips;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class TripBasedRouter {
    private LoadingCache<Trips.TripAtStopTime, Collection<Trips.TripAtStopTime>> tripTransfers;
    private GtfsStorage gtfsStorage;
    int earliestArrivalTime = Integer.MAX_VALUE;
    private GtfsStorage.FeedIdWithStopId destination;
    private ZonedDateTime earliestDepartureTime;
    private ObjectIntHashMap tripDoneFromIndex = new ObjectIntHashMap();

    public TripBasedRouter(LoadingCache<Trips.TripAtStopTime, Collection<Trips.TripAtStopTime>> tripTransfers, GtfsStorage gtfsStorage) {
        this.tripTransfers = tripTransfers;
        this.gtfsStorage = gtfsStorage;
    }

    public void route(Request request) {
        GtfsStorage.FeedIdWithStopId origin = new GtfsStorage.FeedIdWithStopId("gtfs_0", ((GHStationLocation) request.getPoints().get(0)).stop_id);
        destination = new GtfsStorage.FeedIdWithStopId("gtfs_0", ((GHStationLocation) request.getPoints().get(1)).stop_id);

        earliestDepartureTime = request.getEarliestDepartureTime().atZone(ZoneId.of("America/Los_Angeles"));
        GTFSFeed gtfsFeed = gtfsStorage.getGtfsFeeds().get("gtfs_0");
        Map<String, List<PtGraph.PtEdge>> boardingsByPattern = RealtimeFeed.findAllBoardings(gtfsStorage, origin)
                .filter(boarding -> {
                    String serviceId = gtfsFeed.trips.get(boarding.getAttrs().tripDescriptor.getTripId()).service_id;
                    Service service = gtfsFeed.services.get(serviceId);
                    return service.activeOn(earliestDepartureTime.toLocalDate());
                })
                .collect(Collectors.groupingBy(boarding -> gtfsFeed.stopTimes.getUnchecked(boarding.getAttrs().tripDescriptor.getTripId()).pattern.pattern_id));

        List<EnqueuedTripSegment> queue0 = new ArrayList<>();
        boardingsByPattern.forEach((pattern, boardings) -> {
            boardings.stream().filter(boarding -> {
                StopTime stopTime = gtfsFeed.stopTimes.getUnchecked(boarding.getAttrs().tripDescriptor.getTripId()).stopTimes.stream().filter(s -> s.stop_sequence == boarding.getAttrs().stop_sequence).findFirst().get();
                return stopTime.departure_time >= earliestDepartureTime.toLocalTime().toSecondOfDay();
            }).findFirst().map(boarding -> new Trips.TripAtStopTime("gtfs_0", boarding.getAttrs().tripDescriptor, boarding.getAttrs().stop_sequence))
                    .ifPresent(t -> {
                        tripDoneFromIndex.put(t.tripDescriptor.getTripId(), Math.min(tripDoneFromIndex.getOrDefault(t.tripDescriptor.getTripId(), Integer.MAX_VALUE), t.stop_sequence));
                        queue0.add(new EnqueuedTripSegment(t, Integer.MAX_VALUE, 0, null));
                    });
        });
        System.out.println();
        System.out.println("0: "+queue0.size());
        List<EnqueuedTripSegment> queue1 = round(gtfsFeed, queue0);
        System.out.println("1: "+queue1.size());
        List<EnqueuedTripSegment> queue2 = round(gtfsFeed, queue1);
        System.out.println("2: "+queue2.size());
        List<EnqueuedTripSegment> queue3 = round(gtfsFeed, queue2);
        System.out.println("3: "+queue3.size());
        List<EnqueuedTripSegment> queue4 = round(gtfsFeed, queue3);
        System.out.println("4: "+queue4.size());
        List<EnqueuedTripSegment> queue5 = round(gtfsFeed, queue4);
        System.out.println("5: "+queue5.size());
        List<EnqueuedTripSegment> queue6 = round(gtfsFeed, queue5);
        System.out.println("6: "+queue6.size());
        List<EnqueuedTripSegment> queue7 = round(gtfsFeed, queue6);
        System.out.println("7: "+queue7.size());
        List<EnqueuedTripSegment> queue8 = round(gtfsFeed, queue7);
        System.out.println("8: "+queue8.size());
        List<EnqueuedTripSegment> queue9 = round(gtfsFeed, queue8);
        System.out.println("9: "+queue9.size());


    }

    static class EnqueuedTripSegment {
        Trips.TripAtStopTime tripAtStopTime;
        int toStopSequence;
        int plusDays;
        EnqueuedTripSegment parent;

        public EnqueuedTripSegment(Trips.TripAtStopTime tripAtStopTime, int toStopSequence, int plusDays, EnqueuedTripSegment parent) {
            this.tripAtStopTime = tripAtStopTime;
            this.toStopSequence = toStopSequence;
            this.plusDays = plusDays;
            this.parent = parent;
        }
    }

    private List<EnqueuedTripSegment> round(GTFSFeed gtfsFeed, List<EnqueuedTripSegment> queue0) {
        List<EnqueuedTripSegment> queue1 = new ArrayList<>();
        for (EnqueuedTripSegment enqueuedTripSegment : queue0) {
            Trips.TripAtStopTime tripAtStopTime = enqueuedTripSegment.tripAtStopTime;
            Iterator<StopTime> iterator = gtfsFeed.stopTimes.getUnchecked(tripAtStopTime.tripDescriptor.getTripId()).stopTimes.iterator();
            while (iterator.hasNext()) {
                StopTime stopTime = iterator.next();
                if (stopTime.stop_sequence > tripAtStopTime.stop_sequence && stopTime.stop_sequence < enqueuedTripSegment.toStopSequence && stopTime.arrival_time < earliestArrivalTime) {
                    Trips.TripAtStopTime t = new Trips.TripAtStopTime("gtfs_0", tripAtStopTime.tripDescriptor, stopTime.stop_sequence);
                    if (destination.stopId.equals(stopTime.stop_id)) {
                        earliestArrivalTime = stopTime.arrival_time;
                        System.out.printf("%s+%d\n", LocalTime.ofSecondOfDay(stopTime.arrival_time % (60 * 60 * 24)), stopTime.arrival_time / (60 * 60 * 24));
                    }
                    Collection<Trips.TripAtStopTime> transfers = null;
                    try {
                        transfers = tripTransfers.get(t);
                    } catch (ExecutionException e) {
                        throw new RuntimeException(e);
                    }
                    if (transfers == null)
                        continue; // FIXME: overnight stop bug
                    for (Trips.TripAtStopTime transfer : transfers) {
                        GTFSFeed.StopTimesForTripWithTripPatternKey stopTimes = gtfsFeed.stopTimes.getUnchecked(transfer.tripDescriptor.getTripId());
                        StopTime transferStopTime = stopTimes.stopTimes.stream().filter(s -> s.stop_sequence == transfer.stop_sequence).findFirst().get();
                        if (transferStopTime.departure_time < stopTime.arrival_time) {
                            enqueue(queue1, transfer, enqueuedTripSegment, gtfsFeed, 1);
                        } else {
                            enqueue(queue1, transfer, enqueuedTripSegment, gtfsFeed, 0);
                        }
                    }
                }
            }
        }
        return queue1;
    }

    private void enqueue(List<EnqueuedTripSegment> queue1, Trips.TripAtStopTime transfer, EnqueuedTripSegment parent, GTFSFeed gtfsFeed, int plusDays) {
        if (plusDays > 0)
            return;
        String tripId = transfer.tripDescriptor.getTripId();
        int thisTripDoneFromIndex = tripDoneFromIndex.getOrDefault(tripId, Integer.MAX_VALUE);
        if (transfer.stop_sequence < thisTripDoneFromIndex) {
            queue1.add(new EnqueuedTripSegment(transfer, thisTripDoneFromIndex, plusDays, parent));
            GTFSFeed.StopTimesForTripWithTripPatternKey stopTimes = gtfsFeed.stopTimes.getUnchecked(tripId);
            for (String otherTrip : stopTimes.pattern.trips) {
                GTFSFeed.StopTimesForTripWithTripPatternKey otherStopTimes = gtfsFeed.stopTimes.getUnchecked(otherTrip);
                // TODO: Instead just sort them by departure time and put those that come after myself
                int departureTime = stopTimes.stopTimes.get(0).departure_time;
                int otherDepartureTime = otherStopTimes.stopTimes.get(0).departure_time;
                if (otherDepartureTime >= departureTime) {
                    tripDoneFromIndex.put(otherTrip, transfer.stop_sequence);
                }
            }
        }
    }

    private String toString(Trips.TripAtStopTime t) {
        return t.tripDescriptor.getTripId() + " " + t.stop_sequence;
    }
}
