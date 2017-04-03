package com.graphhopper.reader.gtfs;

import com.conveyal.gtfs.model.Stop;
import com.conveyal.gtfs.model.Transfer;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Transfers {

    private final Map<String, List<Transfer>> transfers;

    Transfers(Collection<Transfer> transfers) {
        this.transfers = transfers.stream().collect(Collectors.groupingBy(t -> t.to_stop_id));
    }

    // Starts implementing the proposed GTFS extension for route and trip specific transfer rules.
    // So far, only the route is supported, and that only at the transfer destination.
    Stream<Transfer> getTransfersToStop(Stop to, String toRouteId) {
        final List<Transfer> allInboundTransfers = transfers.getOrDefault(to.stop_id, Collections.emptyList());
        final Map<String, List<Transfer>> byFromStop = allInboundTransfers.stream()
                .filter(t -> t.transfer_type == 2)
                .filter(t -> t.to_route_id == null || toRouteId.equals(t.to_route_id))
                .collect(Collectors.groupingBy(t -> t.from_stop_id));
        return byFromStop.values().stream().flatMap(findMostSpecificRule(toRouteId));
    }

    private Function<List<Transfer>, Stream<Transfer>> findMostSpecificRule(String routeId) {
        return transfers ->
                transfers.stream()
                        .sorted(Comparator.comparingInt(t -> {
                            int score = 0;
                            if (routeId.equals(t.to_route_id)) {
                                score++;
                            }
                            return -score;
                        }))
                        .limit(1);
    }
}
