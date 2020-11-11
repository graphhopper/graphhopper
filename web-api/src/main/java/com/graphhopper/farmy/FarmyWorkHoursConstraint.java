package com.graphhopper.farmy;

import com.graphhopper.jsprit.core.problem.constraint.HardActivityConstraint;
import com.graphhopper.jsprit.core.problem.misc.JobInsertionContext;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity;

public class FarmyWorkHoursConstraint implements HardActivityConstraint {

    private Double totalRouteTime = 0.0;

    @Override
    public ConstraintsStatus fulfilled(JobInsertionContext iFacts, TourActivity prevAct, TourActivity newAct, TourActivity nextAct, double prevActDepTime) {

        totalRouteTime += newAct.getArrTime();

        if (totalRouteTime > 480) {
            return ConstraintsStatus.NOT_FULFILLED;
        }

        return ConstraintsStatus.FULFILLED;
    }
}
