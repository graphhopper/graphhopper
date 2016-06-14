package com.graphhopper.matrix;


import java.util.Arrays;

/**
 * Holds the resulting distance matrix for a given set of
 * origin/destination nodes.
 *
 * The matrix consists of origin points which form the rows,
 * and destination points which form the columns.
 *
 * @author Pascal Büttiker
 */
public final class DistanceMatrix {

    private final int numberOfOrigins;
    private final int numberOfDestinations;


    private final double[][] distances;
    private final long[][] times;
    private final double[][] weights;

    /**
     * Creates a new GHMatrixResponse with the given dimensions
     *
     * @param numberOfOrigins The number of origin points (rows)
     * @param numberOfDestinations The number of destination points (columns)
     * @param includeDistances Include distance array
     * @param includeTimes Include times array
     * @param includeWeights Include weights array
     */
    public DistanceMatrix(int numberOfOrigins, int numberOfDestinations,
                          boolean includeDistances, boolean includeTimes, boolean includeWeights){

        this.numberOfOrigins = numberOfOrigins;
        this.numberOfDestinations = numberOfDestinations;

        distances = includeDistances ? new double[numberOfOrigins][numberOfDestinations] : null;
        times = includeTimes ? new long[numberOfOrigins][numberOfDestinations] : null;
        weights = includeWeights ? new double[numberOfOrigins][numberOfDestinations] : null;
    }

    /**
     * Gets the number of origins
     * (rows)
     */
    public int getNumberOfOrigins(){
        return numberOfOrigins;
    }

    /**
     * Gets the number of destinations
     * (columns)
     */
    public int getNumberOfDestinations(){
        return numberOfDestinations;
    }

    /**
     * Set the distance/time info of a single (origin --> destination) cell
     * @param originIndex The index of the origin
     * @param destIndex The index of the destination
     * @param distance Distance value
     * @param time Time value
     * @param weight weight value
     */
    public void setCell(int originIndex, int destIndex, double distance, long time, double weight){
        if(distances != null) distances[originIndex][destIndex] = distance;
        if(times != null) times[originIndex][destIndex] = time;
        if(weights != null) weights[originIndex][destIndex] = weight;
    }


    public double getDistance(int originIndex, int destIndex) {
        return distances != null ? distances[originIndex][destIndex] : 0;
    }

    public double getWeight(int originIndex, int destIndex) {
        return weights != null ? weights[originIndex][destIndex] : 0;
    }

    public long getTime(int originIndex, int destIndex) {
        return times != null ? times[originIndex][destIndex] : 0;
    }

    /**
     * Returns the matrix as formatted string
     */
    @Override
    public String toString(){
        String allMatrices = "";
        if(distances != null){
            allMatrices += Arrays.deepToString(distances) + "\n";
        }
        if(times != null){
            allMatrices += Arrays.deepToString(times) + "\n";
        }
        if(weights != null){
            allMatrices += Arrays.deepToString(weights) + "\n";
        }
        return allMatrices;
    }
}
