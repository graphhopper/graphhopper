package com.graphhopper.gtfs.analysis;

import com.carrotsearch.hppc.ObjectIntHashMap;
import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Frequency;
import com.conveyal.gtfs.model.StopTime;
import com.conveyal.gtfs.model.Trip;
import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.gtfs.GraphHopperGtfs;
import com.graphhopper.gtfs.GtfsStorage;
import com.graphhopper.gtfs.PtGraph;
import com.graphhopper.gtfs.RealtimeFeed;

import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.conveyal.gtfs.model.Entity.Writer.convertToGtfsTime;

public class Trips {

    public static Map<TripAtStopTime, Collection<TripAtStopTime>> reduceTripTransfers(Map<TripAtStopTime, Collection<TripAtStopTime>> tripTransfers, GTFSFeed feed, GtfsRealtime.TripDescriptor tripDescriptor, String feedKey, GtfsStorage gtfsStorage) {
        Map<TripAtStopTime, Collection<TripAtStopTime>> result = new HashMap<>();
        Iterable<StopTime> orderedStopTimesForTrip = feed.getOrderedStopTimesForTrip(tripDescriptor.getTripId());
        List<StopTime> stopTimesExceptFirst = StreamSupport.stream(orderedStopTimesForTrip.spliterator(), false).skip(1).collect(Collectors.toList());
        Collections.reverse(stopTimesExceptFirst);

        ObjectIntHashMap<GtfsStorage.FeedIdWithStopId> arrivalTimes = new ObjectIntHashMap<>();
        for (StopTime stopTime : stopTimesExceptFirst) {
            GtfsStorage.FeedIdWithStopId stopId = new GtfsStorage.FeedIdWithStopId(feedKey, stopTime.stop_id);
            int arrivalTime = stopTime.arrival_time + (tripDescriptor.hasStartTime() ? LocalTime.parse(tripDescriptor.getStartTime()).toSecondOfDay() : 0);
            arrivalTimes.put(stopId, Math.min(arrivalTime, arrivalTimes.getOrDefault(stopId, Integer.MAX_VALUE)));
            TripAtStopTime origin = new TripAtStopTime(feedKey, tripDescriptor, stopTime.stop_sequence);
            System.out.printf("%s %s %d %s\n", origin.tripDescriptor.getTripId(), origin.tripDescriptor.hasStartTime() ? origin.tripDescriptor.getStartTime() : "", origin.stop_sequence, stopTime.stop_id);
            Collection<TripAtStopTime> destinations = tripTransfers.get(origin);
            System.out.printf("  %d transfers\n", destinations.size());
            for (TripAtStopTime destination : destinations) {
                System.out.printf("    %s %s %d\n", destination.tripDescriptor.getTripId(), destination.tripDescriptor.hasStartTime() ? destination.tripDescriptor.getStartTime() : "", destination.stop_sequence);
            }

            Collection<TripAtStopTime> filteredDestinations = new ArrayList<>();
            for (TripAtStopTime destination : destinations) {
                boolean keep = false;
                GTFSFeed destinationFeed = gtfsStorage.getGtfsFeeds().get(destination.feedId);
                for (StopTime destinationStopTime : destinationFeed.getOrderedStopTimesForTrip(destination.tripDescriptor.getTripId())) {
                    if (destinationStopTime.stop_sequence > destination.stop_sequence) {
                        int destinationArrivalTime = destinationStopTime.arrival_time + (destination.tripDescriptor.hasStartTime() ? LocalTime.parse(destination.tripDescriptor.getStartTime()).toSecondOfDay() : 0);
                        if (destinationArrivalTime < arrivalTime) {
                            destinationArrivalTime += 24 * 60 * 60;
                        }
                        GtfsStorage.FeedIdWithStopId destinationStopId = new GtfsStorage.FeedIdWithStopId(destination.feedId, destinationStopTime.stop_id);
                        int oldArrivalTime = arrivalTimes.getOrDefault(destinationStopId, Integer.MAX_VALUE);
                        keep = keep || destinationArrivalTime < oldArrivalTime;
                        arrivalTimes.put(destinationStopId, Math.min(oldArrivalTime, destinationArrivalTime));
                    }
                }
                if (keep) {
                    filteredDestinations.add(destination);
                }
            }
            result.put(origin, filteredDestinations);
            System.out.printf("  %d filtered transfers\n", filteredDestinations.size());
            for (TripAtStopTime destination : filteredDestinations) {
                System.out.printf("    %s %s %d\n", destination.tripDescriptor.getTripId(), destination.tripDescriptor.hasStartTime() ? destination.tripDescriptor.getStartTime() : "", destination.stop_sequence);
            }
        }
        return result;
    }

    public static ArrayList<TripAtStopTime> listTransfers(PtGraph ptGraph, int node) {
        ArrayList<TripAtStopTime> result = new ArrayList<>();
        for (PtGraph.PtEdge ptEdge : ptGraph.edgesAround(node)) {
            if (ptEdge.getType() == GtfsStorage.EdgeType.TRANSFER) {
                listTrips(ptGraph, ptEdge.getAdjNode(), result);
            }
        }
        return result;
    }

    private static void listTrips(PtGraph ptGraph, final int startNode, ArrayList<TripAtStopTime> acc) {
        int node = startNode;
        do {
            int thisNode = node;
            node = startNode;
            for (PtGraph.PtEdge ptEdge : ptGraph.edgesAround(thisNode)) {
                if (ptEdge.getType() == GtfsStorage.EdgeType.BOARD) {
                    acc.add(new TripAtStopTime(findFeedIdForBoard(ptGraph, ptEdge), ptEdge.getAttrs().tripDescriptor, ptEdge.getAttrs().stop_sequence));
                } else if (ptEdge.getType() == GtfsStorage.EdgeType.WAIT || ptEdge.getType() == GtfsStorage.EdgeType.OVERNIGHT) {
                    node = ptEdge.getAdjNode();
                }
            }
        } while (node != startNode);
    }

    private static String findFeedIdForBoard(PtGraph ptGraph, PtGraph.PtEdge ptEdge) {
        for (PtGraph.PtEdge edge : ptGraph.backEdgesAround(ptEdge.getBaseNode())) {
            if (edge.getAttrs().feedIdWithTimezone != null) {
                return edge.getAttrs().feedIdWithTimezone.feedId;
            }
        }
        throw new RuntimeException();
    }

    public static Map<TripAtStopTime, Collection<TripAtStopTime>> findReducedTripTransfers(String feedKey, GTFSFeed feed, GtfsRealtime.TripDescriptor tripDescriptor, PtGraph ptGraph, GtfsStorage gtfsStorage) {
        Map<TripAtStopTime, Collection<TripAtStopTime>> tripTransfers = findTripTransfers(feedKey, feed, tripDescriptor, ptGraph, gtfsStorage);
        return reduceTripTransfers(tripTransfers, feed, tripDescriptor, feedKey, gtfsStorage);
    }

    public static Map<TripAtStopTime, Collection<TripAtStopTime>> findTripTransfers(String feedKey, GTFSFeed feed, GtfsRealtime.TripDescriptor tripDescriptor, PtGraph ptGraph, GtfsStorage gtfsStorage) {
        Map<TripAtStopTime, Collection<TripAtStopTime>> tripTransfers = new HashMap<>();
        int[] alightEdgesForTrip = RealtimeFeed.findAlightEdgesForTrip(gtfsStorage, feedKey, feed, RealtimeFeed.normalize(tripDescriptor));
        for (int edge : alightEdgesForTrip) {
            if (edge == -1)
                continue;
            PtGraph.PtEdge ptEdge = ptGraph.edge(edge);
            ArrayList<TripAtStopTime> transferTrips = listTransfers(ptGraph, ptEdge.getAdjNode());
            tripTransfers.put(new TripAtStopTime(feedKey, ptEdge.getAttrs().tripDescriptor, ptEdge.getAttrs().stop_sequence), transferTrips);
        }
        return tripTransfers;
    }

    public static Map<TripAtStopTime, Collection<TripAtStopTime>> findAllTripTransfers(GraphHopperGtfs graphHopperGtfs) {
        Map<TripAtStopTime, Collection<TripAtStopTime>> result = new HashMap<>();
        for (Map.Entry<String, GTFSFeed> e : graphHopperGtfs.getGtfsStorage().getGtfsFeeds().entrySet()) {
            String feedKey = e.getKey();
            GTFSFeed feed = e.getValue();
            int total = feed.trips.size();
            int i = 0;
            for (Trip trip : feed.trips.values()) {
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
                    Map<TripAtStopTime, Collection<TripAtStopTime>> reducedTripTransfers = findReducedTripTransfers(feedKey, feed, tripDescriptor, graphHopperGtfs.getPtGraph(), graphHopperGtfs.getGtfsStorage());
                    result.putAll(reducedTripTransfers);
                }
                System.out.printf("%d / %d trips processed\n", i, total);
                i++;
            }
        }
        return result;
    }

    public static class TripAtStopTime {

        public String feedId;
        GtfsRealtime.TripDescriptor tripDescriptor;
        int stop_sequence;

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

    }

}
