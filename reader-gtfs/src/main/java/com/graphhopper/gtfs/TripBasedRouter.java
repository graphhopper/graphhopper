package com.graphhopper.gtfs;

import com.carrotsearch.hppc.ObjectIntHashMap;
import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.StopTime;
import com.conveyal.gtfs.model.Trip;
import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.gtfs.analysis.Trips;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

import static java.util.Comparator.comparingInt;

public class TripBasedRouter {

    private static final Logger logger = LoggerFactory.getLogger(TripBasedRouter.class);

    private final Trips tripTransfers;
    private GtfsStorage gtfsStorage;
    LocalDateTime earliestArrivalTime = LocalDateTime.MAX;
    private ObjectIntHashMap<Trips.TripKey> tripDoneFromIndex = new ObjectIntHashMap();
    private List<ResultLabel> result = new ArrayList<>();
    private Parameters parameters;

    public TripBasedRouter(GtfsStorage gtfsStorage, Trips tripTransfers) {
        this.gtfsStorage = gtfsStorage;
        this.tripTransfers = tripTransfers;
    }

    public static class StopWithTimeDelta {
        GtfsStorage.FeedIdWithStopId stopId;
        ZoneId zoneId;
        long timeDelta;

        public StopWithTimeDelta(GtfsStorage.FeedIdWithStopId stopId, ZoneId zoneId, long timeDelta) {
            this.stopId = stopId;
            this.zoneId = zoneId;
            this.timeDelta = timeDelta;
        }
    }

    public List<ResultLabel> routeNaiveProfileWithNaiveBetas(Parameters parameters) {
        for (StopWithTimeDelta accessStation : parameters.getAccessStations()) {
            Parameters newParameters = new Parameters(Collections.singletonList(accessStation), parameters.getEgressStations(), parameters.getProfileStartTime(), parameters.getProfileLength(), parameters.getTripFilter(), parameters.getBetaAccessTime(), parameters.getBetaEgressTime());
            routeNaiveProfile(newParameters);
        }
        return result;
    }

    public List<ResultLabel> routeNaiveProfile(Parameters parameters) {
        this.parameters = parameters;
        while (!parameters.getProfileLength().isNegative()) {
            Instant initialTime = parameters.getProfileStartTime().plus(parameters.getProfileLength());
            route(parameters.getAccessStations(), parameters.getEgressStations(), initialTime, parameters.getTripFilter());
            parameters.setProfileLength(parameters.getProfileLength().minus(Duration.ofMinutes(1)));
        }
        route(parameters.getAccessStations(), parameters.getEgressStations(), parameters.getProfileStartTime(), parameters.getTripFilter());
        return result;
    }

    public List<ResultLabel> route(List<StopWithTimeDelta> accessStations, List<StopWithTimeDelta> egressStations, Instant initialTime, Predicate<GTFSFeed.StopTimesForTripWithTripPatternKey> tripFilter) {
        List<EnqueuedTripSegment> queue = new ArrayList<>();
        for (StopWithTimeDelta accessStation : accessStations) {
            Map<GtfsRealtime.TripDescriptor, GTFSFeed.StopTimesForTripWithTripPatternKey> tripsForThisFeed = tripTransfers.trips.get(accessStation.stopId.feedId);
            ZonedDateTime earliestDepartureTime = initialTime.atZone(accessStation.zoneId).plus(accessStation.timeDelta, ChronoUnit.MILLIS);
            LocalDate serviceDay = earliestDepartureTime.toLocalDate(); // FIXME service day across timezones FIXME service day wraparound
            Map<String, List<Trips.TripAtStopTime>> boardingsByPattern = tripTransfers.getPatternBoardings(accessStation.stopId);
            int targetSecondOfDay = earliestDepartureTime.toLocalTime().toSecondOfDay();
            for (List<Trips.TripAtStopTime> boardings : boardingsByPattern.values()) {
                int index = binarySearch(targetSecondOfDay, boardings, boarding -> tripsForThisFeed.get(boarding.tripDescriptor).stopTimes.get(boarding.stop_sequence).departure_time);
                for (int i = index; i < boardings.size(); i++) {
                    Trips.TripAtStopTime boarding = boardings.get(i);
                    GTFSFeed.StopTimesForTripWithTripPatternKey trip = tripsForThisFeed.get(boarding.tripDescriptor);
                    if (trip.service.activeOn(serviceDay) && tripFilter.test(trip)) {
                        enqueue(queue, boarding, null, null, accessStation.stopId.feedId, serviceDay, accessStation);
                        break;
                    }
                }
            }
        }
        queue.sort(comparingInt(this::getDepartureTime));
        iterate(queue);
        return result;
    }

    private static int binarySearch(int targetSecondOfDay, List<Trips.TripAtStopTime> boardings, ToIntFunction<Trips.TripAtStopTime> tripAtStopTimeToIntFunction) {
        int index = Collections.binarySearch(boardings, null, (boarding, key) -> {
            int x = tripAtStopTimeToIntFunction.applyAsInt(boarding);
            return x >= targetSecondOfDay ? 1 : -1;
        });
        return index >= 0 ? index : (- index) - 1;
    }

    private void iterate(List<EnqueuedTripSegment> queue) {
        int round = 0;
        checkArrivals(queue, round);
        while (queue.size() != 0 && round < 3) {
            logger.trace("Round {}", round);
            List<EnqueuedTripSegment> queue1 = enqueueTransfers(queue);
            queue = queue1;
            round = round + 1;
            checkArrivals(queue, round);
        }
    }

    static class EnqueuedTripSegment {
        Trips.TripAtStopTime tripAtStopTime;
        int toStopSequence;
        LocalDate serviceDay;
        Trips.TripAtStopTime transferOrigin;
        EnqueuedTripSegment parent;
        StopWithTimeDelta accessStation;

        public EnqueuedTripSegment(Trips.TripAtStopTime tripAtStopTime, int toStopSequence, LocalDate serviceDay, Trips.TripAtStopTime transferOrigin, EnqueuedTripSegment parent, StopWithTimeDelta accessStation) {
            this.tripAtStopTime = tripAtStopTime;
            this.toStopSequence = toStopSequence;
            this.serviceDay = serviceDay;
            this.transferOrigin = transferOrigin;
            this.parent = parent;
            this.accessStation = accessStation;
        }

        @Override
        public String toString() {
            return "EnqueuedTripSegment{" +
                    "tripAtStopTime=" + tripAtStopTime +
                    ", serviceDay=" + serviceDay +
                    '}';
        }
    }

    private List<EnqueuedTripSegment> enqueueTransfers(List<EnqueuedTripSegment> queue0) {
        List<EnqueuedTripSegment> queue1 = new ArrayList<>();
        for (EnqueuedTripSegment enqueuedTripSegment : queue0) {
            logger.trace("{}", enqueuedTripSegment);
            String feedId = enqueuedTripSegment.tripAtStopTime.feedId;
            Trips.TripAtStopTime tripAtStopTime = enqueuedTripSegment.tripAtStopTime;
            List<StopTime> stopTimes = tripTransfers.trips.get(feedId).get(tripAtStopTime.tripDescriptor).stopTimes;
            int toStopSequence = Math.min(enqueuedTripSegment.toStopSequence, stopTimes.size());
            for (int i = tripAtStopTime.stop_sequence + 1; i < toStopSequence; i++) {
                StopTime stopTime = stopTimes.get(i);
                if (stopTime == null) continue;
                if (!getArrivalTime(enqueuedTripSegment, stopTime, 0).isBefore(earliestArrivalTime))
                    break;
                Trips.TripAtStopTime transferOrigin = new Trips.TripAtStopTime(feedId, tripAtStopTime.tripDescriptor, stopTime.stop_sequence);
                logger.trace("  {}", stopTime);
                Collection<Trips.TripAtStopTime> transferDestinations = gtfsStorage.tripTransfers.getTripTransfers(enqueuedTripSegment.serviceDay).get(transferOrigin);
                if (transferDestinations == null) {
                    continue; // schedule day not prepared
                }
                for (Trips.TripAtStopTime transferDestination : transferDestinations) {
                    GTFSFeed.StopTimesForTripWithTripPatternKey destinationStopTimes = tripTransfers.trips.get(transferDestination.feedId).get(transferDestination.tripDescriptor);
                    StopTime transferStopTime = destinationStopTimes.stopTimes.get(transferDestination.stop_sequence);
                    if (destinationStopTimes.service.activeOn(enqueuedTripSegment.serviceDay) && parameters.getTripFilter().test(destinationStopTimes)) {
                        logger.trace("    {}", transferDestination);
                        if (transferStopTime.departure_time < stopTime.arrival_time) {
                            enqueue(queue1, transferDestination, transferOrigin, enqueuedTripSegment, feedId, enqueuedTripSegment.serviceDay, enqueuedTripSegment.accessStation);
                        } else {
                            enqueue(queue1, transferDestination, transferOrigin, enqueuedTripSegment, feedId, enqueuedTripSegment.serviceDay, enqueuedTripSegment.accessStation);
                        }
                    }
                }
            }
        }
        return queue1;
    }

    private void checkArrivals(List<EnqueuedTripSegment> queue0, int round) {
        for (EnqueuedTripSegment enqueuedTripSegment : queue0) {
            String feedId = enqueuedTripSegment.tripAtStopTime.feedId;
            Trips.TripAtStopTime tripAtStopTime = enqueuedTripSegment.tripAtStopTime;
            List<StopTime> stopTimes = tripTransfers.trips.get(feedId).get(tripAtStopTime.tripDescriptor).stopTimes;
            int toStopSequence = Math.min(enqueuedTripSegment.toStopSequence, stopTimes.size());
            for (int i = tripAtStopTime.stop_sequence + 1; i < toStopSequence; i++) {
                StopTime stopTime = stopTimes.get(i);
                if (stopTime == null) continue;
                for (StopWithTimeDelta destination : parameters.getEgressStations()) {
                    LocalDateTime newArrivalTime = getArrivalTime(enqueuedTripSegment, stopTime, (int) (destination.timeDelta / 1000L));
                    if (destination.stopId.stopId.equals(stopTime.stop_id) && destination.stopId.feedId.equals(feedId) && newArrivalTime.isBefore(earliestArrivalTime)) {
                        earliestArrivalTime = newArrivalTime;
                        ResultLabel newResult = new ResultLabel(round, destination, new Trips.TripAtStopTime(feedId, tripAtStopTime.tripDescriptor, stopTime.stop_sequence), enqueuedTripSegment);
                        int newRealTransfers = newResult.getRealTransfers();
                        int newDepartureTime = newResult.getDepartureTime();
                        Iterator<ResultLabel> it = result.iterator();
                        while (it.hasNext()) {
                            ResultLabel oldResult = it.next();
                            if (oldResult.getArrivalTime().isBefore(newArrivalTime)) continue;
                            if (oldResult.getRealTransfers() < newRealTransfers) continue;
                            if (oldResult.getDepartureTime() > newDepartureTime) continue;
                            it.remove();
                        }
                        result.add(newResult);
                    }
                }
            }
        }
    }

    private LocalDateTime getArrivalTime(EnqueuedTripSegment enqueuedTripSegment, StopTime stopTime, int extraSeconds) {
        long extraDisutilityOfAccessSeconds = ((long) (enqueuedTripSegment.accessStation.timeDelta * (parameters.getBetaAccessTime() - 1.0))) / 1000L;
        return enqueuedTripSegment.serviceDay.atStartOfDay().plusSeconds(stopTime.arrival_time + extraDisutilityOfAccessSeconds + extraSeconds);
    }

    private void enqueue(List<EnqueuedTripSegment> queue1, Trips.TripAtStopTime transferDestination, Trips.TripAtStopTime transferOrigin, EnqueuedTripSegment parent, String gtfsFeed, LocalDate serviceDay, StopWithTimeDelta accessStation) {
        GtfsRealtime.TripDescriptor tripId = transferDestination.tripDescriptor;
        Trips.TripKey tripKey = new Trips.TripKey(gtfsFeed, tripId.getTripId(), tripId.getStartTime().isEmpty() ? 0 : LocalTime.parse(tripId.getStartTime()).toSecondOfDay(), serviceDay);
        int thisTripDoneFromIndex = tripDoneFromIndex.getOrDefault(tripKey, Integer.MAX_VALUE);
        if (transferDestination.stop_sequence < thisTripDoneFromIndex) {
            queue1.add(new EnqueuedTripSegment(transferDestination, thisTripDoneFromIndex, serviceDay, transferOrigin, parent, accessStation));
            markAsDone(transferDestination, tripId, serviceDay);
        }
    }

    private void markAsDone(Trips.TripAtStopTime transferDestination, GtfsRealtime.TripDescriptor tripId, LocalDate serviceDay) {
        GTFSFeed.StopTimesForTripWithTripPatternKey stopTimes = tripTransfers.trips.get(transferDestination.feedId).get(tripId);
        boolean seenMyself = false;
        for (GtfsRealtime.TripDescriptor otherTrip : stopTimes.pattern.trips) {
            // Trips within a pattern are sorted by start time. All that come after me can be marked as done.
            if (tripId.equals(otherTrip))
                seenMyself = true;
            if (seenMyself) {
                Trips.TripKey otherTripKey = new Trips.TripKey(transferDestination.feedId, otherTrip.getTripId(), otherTrip.getStartTime().isEmpty() ? 0 : LocalTime.parse(otherTrip.getStartTime()).toSecondOfDay(), serviceDay);
                int previousDoneFromIndex = tripDoneFromIndex.getOrDefault(otherTripKey, Integer.MAX_VALUE);
                if (transferDestination.stop_sequence < previousDoneFromIndex)
                    tripDoneFromIndex.put(otherTripKey, transferDestination.stop_sequence);
                // TODO: and on later service days, in principle, otherwise we may do much too much work.
                // alternative: keep labels only for today, and just don't check it off for trips that are not today?
            }
        }
        if (!seenMyself) {
            throw new RuntimeException();
        }
    }

    public class ResultLabel {
        private final int round;
        public final StopWithTimeDelta destination;
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
            List<StopTime> stopTimes = tripTransfers.trips.get(t.feedId).get(t.tripDescriptor).stopTimes;
            return stopTimes.get(t.stop_sequence);
        }

        int getDepartureTime() {
            return TripBasedRouter.this.getDepartureTime(enqueuedTripSegment);
        }

        public StopWithTimeDelta getAccessStop() {
            EnqueuedTripSegment i = enqueuedTripSegment;
            while (i.parent != null)
                i = i.parent;
            return i.accessStation;
        }

        LocalDateTime getArrivalTime() {
            return TripBasedRouter.this.getArrivalTime(enqueuedTripSegment, getStopTime(), (int) ((destination.timeDelta / 1000L) * parameters.getBetaEgressTime()));
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

    private int getDepartureTime(EnqueuedTripSegment i) {
        while (i.parent != null)
            i = i.parent;
        List<StopTime> stopTimes = tripTransfers.trips.get(i.tripAtStopTime.feedId).get(i.tripAtStopTime.tripDescriptor).stopTimes;
        StopTime stopTime = stopTimes.get(i.tripAtStopTime.stop_sequence);
        return stopTime.departure_time;
    }

    static class Parameters {
        private final List<StopWithTimeDelta> accessStations;
        private final List<StopWithTimeDelta> egressStations;
        private final Instant profileStartTime;
        private Duration profileLength;
        private final Predicate<GTFSFeed.StopTimesForTripWithTripPatternKey> tripFilter;
        private final double betaAccessTime;
        private final double betaEgressTime;

        Parameters(List<StopWithTimeDelta> accessStations, List<StopWithTimeDelta> egressStations, Instant profileStartTime, Duration profileLength, Predicate<GTFSFeed.StopTimesForTripWithTripPatternKey> tripFilter, double betaAccessTime, double betaEgressTime) {
            this.accessStations = accessStations;
            this.egressStations = egressStations;
            this.profileStartTime = profileStartTime;
            this.profileLength = profileLength;
            this.tripFilter = tripFilter;
            this.betaAccessTime = betaAccessTime;
            this.betaEgressTime = betaEgressTime;
        }

        public List<StopWithTimeDelta> getAccessStations() {
            return accessStations;
        }

        public List<StopWithTimeDelta> getEgressStations() {
            return egressStations;
        }

        public Instant getProfileStartTime() {
            return profileStartTime;
        }

        public Duration getProfileLength() {
            return profileLength;
        }

        public Predicate<GTFSFeed.StopTimesForTripWithTripPatternKey> getTripFilter() {
            return tripFilter;
        }

        public void setProfileLength(Duration profileLength) {
            this.profileLength = profileLength;
        }

        public double getBetaAccessTime() {
            return betaAccessTime;
        }

        public double getBetaEgressTime() {
            return betaEgressTime;
        }
    }
}
