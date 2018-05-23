package com.graphhopper.routing.weighting;

import com.graphhopper.routing.WeightFactors;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.util.EdgeIteratorState;

public class FactoredWeightings implements Weighting {
    private Weighting weighting;
    private final WeightFactors weightFactors;

    public FactoredWeightings(Weighting weighting, WeightFactors weightFactors) {
        this.weightFactors = weightFactors;
        this.weighting = weighting;
    }

    @Override
    public double getMinWeight(double distance) {
        return weighting.getMinWeight(distance);
    }

    @Override
    public double calcWeight(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        return weighting.calcWeight(edgeState, reverse, prevOrNextEdgeId) / weightFactors.getFactor(edgeState, reverse);
    }

    @Override
    public long calcMillis(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        return (long) (weighting.calcMillis(edgeState, reverse, prevOrNextEdgeId) / weightFactors.getFactor(edgeState, reverse));
    }

    @Override
    public FlagEncoder getFlagEncoder() {
        return weighting.getFlagEncoder();
    }

    @Override
    public String getName() {
        return weighting.getName();
    }

    @Override
    public boolean matches(HintsMap map) {
        return weighting.matches(map);
    }
}