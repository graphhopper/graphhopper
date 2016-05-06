package com.graphhopper.matrix;

import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.util.shapes.GHPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a distance matrix request.
 *
 * It will calculate every distance & duration from all origin points to all given destination points.
 *
 * @author Pascal Büttiker
 */
public class GHMatrixRequest { // TODO Probably refactor & subclass a base GHRequest

    private String algorithm;
    private String vehicle = "";
    private final HintsMap hints = new HintsMap();

    private final List<GHPoint> origins = new ArrayList<>();
    private final List<GHPoint> destinations = new ArrayList<>();

    /**
     * One or more locations to use as the starting point for calculating travel distance and time.
     */
    public List<GHPoint> getOrigins(){
        return origins;
    }

    /**
     * One or more locations to use as the finishing point for calculating travel distance and time.
     */
    public List<GHPoint> getDestinations(){
        return destinations;
    }

    /**
     * The matrix algorithm to use. If not set, a default is used.
     * @see com.graphhopper.matrix.algorithm.MatrixAlgorithm
     */
    public String getAlgorithm() {
        return algorithm;
    }

    /**
     * The matrix algorithm to use. If not set, a default is used.
     * @see com.graphhopper.matrix.algorithm.MatrixAlgorithm
     */
    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getVehicle() {
        return vehicle;
    }

    public void setVehicle(String vehicle) {
        this.vehicle = vehicle;
    }

    public HintsMap getHints()
    {
        return hints;
    }
}
