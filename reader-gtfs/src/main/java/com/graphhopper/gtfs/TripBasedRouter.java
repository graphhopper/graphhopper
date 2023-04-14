package com.graphhopper.gtfs;

import com.carrotsearch.hppc.ObjectIntHashMap;
import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Service;
import com.conveyal.gtfs.model.StopTime;
import com.conveyal.gtfs.model.Trip;
import com.google.common.cache.LoadingCache;
import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.gtfs.analysis.Trips;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class TripBasedRouter {
    private final Trips trips;
    private GtfsStorage gtfsStorage;
    int earliestArrivalTime = Integer.MAX_VALUE;
    private ObjectIntHashMap<GtfsRealtime.TripDescriptor> tripDoneFromIndex = new ObjectIntHashMap();
    private List<ResultLabel> result = new ArrayList<>();
    private List<StopWithTimeDelta> egressStations;
    private LocalDate trafficDay;

    public TripBasedRouter(GtfsStorage gtfsStorage, Trips trips) {
        this.gtfsStorage = gtfsStorage;
        this.trips = trips;
    }

    public static class StopWithTimeDelta {
        GtfsStorage.FeedIdWithStopId stopId;

        long timeDelta;


        public StopWithTimeDelta(GtfsStorage.FeedIdWithStopId stopId, long timeDelta) {
            this.stopId = stopId;
            this.timeDelta = timeDelta;
        }


    }

    public List<ResultLabel> routeNaiveProfile(List<StopWithTimeDelta> accessStations, List<StopWithTimeDelta> egressStations, Instant profileStartTime, Duration profileLength) {
        while (!profileLength.isNegative()) {
            Instant initialTime = profileStartTime.plus(profileLength);
            route(accessStations, egressStations, initialTime);
            profileLength = profileLength.minus(Duration.ofMinutes(1));
        }
        route(accessStations, egressStations, profileStartTime);
        return result;
    }

    public List<ResultLabel> route(List<StopWithTimeDelta> accessStations, List<StopWithTimeDelta> egressStations, Instant initialTime) {
        this.egressStations = egressStations;
        List<EnqueuedTripSegment> queue0 = new ArrayList<>();
        for (StopWithTimeDelta accessStation : accessStations) {
            GTFSFeed gtfsFeed = gtfsStorage.getGtfsFeeds().get(accessStations.iterator().next().stopId.feedId);
            ZonedDateTime earliestDepartureTime = initialTime.atZone(ZoneId.of("America/Los_Angeles")).plus(accessStation.timeDelta, ChronoUnit.MILLIS);
            trafficDay = earliestDepartureTime.toLocalDate();
            Map<String, List<Trips.TripAtStopTime>> boardingsByPattern = trips.boardingsForStopByPattern.getUnchecked(accessStation.stopId);
            for (List<Trips.TripAtStopTime> boardings : boardingsByPattern.values()) {
                for (Trips.TripAtStopTime boarding : boardings) {
                    GTFSFeed.StopTimesForTripWithTripPatternKey stopTimesForTripWithTripPatternKey = gtfsFeed.stopTimes.getUnchecked(boarding.tripDescriptor);
                    StopTime stopTime = stopTimesForTripWithTripPatternKey.stopTimes.get(boarding.stop_sequence);
                    if (stopTime.departure_time >= earliestDepartureTime.toLocalTime().toSecondOfDay()) {
                        String serviceId = gtfsFeed.trips.get(boarding.tripDescriptor.getTripId()).service_id;
                        Service service = gtfsFeed.services.get(serviceId);
                        if (service.activeOn(trafficDay)) {
                            enqueue(queue0, boarding, null, null, gtfsFeed, 0);
                            break;
                        }
                    }
                }
            }
        }
        iterate(queue0);
        return result;
    }

    private void iterate(List<EnqueuedTripSegment> queue0) {
        int round = 0;
        while (queue0.size() != 0) {
            queue0 = round(queue0, round);
            round = round + 1;
        }
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

    private List<EnqueuedTripSegment> round(List<EnqueuedTripSegment> queue0, int round) {
        for (EnqueuedTripSegment enqueuedTripSegment : queue0) {
            String feedId = enqueuedTripSegment.tripAtStopTime.feedId;
            GTFSFeed gtfsFeed = gtfsStorage.getGtfsFeeds().get(feedId);
            Trips.TripAtStopTime tripAtStopTime = enqueuedTripSegment.tripAtStopTime;
            List<StopTime> stopTimes = gtfsFeed.stopTimes.getUnchecked(tripAtStopTime.tripDescriptor).stopTimes;
            int toStopSequence = Math.min(enqueuedTripSegment.toStopSequence, stopTimes.size());
            for (int i = tripAtStopTime.stop_sequence + 1; i < toStopSequence; i++) {
                StopTime stopTime = stopTimes.get(i);
                if (stopTime == null) continue;
                if (stopTime.arrival_time >= earliestArrivalTime)
                    break;
                for (StopWithTimeDelta destination : egressStations) {
                    int newArrivalTime = stopTime.arrival_time + (int) (destination.timeDelta / 1000);
                    if (destination.stopId.stopId.equals(stopTime.stop_id) && destination.stopId.feedId.equals(feedId) && newArrivalTime < earliestArrivalTime) {
                        earliestArrivalTime = newArrivalTime;
                        ResultLabel newResult = new ResultLabel(round, destination, new Trips.TripAtStopTime("gtfs_0", tripAtStopTime.tripDescriptor, stopTime.stop_sequence), enqueuedTripSegment);
                        int newRealTransfers = newResult.getRealTransfers();
                        int newDepartureTime = newResult.getDepartureTime();
                        Iterator<ResultLabel> it = result.iterator();
                        while (it.hasNext()) {
                            ResultLabel oldResult = it.next();
                            if (oldResult.getArrivalTime() < newArrivalTime) continue;
                            if (oldResult.getRealTransfers() < newRealTransfers) continue;
                            if (oldResult.getDepartureTime() > newDepartureTime) continue;
                            it.remove();
                        }
                        result.add(newResult);
                        System.out.println(newResult);
                    }
                }
            }
        }
        List<EnqueuedTripSegment> queue1 = new ArrayList<>();
        for (EnqueuedTripSegment enqueuedTripSegment : queue0) {
            String feedId = enqueuedTripSegment.tripAtStopTime.feedId;
            GTFSFeed gtfsFeed = gtfsStorage.getGtfsFeeds().get(feedId);
            Trips.TripAtStopTime tripAtStopTime = enqueuedTripSegment.tripAtStopTime;
            List<StopTime> stopTimes = gtfsFeed.stopTimes.getUnchecked(tripAtStopTime.tripDescriptor).stopTimes;
            int toStopSequence = Math.min(enqueuedTripSegment.toStopSequence, stopTimes.size());
            for (int i = tripAtStopTime.stop_sequence + 1; i < toStopSequence; i++) {
                StopTime stopTime = stopTimes.get(i);
                if (stopTime == null) continue;
                if (stopTime.arrival_time >= earliestArrivalTime)
                    break;
                Trips.TripAtStopTime transferOrigin = new Trips.TripAtStopTime("gtfs_0", tripAtStopTime.tripDescriptor, stopTime.stop_sequence);
                Collection<Trips.TripAtStopTime> transferDestinations = gtfsStorage.getTripTransfers(trafficDay).get(transferOrigin);
                for (Trips.TripAtStopTime transferDestination : transferDestinations) {
                    GTFSFeed destinationFeed = gtfsStorage.getGtfsFeeds().get(transferDestination.feedId);
                    GTFSFeed.StopTimesForTripWithTripPatternKey destinationStopTimes = destinationFeed.stopTimes.getUnchecked(transferDestination.tripDescriptor);
                    StopTime transferStopTime = destinationStopTimes.stopTimes.get(transferDestination.stop_sequence);
                    String serviceId = destinationFeed.trips.get(transferDestination.tripDescriptor.getTripId()).service_id;
                    Service service = destinationFeed.services.get(serviceId);
                    if (service.activeOn(trafficDay)) {
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
            markAsDone(transferDestination, gtfsFeed, tripId);
        }
    }

    private void markAsDone(Trips.TripAtStopTime transferDestination, GTFSFeed gtfsFeed, GtfsRealtime.TripDescriptor tripId) {
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
        if (!seenMyself) {
            throw new RuntimeException();
        }
    }

    public class ResultLabel {
        private final int round;
        private final StopWithTimeDelta destination;
        public Trips.TripAtStopTime t;
        public EnqueuedTripSegment enqueuedTripSegment;

        public ResultLabel(int round, StopWithTimeDelta destination, Trips.TripAtStopTime t, EnqueuedTripSegment enqueuedTripSegment) {
            this.round = round;
            this.destination = destination;
            this.t = t;
            this.enqueuedTripSegment = enqueuedTripSegment;
        }

        @Override
        public String toString() {
            StopTime stopTime = getStopTime();
            return String.format("%s+%d %s", LocalTime.ofSecondOfDay(stopTime.arrival_time % (60 * 60 * 24)), stopTime.arrival_time / (60 * 60 * 24), stopTime.stop_id);
        }

        private StopTime getStopTime() {
            GTFSFeed gtfsFeed = gtfsStorage.getGtfsFeeds().get(t.feedId);
            List<StopTime> stopTimes = gtfsFeed.stopTimes.getUnchecked(t.tripDescriptor).stopTimes;
            return stopTimes.get(t.stop_sequence);
        }

        int getDepartureTime() {
            EnqueuedTripSegment i = enqueuedTripSegment;
            while (i.parent != null)
                i = i.parent;
            GTFSFeed gtfsFeed = gtfsStorage.getGtfsFeeds().get(i.tripAtStopTime.feedId);
            List<StopTime> stopTimes = gtfsFeed.stopTimes.getUnchecked(i.tripAtStopTime.tripDescriptor).stopTimes;
            StopTime stopTime = stopTimes.get(i.tripAtStopTime.stop_sequence);
            return stopTime.departure_time;
        }

        int getArrivalTime() {
            StopTime stopTime = getStopTime();
            return stopTime.arrival_time + (int) (destination.timeDelta / 1000L);
        }

        public int getRound() {
            return round;
        }

        public int getRealTransfers() {
            int result = 0;
            EnqueuedTripSegment i = enqueuedTripSegment;
            while (i.parent != null) {
                GTFSFeed gtfsFeed1 = gtfsStorage.getGtfsFeeds().get(i.tripAtStopTime.feedId);
                Trip trip1 = gtfsFeed1.trips.get(i.tripAtStopTime.tripDescriptor.getTripId());
                GTFSFeed gtfsFeed2 = gtfsStorage.getGtfsFeeds().get(i.transferOrigin.feedId);
                Trip trip2 = gtfsFeed2.trips.get(i.transferOrigin.tripDescriptor.getTripId());
                if (trip1.block_id == null || trip2.block_id == null || !trip1.block_id.equals(trip2.block_id)) {
                    result = result + 1;
                }
                i = i.parent;
            }
            return result;
        }
    }
}
