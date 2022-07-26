package com.graphhopper.util.details;

import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;

import static com.graphhopper.util.Parameters.Details.AVERAGE_SPEED;

public class AverageSpeedDetails extends AbstractPathDetailsBuilder {

    private final Weighting weighting;
    private final double precision;
    private Double decimalValue;
    // will include the turn time penalty
    private int prevEdgeId = EdgeIterator.NO_EDGE;

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
        // for very short edges we might not be able to calculate a proper value for speed. dividing by calcMillis can
        // even lead to speed=Infinity -> just ignore these cases here, see #1848 and #2620
        final double distance = edge.getDistance();
        if (distance < 0.01) {
            if (decimalValue != null) return false;
            // in case this is the first edge we return decimalValue=null
            return true;
        }

        double tmpVal = distance / GHUtility.calcMillisWithTurnMillis(weighting, edge, false, prevEdgeId) * 3600;
        prevEdgeId = edge.getEdge();
        if (decimalValue == null || Math.abs(tmpVal - decimalValue) >= precision) {
            this.decimalValue = Math.round(tmpVal / precision) * precision;
            return true;
        }
        return false;
    }
}
