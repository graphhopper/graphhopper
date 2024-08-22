package com.graphhopper.gtfs.analysis;

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.ObjectIntHashMap;
import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.*;
import com.google.common.collect.*;
import com.graphhopper.gtfs.*;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Trips {

    private int maxTransferTime;
    public final List<GTFSFeed.StopTimesForTripWithTripPatternKey> trips;
    private Map<GtfsStorage.FeedIdWithStopId, Map<String, List<TripAtStopTime>>> boardingsForStopByPattern = new ConcurrentHashMap<>();
    private Map<LocalDate, Map<Trips.TripAtStopTime, Collection<Trips.TripAtStopTime>>> tripTransfersPerDay = new ConcurrentHashMap<>();
    public int idx;

    public Trips(GtfsStorage gtfsStorage) {
        this.gtfsStorage = gtfsStorage;
        trips = new ArrayList<>();
        idx = 0;
        for (Map.Entry<String, GTFSFeed> entry : this.gtfsStorage.getGtfsFeeds().entrySet()) {
            GTFSFeed feed = entry.getValue();
            Map<TripPatternKey, Pattern> patterns = new LinkedHashMap<>();
            int nextPatternId = 1;
            for (Trip trip : feed.trips.values()) {
                TripPatternKey key = new TripPatternKey();
                Route route = feed.routes.get(trip.route_id);
                Service service = feed.services.get(trip.service_id);
                List<StopTime> orderedStopTimesForTripWithPadding = new ArrayList<>();
                List<StopTime> interpolatedStopTimesForTrip = feed.getInterpolatedStopTimesForTrip(trip.trip_id);
                if (interpolatedStopTimesForTrip.isEmpty()) {
                    System.out.println("empty trip: "+trip.trip_id);
                    continue;
                }

                interpolatedStopTimesForTrip.forEach(stopTime -> {
                    while (orderedStopTimesForTripWithPadding.size() < stopTime.stop_sequence) {
                        orderedStopTimesForTripWithPadding.add(null); // Padding, so that index == stop_sequence
                    }
                    key.addStopTime(stopTime);
                    orderedStopTimesForTripWithPadding.add(stopTime);
                });
                Pattern pattern = patterns.get(key);
                if (pattern == null) {
                    pattern = new Pattern(key.stops, new ArrayList<>());
                    pattern.pattern_id = feed.feedId + " " + nextPatternId++;
                    patterns.put(key, pattern);
                }
                Collection<Frequency> frequencies = feed.getFrequencies(trip.trip_id);
                if (frequencies.isEmpty()) {
                    GTFSFeed.StopTimesForTripWithTripPatternKey tripPointer = new GTFSFeed.StopTimesForTripWithTripPatternKey(entry.getKey(), trip, service, route.route_type, orderedStopTimesForTripWithPadding, pattern);
                    pattern.trips.add(tripPointer);
                } else {
                    for (Frequency frequency : frequencies) {
                        for (int time = frequency.start_time; time < frequency.end_time; time += frequency.headway_secs) {
                            List<StopTime> orderedStopTimesForUnrolledTripWithPadding = new ArrayList<>();
                            for (StopTime stopTime : orderedStopTimesForTripWithPadding) {
                                if (stopTime != null) {
                                    StopTime stopTimeForUnrolledTrip = stopTime.clone();
                                    stopTimeForUnrolledTrip.arrival_time += time;
                                    stopTimeForUnrolledTrip.departure_time += time;
                                    orderedStopTimesForUnrolledTripWithPadding.add(stopTimeForUnrolledTrip);
                                } else {
                                    orderedStopTimesForUnrolledTripWithPadding.add(null);
                                }
                            }
                            GTFSFeed.StopTimesForTripWithTripPatternKey tripPointer = new GTFSFeed.StopTimesForTripWithTripPatternKey(entry.getKey(), trip, service, route.route_type, orderedStopTimesForUnrolledTripWithPadding, pattern);
                            pattern.trips.add(tripPointer);
                        }
                    }
                }
            }
            for (Pattern pattern : patterns.values()) {
                pattern.trips.sort(Comparator.comparingInt(GTFSFeed.StopTimesForTripWithTripPatternKey::getDepartureTime));
                int endIdxOfPattern = idx + pattern.trips.size();
                for (GTFSFeed.StopTimesForTripWithTripPatternKey tripPointer : pattern.trips) {
                    tripPointer.idx = idx++;
                    tripPointer.endIdxOfPattern = endIdxOfPattern;
                    if (tripPointer.idx == Integer.MAX_VALUE)
                        throw new RuntimeException();
                    trips.add(tripPointer);
                }
            }
        }
        for (GTFSFeed.StopTimesForTripWithTripPatternKey tripPointer : trips) {
            for (int i = 0; i < tripPointer.stopTimes.size(); i++) {
                StopTime stopTime = tripPointer.stopTimes.get(i);
                if (stopTime != null) {
                    Map<String, List<TripAtStopTime>> patternBoardings = boardingsForStopByPattern.computeIfAbsent(new GtfsStorage.FeedIdWithStopId(tripPointer.feedId, stopTime.stop_id), k -> new HashMap<>());
                    List<TripAtStopTime> boardings = patternBoardings.computeIfAbsent(tripPointer.pattern.pattern_id, k -> new ArrayList<>());
                    boardings.add(new TripAtStopTime(tripPointer.idx, i));
                }
            }
        }
    }

    public Map<String, List<TripAtStopTime>> getPatternBoardings(GtfsStorage.FeedIdWithStopId k) {
        return boardingsForStopByPattern.get(k);
    }

    GtfsStorage gtfsStorage;

    private Map<TripAtStopTime, Collection<TripAtStopTime>> findTripTransfers(GTFSFeed.StopTimesForTripWithTripPatternKey tripPointer, String feedKey, LocalDate trafficDay, Map<String, Transfers> transfers, ArrayListMultimap<Integer, GtfsStorage.FeedIdWithStopId> stopsForStationNode) {
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
                GtfsStorage.FeedIdWithStopId boardingStop = it.toPlatformDescriptor;
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
                insertTripTransfers(trafficDay, arrivalTimes, feedKey, stopTime, destinations, new GtfsStorage.FeedIdWithStopId(feedKey, stopTime.stop_id), 0, multimap.get(stopTime.stop_id));
            }
            for (String toStopId : multimap.keySet()) {
                insertTripTransfers(trafficDay, arrivalTimes, feedKey, stopTime, destinations, new GtfsStorage.FeedIdWithStopId(feedKey, toStopId), 0, multimap.get(toStopId));
            }
            for (GtfsStorage.FeedIdWithStopId otherStop : stopsForStationNode.get(gtfsStorage.getStationNodes().get(stopId))) {
                if (!stopId.equals(otherStop)) {
                    insertTripTransfers(trafficDay, arrivalTimes, feedKey, stopTime, destinations, otherStop, 0, Collections.emptyList());
                }
            }
            for (GtfsStorage.InterpolatedTransfer it : gtfsStorage.interpolatedTransfers.get(stopId)) {
                insertTripTransfers(trafficDay, arrivalTimes, feedKey, stopTime, destinations, it.toPlatformDescriptor, it.streetTime, Collections.emptyList());
            }
            result.put(origin, destinations);
        }
        return result;
    }

    private void insertTripTransfers(LocalDate trafficDay, ObjectIntHashMap<GtfsStorage.FeedIdWithStopId> arrivalTimes, String feedKey, StopTime arrivalStopTime, List<TripAtStopTime> destinations, GtfsStorage.FeedIdWithStopId boardingStop, int streetTime, List<Transfer> transfers) {
        GTFSFeed sourceFeed = gtfsStorage.getGtfsFeeds().get(feedKey);
        GTFSFeed destinationFeed = gtfsStorage.getGtfsFeeds().get(boardingStop.feedId);
        ZoneId sourceZoneId = ZoneId.of(sourceFeed.agency.values().stream().findFirst().get().agency_timezone);
        ZoneId destinationZoneId = ZoneId.of(destinationFeed.agency.values().stream().findFirst().get().agency_timezone);
        LocalDateTime scheduleArrivalTime = trafficDay.atStartOfDay().plusSeconds(arrivalStopTime.arrival_time);
        int timeZoneOffset = (int) (scheduleArrivalTime.atZone(sourceZoneId).toEpochSecond() - scheduleArrivalTime.atZone(destinationZoneId).toEpochSecond());
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
                if (departureStopTime.departure_time - timeZoneOffset >= arrivalStopTime.arrival_time + maxTransferTime) {
                    break; // next pattern
                }
                if (trip.service.activeOn(trafficDay)) {
                    if (departureStopTime.departure_time - timeZoneOffset >= earliestDepatureTimeForThisDestination) {
                        boolean keep = false;
                        boolean overnight = false;
                        for (int i = candidate.stop_sequence; i < trip.stopTimes.size(); i++) {
                            StopTime destinationStopTime = trip.stopTimes.get(i);
                            if (destinationStopTime == null)
                                continue;
                            int destinationArrivalTime = destinationStopTime.arrival_time - timeZoneOffset;
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

    public void findAllTripTransfersInto(Map<TripAtStopTime, Collection<TripAtStopTime>> result, LocalDate trafficDay, Map<String, Transfers> transfers, ArrayListMultimap<Integer, GtfsStorage.FeedIdWithStopId> stopsForStationNode, int maxTransferTime) {
        this.maxTransferTime = maxTransferTime;
        Map<TripAtStopTime, Collection<TripAtStopTime>> r = Collections.synchronizedMap(result);
        trips.stream()
            .filter(trip -> trip.service.activeOn(trafficDay))
            .parallel()
            .forEach(tripPointer -> {
                Map<TripAtStopTime, Collection<TripAtStopTime>> reducedTripTransfers = findTripTransfers(tripPointer, tripPointer.feedId, trafficDay, transfers, stopsForStationNode);
                r.putAll(reducedTripTransfers);
            });
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

        public enum ArrivalDeparture { ARRIVAL, DEPARTURE };

        public static String print(Trips.TripAtStopTime transferDestination, Trips tripTransfers, ArrivalDeparture arrivalDeparture) {
            GTFSFeed.StopTimesForTripWithTripPatternKey tripPointer = tripTransfers.trips.get(transferDestination.tripIdx);
            StopTime stopTime = tripPointer.stopTimes.get(transferDestination.stop_sequence);
            return tripPointer.idx + " " + tripPointer.trip.trip_id + " @ " + stopTime.stop_sequence + " " + stopTime.stop_id + " " + (arrivalDeparture == ArrivalDeparture.ARRIVAL ? stopTime.arrival_time : stopTime.departure_time);
        }
    }

    /**
     * Represents a collection of trips that all visit the same stops in the same sequence.
     */
    public static class Pattern extends Entity {
        public static final long serialVersionUID = 1L;

        public String getId () {
            return pattern_id;
        }

        public String pattern_id;

        public List<String> orderedStops;
        public List<GTFSFeed.StopTimesForTripWithTripPatternKey> trips;
        public String name;
        public String feed_id;

        public Pattern (List<String> orderedStops, List<GTFSFeed.StopTimesForTripWithTripPatternKey> trips) {

            // Assign ordered list of stop IDs to be the key of this pattern.
            // FIXME what about pickup / dropoff type?
            this.orderedStops = orderedStops;

            this.trips = trips;

        }

    }

    public static class TripPatternKey {

        public List<String> stops = new ArrayList<>();
        public IntArrayList pickupTypes = new IntArrayList();
        public IntArrayList dropoffTypes = new IntArrayList();

        public TripPatternKey () {
        }

        public void addStopTime (StopTime st) {
            stops.add(st.stop_id);
            pickupTypes.add(st.pickup_type);
            dropoffTypes.add(st.drop_off_type);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TripPatternKey that = (TripPatternKey) o;

            if (!Objects.equals(dropoffTypes, that.dropoffTypes)) return false;
            if (!Objects.equals(pickupTypes, that.pickupTypes)) return false;
            if (!Objects.equals(stops, that.stops)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = stops != null ? stops.hashCode() : 0;
            result = 31 * result + (pickupTypes != null ? pickupTypes.hashCode() : 0);
            result = 31 * result + (dropoffTypes != null ? dropoffTypes.hashCode() : 0);
            return result;
        }

    }
}
