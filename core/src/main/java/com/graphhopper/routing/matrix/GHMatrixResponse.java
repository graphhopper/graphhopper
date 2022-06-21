package com.graphhopper.routing.matrix;

/**
 * Represents the result of a distance matrix query.
 *
 * @author Pascal Büttiker
 */
public class GHMatrixResponse {

    private DistanceMatrix matrix;

    private String debugInfo = "";


    public GHMatrixResponse() {
    }

    public void setMatrix(DistanceMatrix matrix) {
        this.matrix = matrix;
    }

    /**
     * Gets the internal distance matrix
     */
    public DistanceMatrix getMatrix() {
        return matrix;
    }
}
