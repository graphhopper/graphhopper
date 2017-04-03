package com.graphhopper.gtfs.fare;

import java.util.*;

class TicketPurchase {
    private List<FareAssignment> fareAssignments = new ArrayList<>();

    TicketPurchase(List<FareAssignment> fareAssignments) {
        this.fareAssignments = fareAssignments;
    }

    List<Ticket> getTickets() {
        Map<String, TicketPurchaseScoreCalculator.TempTicket> currentTickets = new HashMap<>();
        for (FareAssignment fareAssignment : fareAssignments) {
            if (fareAssignment.fare != null) {
                currentTickets.computeIfAbsent(fareAssignment.fare.fare_id, fareId -> new TicketPurchaseScoreCalculator.TempTicket());
                currentTickets.compute(fareAssignment.fare.fare_id, (s, tempTicket) -> {
                    if (fareAssignment.segment.getStartTime() > tempTicket.validUntil
                            || tempTicket.nMoreTransfers == 0) {
                        tempTicket.fare = fareAssignment.fare;
                        tempTicket.validUntil = fareAssignment.segment.getStartTime() + fareAssignment.fare.fare_attribute.transfer_duration;
                        tempTicket.nMoreTransfers = fareAssignment.fare.fare_attribute.transfers;
                        tempTicket.totalNumber++;
                        return tempTicket;
                    } else {
                        tempTicket.nMoreTransfers--;
                        return tempTicket;
                    }
                });
            }
        }
        ArrayList<Ticket> tickets = new ArrayList<>();
        for (TicketPurchaseScoreCalculator.TempTicket t : currentTickets.values()) {
            for (int i = 0; i<t.totalNumber; i++) {
                tickets.add(new Ticket(t.fare));
            }
        }
        return tickets;
    }

    int getNSchwarzfahrTrips() {
        return (int) fareAssignments.stream().filter(assignment -> assignment.fare == null).count();
    }
}
