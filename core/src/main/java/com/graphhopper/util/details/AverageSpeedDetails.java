package com.graphhopper.util.details;

import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;

import static com.graphhopper.util.Parameters.Details.AVERAGE_SPEED;

public class AverageSpeedDetails extends AbstractPathDetailsBuilder {

    private final Weighting weighting;
    private double decimalValue = -1;
    // will include the turn time penalty
    private int prevEdgeId = -1;

    public AverageSpeedDetails(Weighting weighting) {
        super(AVERAGE_SPEED);
        this.weighting = weighting;
    }

    @Override
    protected Object getCurrentValue() {
        if (Double.isInfinite(decimalValue))
            throw new IllegalStateException("average_speed must not be infinite");

        return decimalValue;
    }

    @Override
    public boolean isEdgeDifferentToLastEdge(EdgeIteratorState edge) {
        double tmpVal = edge.getDistance() / weighting.calcMillis(edge, false, prevEdgeId) * 3600;
        prevEdgeId = edge.getEdge();
        // avoid creating too many path details => round the speed to 0.01 precision and include it only if:
        if (Math.abs(tmpVal - decimalValue) >= 0.1) {
            this.decimalValue = Helper.round2(tmpVal);
            return true;
        }
        return false;
    }
}
