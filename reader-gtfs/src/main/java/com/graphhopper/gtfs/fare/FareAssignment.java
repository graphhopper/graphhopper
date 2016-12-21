package com.graphhopper.gtfs.fare;

import com.conveyal.gtfs.model.Fare;
import org.optaplanner.core.api.domain.entity.PlanningEntity;
import org.optaplanner.core.api.domain.valuerange.ValueRangeProvider;
import org.optaplanner.core.api.domain.variable.PlanningVariable;

import java.util.Collection;

@PlanningEntity
public class FareAssignment {

    Trip.Segment segment;
    private Collection<Fare> possibleFares;

    public FareAssignment() {

    }

    @PlanningVariable(valueRangeProviderRefs = "possibleFares")
    public Fare getFare() {
        return fare;
    }

    public void setFare(Fare fare) {
        this.fare = fare;
    }

    Fare fare;

    @ValueRangeProvider(id = "possibleFares")
    public Collection<Fare> getPossibleFares() {
        return possibleFares;
    }

    public FareAssignment(Trip.Segment segment, Collection<Fare> possibleFares) {
        this.segment = segment;
        this.possibleFares = possibleFares;
    }
}
