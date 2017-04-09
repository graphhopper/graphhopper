package com.graphhopper.reader.gtfs;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Stop;
import com.conveyal.gtfs.model.Transfer;

import java.util.*;
import java.util.stream.Collectors;

class Transfers {

    private final Map<String, List<Transfer>> transfers;
    private final GTFSFeed feed;

    Transfers(GTFSFeed feed) {
        this.feed = feed;
        this.transfers = feed.transfers.values().stream().collect(Collectors.groupingBy(t -> t.to_stop_id));
    }

    // Starts implementing the proposed GTFS extension for route and trip specific transfer rules.
    // So far, only the route is supported.
    List<Transfer> getTransfersToStop(Stop to, String toRouteId) {
        final List<Transfer> allInboundTransfers = transfers.getOrDefault(to.stop_id, Collections.emptyList());
        final Map<String, List<Transfer>> byFromStop = allInboundTransfers.stream()
                .filter(t -> t.transfer_type == 2)
                .filter(t -> t.to_route_id == null || toRouteId.equals(t.to_route_id))
                .collect(Collectors.groupingBy(t -> t.from_stop_id));
        final List<Transfer> result = new ArrayList<>();
        byFromStop.forEach((fromStop, transfers) -> {
            feed.getDistinctTripsForStop(fromStop).stream().map(trip -> trip.route_id).distinct().forEach(fromRoute -> {
                final Transfer mostSpecificRule = findMostSpecificRule(transfers, fromRoute, toRouteId);
                final Transfer myRule = new Transfer();
                myRule.to_route_id = toRouteId;
                myRule.from_route_id = fromRoute;
                myRule.to_stop_id = mostSpecificRule.to_stop_id;
                myRule.from_stop_id = mostSpecificRule.from_stop_id;
                myRule.transfer_type = mostSpecificRule.transfer_type;
                myRule.min_transfer_time = mostSpecificRule.min_transfer_time;
                myRule.from_trip_id = mostSpecificRule.from_trip_id;
                myRule.to_trip_id = mostSpecificRule.to_trip_id;
                result.add(myRule);
            });
        });
        return result;
    }

    private Transfer findMostSpecificRule(List<Transfer> transfers, String fromRouteId, String toRouteId) {
        final ArrayList<Transfer> transfersBySpecificity = new ArrayList<>(transfers);
        transfersBySpecificity.sort(Comparator.comparingInt(t -> {
            int score = 0;
            if (fromRouteId.equals(t.from_route_id)) {
                score++;
            }
            if (toRouteId.equals(t.to_route_id)) {
                score++;
            }
            return -score;
        }));
        if (transfersBySpecificity.isEmpty()) {
            throw new RuntimeException();
        }
        return transfersBySpecificity.get(0);
    }

}
