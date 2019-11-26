package com.graphhopper.util.details;

import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;

import static com.graphhopper.util.Parameters.Details.AVERAGE_SPEED;

public class AverageSpeedDetails extends AbstractPathDetailsBuilder {

    private final Weighting weighting;
    private final double precision;
    private double decimalValue = -1;
    // will include the turn time penalty
    private int prevEdgeId = -1;

    public AverageSpeedDetails(Weighting weighting) {
        this(weighting, 0.1);
    }

    /**
     * @param precision e.g. 0.1 to avoid creating too many path details, i.e. round the speed to the specified precision
     *                  before detecting a change.
     */
    public AverageSpeedDetails(Weighting weighting, double precision) {
        super(AVERAGE_SPEED);
        this.weighting = weighting;
        this.precision = precision;
    }

    @Override
    protected Object getCurrentValue() {
        return decimalValue;
    }

    @Override
    public boolean isEdgeDifferentToLastEdge(EdgeIteratorState edge) {
        double tmpVal = edge.getDistance() / weighting.calcMillis(edge, false, prevEdgeId) * 3600;
        if (Double.isInfinite(tmpVal))
            throw new IllegalStateException("average_speed was infinite for " + edge.fetchWayGeometry(3));

        prevEdgeId = edge.getEdge();
        if (Math.abs(tmpVal - decimalValue) >= precision) {
            this.decimalValue =  Math.round(tmpVal / precision) * precision;
            return true;
        }
        return false;
    }
}
