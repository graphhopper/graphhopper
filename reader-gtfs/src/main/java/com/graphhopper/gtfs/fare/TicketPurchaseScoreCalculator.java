package com.graphhopper.gtfs.fare;

import com.conveyal.gtfs.model.Fare;
import org.optaplanner.core.api.score.Score;
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.optaplanner.core.impl.score.director.easy.EasyScoreCalculator;

public class TicketPurchaseScoreCalculator implements EasyScoreCalculator<TicketPurchase> {

    static class TempTicket {
        Fare fare;
        int totalNumber = 0;
        long validUntil;
        int nMoreTransfers = 0;
    }

    @Override
    public Score calculateScore(TicketPurchase ticketPurchase) {
        int cost = 0;
        for (Ticket ticket : ticketPurchase.getTickets()) {
            cost -= (int) ticket.getFare().fare_attribute.price;
        }
        return HardSoftScore.valueOf(ticketPurchase.getNSchwarzfahrTrips(), cost);
    }
}
