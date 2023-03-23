package com.graphhopper.gtfs.analysis;

import com.carrotsearch.hppc.ObjectIntHashMap;
import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.StopTime;
import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.gtfs.GtfsStorage;
import com.graphhopper.gtfs.PtGraph;

import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class Trips {

    public static void computeReducedTransfers(Map<TripAtStopTime, Collection<TripAtStopTime>> tripTransfers, Map.Entry<String, GTFSFeed> e, GTFSFeed feed, GtfsRealtime.TripDescriptor tripDescriptor) {
        Iterable<StopTime> orderedStopTimesForTrip = feed.getOrderedStopTimesForTrip(tripDescriptor.getTripId());
        List<StopTime> stopTimesExceptFirst = StreamSupport.stream(orderedStopTimesForTrip.spliterator(), false).skip(1).collect(Collectors.toList());
        Collections.reverse(stopTimesExceptFirst);

        ObjectIntHashMap<GtfsStorage.FeedIdWithStopId> arrivalTimes = new ObjectIntHashMap<>();
        for (StopTime stopTime : stopTimesExceptFirst) {
            GtfsStorage.FeedIdWithStopId stopId = new GtfsStorage.FeedIdWithStopId(e.getKey(), stopTime.stop_id);
            int arrivalTime = stopTime.arrival_time + (tripDescriptor.hasStartTime() ? LocalTime.parse(tripDescriptor.getStartTime()).toSecondOfDay() : 0);
            arrivalTimes.put(stopId, Math.min(arrivalTime, arrivalTimes.getOrDefault(stopId, Integer.MAX_VALUE)));
            TripAtStopTime origin = new TripAtStopTime(e.getKey(), tripDescriptor, stopTime.stop_sequence);
            System.out.printf("%s %s %d %s\n", origin.tripDescriptor.getTripId(), origin.tripDescriptor.hasStartTime() ? origin.tripDescriptor.getStartTime() : "", origin.stop_sequence, stopTime.stop_id);
            Collection<TripAtStopTime> destinations = tripTransfers.get(origin);
            System.out.printf("  %d transfers\n", destinations.size());
            Collection<TripAtStopTime> filteredDestinations = new ArrayList<>();
            for (TripAtStopTime destination : destinations) {
                boolean keep = false;
                for (StopTime destinationStopTime : feed.getOrderedStopTimesForTrip(destination.tripDescriptor.getTripId())) {
                    if (destinationStopTime.stop_sequence > destination.stop_sequence) {
                        int destinationArrivalTime = destinationStopTime.arrival_time + (destination.tripDescriptor.hasStartTime() ? LocalTime.parse(destination.tripDescriptor.getStartTime()).toSecondOfDay() : 0);
                        if (destinationArrivalTime < arrivalTime) {
                            destinationArrivalTime += 24 * 60 * 60;
                        }
                        GtfsStorage.FeedIdWithStopId destinationStopId = new GtfsStorage.FeedIdWithStopId(destination.feedId, destinationStopTime.stop_id);
                        keep = keep || destinationArrivalTime < arrivalTimes.getOrDefault(destinationStopId, Integer.MAX_VALUE);
                        arrivalTimes.put(destinationStopId, Math.min(arrivalTimes.get(destinationStopId), arrivalTime));
                    }
                }
                if (keep) {
                    filteredDestinations.add(destination);
                }
            }
            System.out.printf("  %d filtered transfers\n", filteredDestinations.size());
            for (TripAtStopTime destination : filteredDestinations) {
                System.out.printf("    %s %s %d %s\n", destination.tripDescriptor.getTripId(), destination.tripDescriptor.hasStartTime() ? destination.tripDescriptor.getStartTime() : "", destination.stop_sequence, stopTime.stop_id);
            }
        }
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
