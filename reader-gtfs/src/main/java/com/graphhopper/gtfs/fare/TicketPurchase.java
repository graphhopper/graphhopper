package com.graphhopper.gtfs.fare;

import com.conveyal.gtfs.model.Fare;
import org.optaplanner.core.api.domain.solution.PlanningEntityCollectionProperty;
import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.api.domain.solution.Solution;
import org.optaplanner.core.api.score.Score;

import java.util.*;

@PlanningSolution
public class TicketPurchase implements Solution {
    private Trip trip;
    private Score score;
    private Map<String, Fare> fares;
    private ArrayList<FareAssignment> fareAssignments = new ArrayList<>();

    TicketPurchase() {}

    TicketPurchase(Map<String, Fare> fares, Trip trip) {
        this.trip = trip;
        this.fares = fares;
        for (Trip.Segment segment : trip.segments) {
            fareAssignments.add(new FareAssignment(segment, Fares.calculate(fares, segment)));
        }
    }

    @Override
    public Score getScore() {
        return score;
    }

    @Override
    public void setScore(Score score) {
        this.score = score;
    }

    @Override
    public Collection<?> getProblemFacts() {
        return null;
    }

    @PlanningEntityCollectionProperty
    List<FareAssignment> getFareAssignments() {
        return fareAssignments;
    }


    List<Ticket> getTickets() {
        Map<String, TicketPurchaseScoreCalculator.TempTicket> currentTickets = new HashMap<>();
        for (FareAssignment fareAssignment : getFareAssignments()) {
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
