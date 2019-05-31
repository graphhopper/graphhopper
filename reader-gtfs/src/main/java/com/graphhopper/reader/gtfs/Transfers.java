/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.graphhopper.reader.gtfs;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Transfer;

import java.util.*;
import java.util.stream.Collectors;

class Transfers {

    private final Map<String, List<Transfer>> transfersFromStop;
    private final Map<String, List<Transfer>> transfersToStop;
    private final Map<String, Set<String>> routesByStop;

    Transfers(GTFSFeed feed) {
        this.transfersToStop = feed.transfers.values().stream().collect(Collectors.groupingBy(t -> t.to_stop_id));
        this.transfersFromStop = feed.transfers.values().stream().collect(Collectors.groupingBy(t -> t.from_stop_id));
        this.routesByStop = feed.stop_times.values().stream()
                .collect(Collectors.groupingBy(stopTime -> stopTime.stop_id,
                        Collectors.mapping(stopTime -> feed.trips.get(stopTime.trip_id).route_id, Collectors.toSet())));
    }

    // Starts implementing the proposed GTFS extension for route and trip specific transfer rules.
    // So far, only the route is supported.
    List<Transfer> getTransfersToStop(String toStopId, String toRouteId) {
        final List<Transfer> allInboundTransfers = transfersToStop.getOrDefault(toStopId, Collections.emptyList());
        final Map<String, List<Transfer>> byFromStop = allInboundTransfers.stream()
                .filter(t -> t.transfer_type == 0 || t.transfer_type == 2)
                .filter(t -> t.to_route_id == null || toRouteId.equals(t.to_route_id))
                .collect(Collectors.groupingBy(t -> t.from_stop_id));
        final List<Transfer> result = new ArrayList<>();
        byFromStop.forEach((fromStop, transfers) -> {
            routesByStop.getOrDefault(fromStop, Collections.emptySet()).forEach(fromRoute -> {
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

    List<Transfer> getTransfersFromStop(String fromStopId, String fromRouteId) {
        final List<Transfer> allOutboundTransfers = transfersFromStop.getOrDefault(fromStopId, Collections.emptyList());
        final Map<String, List<Transfer>> byToStop = allOutboundTransfers.stream()
                .filter(t -> t.transfer_type == 0 || t.transfer_type == 2)
                .filter(t -> t.from_route_id == null || fromRouteId.equals(t.from_route_id))
                .collect(Collectors.groupingBy(t -> t.to_stop_id));
        final List<Transfer> result = new ArrayList<>();
        byToStop.forEach((toStop, transfers) -> {
            routesByStop.getOrDefault(toStop, Collections.emptySet()).forEach(toRouteId -> {
                final Transfer mostSpecificRule = findMostSpecificRule(transfers, fromRouteId, toRouteId);
                final Transfer myRule = new Transfer();
                myRule.to_route_id = toRouteId;
                myRule.from_route_id = fromRouteId;
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
            if (Objects.equals(fromRouteId, t.from_route_id)) {
                score++;
            }
            if (Objects.equals(toRouteId, t.to_route_id)) {
                score++;
            }
            return -score;
        }));
        if (transfersBySpecificity.isEmpty()) {
            throw new RuntimeException();
        }
        return transfersBySpecificity.get(0);
    }

    public boolean hasNoRouteSpecificDepartureTransferRules(String stop_id) {
        return transfersToStop.getOrDefault(stop_id, Collections.emptyList()).stream().allMatch(transfer -> transfer.to_route_id == null);
    }

    public boolean hasNoRouteSpecificArrivalTransferRules(String stop_id) {
        return transfersFromStop.getOrDefault(stop_id, Collections.emptyList()).stream().allMatch(transfer -> transfer.from_route_id == null);
    }
}
