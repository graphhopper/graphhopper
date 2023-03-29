package com.graphhopper.application.cli;

import com.carrotsearch.hppc.ObjectIntHashMap;
import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.PatternFinder;
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

        List<Trips.TripAtStopTime> queue0 = new ArrayList<>();
        boardingsByPattern.forEach((pattern, boardings) -> {
            boardings.stream().filter(boarding -> {
                StopTime stopTime = gtfsFeed.stopTimes.getUnchecked(boarding.getAttrs().tripDescriptor.getTripId()).stopTimes.stream().filter(s -> s.stop_sequence == boarding.getAttrs().stop_sequence).findFirst().get();
                return stopTime.departure_time >= earliestDepartureTime.toLocalTime().toSecondOfDay();
            }).findFirst().map(boarding -> new Trips.TripAtStopTime("gtfs_0", boarding.getAttrs().tripDescriptor, boarding.getAttrs().stop_sequence))
                    .ifPresent(t -> {
                        tripDoneFromIndex.put(t.tripDescriptor.getTripId(), Math.min(tripDoneFromIndex.getOrDefault(t.tripDescriptor.getTripId(), Integer.MAX_VALUE), t.stop_sequence));
                        queue0.add(t);
                    });
        });
        System.out.println();
        System.out.println("0: "+queue0.size());
        List<Trips.TripAtStopTime> queue1 = round(gtfsFeed, queue0);
        System.out.println("1: "+queue1.size());
        List<Trips.TripAtStopTime> queue2 = round(gtfsFeed, queue1);
        System.out.println("2: "+queue2.size());
        List<Trips.TripAtStopTime> queue3 = round(gtfsFeed, queue2);
        System.out.println("3: "+queue3.size());
        List<Trips.TripAtStopTime> queue4 = round(gtfsFeed, queue3);
        System.out.println("4: "+queue4.size());
        List<Trips.TripAtStopTime> queue5 = round(gtfsFeed, queue4);
        System.out.println("5: "+queue5.size());
        List<Trips.TripAtStopTime> queue6 = round(gtfsFeed, queue5);
        System.out.println("6: "+queue6.size());
        List<Trips.TripAtStopTime> queue7 = round(gtfsFeed, queue6);
        System.out.println("7: "+queue7.size());
        List<Trips.TripAtStopTime> queue8 = round(gtfsFeed, queue7);
        System.out.println("8: "+queue8.size());
        List<Trips.TripAtStopTime> queue9 = round(gtfsFeed, queue8);
        System.out.println("9: "+queue9.size());


    }

    private List<Trips.TripAtStopTime> round(GTFSFeed gtfsFeed, List<Trips.TripAtStopTime> queue0) {
        List<Trips.TripAtStopTime> queue1 = new ArrayList<>();
        for (Trips.TripAtStopTime tripAtStopTime : queue0) {
            Iterator<StopTime> iterator = gtfsFeed.stopTimes.getUnchecked(tripAtStopTime.tripDescriptor.getTripId()).stopTimes.iterator();
            boolean nextDay = false;
            while (iterator.hasNext()) {
                StopTime stopTime = iterator.next();
                if (stopTime.stop_sequence == tripAtStopTime.stop_sequence) {
                    if (stopTime.departure_time < earliestDepartureTime.toLocalTime().toSecondOfDay())
                        nextDay = true;
                } else if (stopTime.stop_sequence > tripAtStopTime.stop_sequence && stopTime.arrival_time < earliestArrivalTime) {
                    Trips.TripAtStopTime t = new Trips.TripAtStopTime("gtfs_0", tripAtStopTime.tripDescriptor, stopTime.stop_sequence);
                    if (destination.stopId.equals(stopTime.stop_id)) {
                        int arrivalTime = stopTime.arrival_time;
                        if (nextDay)
                            arrivalTime += 60 * 60 * 24;
                        if (arrivalTime < earliestArrivalTime) {
                            earliestArrivalTime = arrivalTime;
                            System.out.println(LocalTime.ofSecondOfDay(arrivalTime));
                        }
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
                        enqueue(queue1, transfer, gtfsFeed);
                    }
                }
            }
        }
        return queue1;
    }

    private void enqueue(List<Trips.TripAtStopTime> queue1, Trips.TripAtStopTime transfer, GTFSFeed gtfsFeed) {
        String tripId = transfer.tripDescriptor.getTripId();
        if (transfer.stop_sequence < tripDoneFromIndex.getOrDefault(tripId, Integer.MAX_VALUE)) {
            queue1.add(transfer);
            GTFSFeed.StopTimesForTripWithTripPatternKey stopTimes = gtfsFeed.stopTimes.getUnchecked(tripId);
            for (String otherTrip : stopTimes.pattern.associatedTrips) {
                GTFSFeed.StopTimesForTripWithTripPatternKey otherStopTimes = gtfsFeed.stopTimes.getUnchecked(otherTrip);
                int departureTime = stopTimes.stopTimes.get(0).departure_time;
                if (departureTime < earliestDepartureTime.toLocalTime().toSecondOfDay())
                    departureTime += 60 * 60 * 24;
                int otherDepartureTime = otherStopTimes.stopTimes.get(0).departure_time;
                if (otherDepartureTime < earliestDepartureTime.toLocalTime().toSecondOfDay())
                    otherDepartureTime += 60 * 60 * 24;
                if (otherDepartureTime >= departureTime) {
                    tripDoneFromIndex.put(otherTrip, Math.min(tripDoneFromIndex.getOrDefault(tripId, Integer.MAX_VALUE), transfer.stop_sequence));
                }
            }
        }
    }

    private String toString(Trips.TripAtStopTime t) {
        return t.tripDescriptor.getTripId() + " " + t.stop_sequence;
    }
}
