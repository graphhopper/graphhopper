package com.graphhopper.routing.weighting;

import com.graphhopper.routing.util.EdgeData;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.util.EdgeIteratorState;

import java.util.Map;

import static com.graphhopper.routing.util.EdgeData.getEdgeId;

public class WeightingsWithFactors implements Weighting {
    private final Weighting weighting;
    private final Map<EdgeData, Double> edgesWeightFactors;

    public WeightingsWithFactors(Weighting weighting, Map<EdgeData, Double> edgesWeightFactors) {
        this.edgesWeightFactors = edgesWeightFactors;
        this.weighting = weighting;
    }

    @Override
    public double getMinWeight(double distance) {
        return weighting.getMinWeight(distance);
    }

    @Override
    public double calcWeight(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        return weighting.calcWeight(edgeState, reverse, prevOrNextEdgeId) * getFactor(edgeState, reverse);
    }

    private double getFactor(EdgeIteratorState edgeState, boolean reverse) {
        final EdgeData edgeData = reverse ? new EdgeData(getEdgeId(edgeState), edgeState.getAdjNode(), edgeState.getBaseNode()) : new EdgeData(getEdgeId(edgeState), edgeState.getBaseNode(), edgeState.getAdjNode());
        return edgesWeightFactors.containsKey(edgeData) ? edgesWeightFactors.get(edgeData) : 1.0;
    }

    @Override
    public long calcMillis(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        return (long) (weighting.calcMillis(edgeState, reverse, prevOrNextEdgeId) * getFactor(edgeState, reverse));
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
