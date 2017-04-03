package com.graphhopper.gtfs.fare;

import com.conveyal.gtfs.model.Fare;

class TicketPurchaseScoreCalculator {

    static class TempTicket {
        Fare fare;
        int totalNumber = 0;
        long validUntil;
        int nMoreTransfers = 0;
    }

    int calculateScore(TicketPurchase ticketPurchase) {
        int cost = 0;
        for (Ticket ticket : ticketPurchase.getTickets()) {
            cost -= (int) ticket.getFare().fare_attribute.price;
        }
        return cost - ticketPurchase.getNSchwarzfahrTrips() * 60;
    }
}
