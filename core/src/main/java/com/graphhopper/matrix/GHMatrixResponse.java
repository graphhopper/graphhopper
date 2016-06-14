package com.graphhopper.matrix;

/**
 * Represents the result of a distance matrix query.
 *
 * @author Pascal Büttiker
 */
public class GHMatrixResponse {

    private final DistanceMatrix matrix;


    public GHMatrixResponse(DistanceMatrix matrix){
        this.matrix = matrix;
    }

    /**
     * Gets the internal distance matrix
     */
    public DistanceMatrix getMatrix() {
        return matrix;
    }
}
