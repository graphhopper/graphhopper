package com.graphhopper.util.details;

import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.weighting.custom.CustomWeightingHelper;
import com.graphhopper.util.EdgeIteratorState;

import static com.graphhopper.util.Parameters.Details.CHANGE_ANGLE;

/**
 * This class handles the calculation for the change_angle path detail, i.e. the angle between the
 * edges calculated from the 'orientation' of an edge.
 */
public class ChangeAngleDetails extends AbstractPathDetailsBuilder {

    private final DecimalEncodedValue orientationEv;
    private Double prevAzimuth;
    private Double changeAngle;

    public ChangeAngleDetails(DecimalEncodedValue orientationEv) {
        super(CHANGE_ANGLE);
        this.orientationEv = orientationEv;
    }

    @Override
    protected Object getCurrentValue() {
        return changeAngle;
    }

    @Override
    public boolean isEdgeDifferentToLastEdge(EdgeIteratorState edge) {
        if (prevAzimuth != null) {
            double azimuth = edge.getReverse(orientationEv);
            double tmp = CustomWeightingHelper.calcChangeAngle(prevAzimuth, azimuth);
            double tmpRound = Math.round(tmp);

            if (changeAngle == null || Math.abs(tmpRound - changeAngle) > 0) {
                prevAzimuth = edge.get(orientationEv);
                changeAngle = tmpRound;
                return true;
            }
        }

        prevAzimuth = edge.get(orientationEv);
        return changeAngle == null;
    }
}
