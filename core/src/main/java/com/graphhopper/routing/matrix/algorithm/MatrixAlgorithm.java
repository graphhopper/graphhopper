package com.graphhopper.routing.matrix.algorithm;

import com.graphhopper.routing.matrix.DistanceMatrix;
import com.graphhopper.storage.index.Snap;

import java.util.List;

/**
 * Represents a distance matrix algorithm.
 *
 * @author Pascal Büttiker
 */
public interface MatrixAlgorithm {

    /**
     * Calculate all distances and durations from all given origins to all given destinations.
     *
     * @param origins      One or more origin nodes
     * @param destinations One or more destination nodes
     * @return Returns a row for each origin, with duration/distance info to each destination
     * @throws IllegalArgumentException Thrown when origin.size() < 1 or desitnations.size() < 1
     */
    DistanceMatrix calcMatrix(List<Snap> origins, List<Snap> destinations);

    /**
     * Limit the search to numberOfNodes. See #681
     */
    void setMaxVisitedNodes(int numberOfNodes);

    /**
     * @return name of this algorithm
     */
    String getName();

    /**
     * Returns the visited nodes after searching. Useful for debugging.
     */
    int getVisitedNodes();
}
