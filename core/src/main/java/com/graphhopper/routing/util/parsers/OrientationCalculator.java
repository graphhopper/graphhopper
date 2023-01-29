package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.PointList;

import static com.graphhopper.util.AngleCalc.ANGLE_CALC;

public class OrientationCalculator implements TagParser {

    private final DecimalEncodedValue orientationEnc;

    public OrientationCalculator(DecimalEncodedValue orientationEnc) {
        this.orientationEnc = orientationEnc;
    }

    @Override
    public void handleWayTags(IntsRef edgeFlags, ReaderWay way, IntsRef relationFlags) {
        PointList pointList = way.getTag("point_list", null);
        if (pointList != null) {
            // store orientation in radians and use the end of the edge
            double orientation = ANGLE_CALC.calcOrientation(pointList.getLat(pointList.size() - 2), pointList.getLon(pointList.size() - 2),
                    pointList.getLat(pointList.size() - 1), pointList.getLon(pointList.size() - 1), true);
            orientationEnc.setDecimal(false, edgeFlags, orientation);

            // same for the opposite direction
            double revOrientation = ANGLE_CALC.calcOrientation(pointList.getLat(1), pointList.getLon(1),
                    pointList.getLat(0), pointList.getLon(0), true);
            orientationEnc.setDecimal(true, edgeFlags, revOrientation);
        }
    }
}

