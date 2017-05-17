package com.graphhopper.routing.weighting;

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.util.*;

public class BlockAreaWeighting implements Weighting {
    private final Weighting superWeighting;

    public BlockAreaWeighting(Weighting superWeighting) {
        this.superWeighting = superWeighting;
    }

    @Override
    public double getMinWeight(double distance) {
        return superWeighting.getMinWeight(distance);
    }

    @Override
    public double calcWeight(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        return superWeighting.calcWeight(edgeState, reverse, prevOrNextEdgeId);
    }

    @Override
    public long calcMillis(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        long millis = superWeighting.calcMillis(edgeState, reverse, prevOrNextEdgeId);
        return millis;
    }

    @Override
    public FlagEncoder getFlagEncoder() {
        return superWeighting.getFlagEncoder();
    }

    @Override
    public boolean matches(HintsMap weightingMap) {
        // TODO without the 'block_area' in comparison
        return superWeighting.matches(weightingMap);
    }

    /**
     * This method creates a map containing the specified
     */
    public static ConfigMap createConfigMap(String blockedRectangularAreas) {
        ConfigMap hintsMap = new ConfigMap(2);
        // add default blocked rectangular areas from config properties
        if (!Helper.isEmpty(blockedRectangularAreas)) {
            String val = blockedRectangularAreas;
            String blockedAreasFromRequest = hintsMap.get(Parameters.Routing.BLOCK_AREA, "");
            if (!blockedAreasFromRequest.isEmpty())
                val += ";" + blockedAreasFromRequest;
            hintsMap.put(Parameters.Routing.BLOCK_AREA, val);
        }
        return hintsMap;
    }

    @Override
    public String toString() {
        return "block_area|" + superWeighting.toString();
    }

    @Override
    public String getName() {
        return "block_area|" + superWeighting.getName();
    }
}
