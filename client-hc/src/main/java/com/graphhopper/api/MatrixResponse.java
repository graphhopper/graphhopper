package com.graphhopper.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * This class defines the response for a M-to-N requests.
 *
 * @author Peter Karich
 */
public class MatrixResponse {

    private String debugInfo = "";
    private final List<Throwable> errors = new ArrayList<>(4);
    private long[][] times = new long[0][];
    private int[][] distances = new int[0][];
    private double[][] weights = new double[0][];
    private final int fromCount;
    private final int toCount;

    public MatrixResponse() {
        this(10, 10, true, true, true);
    }

    public MatrixResponse(int fromCap, int toCap, boolean withTimes, boolean withDistances, boolean withWeights) {
        if (fromCap <= 0 || toCap <= 0)
            throw new IllegalArgumentException("Requested matrix too small: " + fromCap + "x" + toCap);
        this.fromCount = fromCap;
        this.toCount = toCap;

        if (withTimes) {
            times = new long[fromCap][toCap];
        }

        if (withDistances) {
            distances = new int[fromCap][toCap];
        }

        if (withWeights) {
            weights = new double[fromCap][toCap];
        }

        if (!withTimes && !withDistances && !withWeights)
            throw new IllegalArgumentException("Please specify times, distances or weights that should be calculated by the matrix");
    }

    public void setFromRow(int row, long timeRow[], int distanceRow[], double weightRow[]) {
        if (times.length > 0) {
            check(timeRow.length, toCount, "to times");
            times[row] = timeRow;
        }

        if (distances.length > 0) {
            check(distanceRow.length, toCount, "to distances");
            distances[row] = distanceRow;
        }

        if (weights.length > 0) {
            check(weights.length, toCount, "to weights");
            weights[row] = weightRow;
        }
    }

    private void check(int currentLength, int expectedLength, String times) {
        if (currentLength != expectedLength)
            throw new IllegalArgumentException("Sizes do not match for '" + times + "'. " +
                    "Expected " + expectedLength + " was: " + currentLength + ". Matrix: " + fromCount + "x" + toCount);
    }

    public void setTimeRow(int row, long timeRow[]) {
        if (times.length > 0) {
            check(timeRow.length, toCount, "to times");
            times[row] = timeRow;
        } else {
            throw new UnsupportedOperationException("Cannot call setTimeRow if times are disabled");
        }
    }

    public void setDistanceRow(int row, int distanceRow[]) {
        if (distances.length > 0) {
            check(distanceRow.length, toCount, "to distances");
            distances[row] = distanceRow;
        } else {
            throw new UnsupportedOperationException("Cannot call setDistanceRow if distances are disabled");
        }
    }

    public void setWeightRow(int row, double weightRow[]) {
        if (weights.length > 0) {
            check(weightRow.length, toCount, "to weights");
            weights[row] = weightRow;
        } else {
            throw new UnsupportedOperationException("Cannot call setWeightRow if weights are disabled");
        }
    }

    /**
     * Returns the time for the specific entry (from -&gt; to) in milliseconds.
     */
    public long getTime(int from, int to) {
        if (hasErrors()) {
            throw new IllegalStateException("Cannot return time (" + from + "," + to + ") if errors occured " + getErrors());
        }

        if (from >= times.length) {
            throw new IllegalStateException("Cannot get 'from' " + from + " from times with size " + times.length);
        } else if (to >= times[from].length) {
            throw new IllegalStateException("Cannot get 'to' " + to + " from times with size " + times[from].length);
        }
        return times[from][to];
    }

    /**
     * Returns the distance for the specific entry (from -&gt; to) in meter.
     */
    public double getDistance(int from, int to) {
        if (hasErrors()) {
            throw new IllegalStateException("Cannot return distance (" + from + "," + to + ") if errors occured " + getErrors());
        }

        if (from >= distances.length) {
            throw new IllegalStateException("Cannot get 'from' " + from + " from distances with size " + distances.length);
        } else if (to >= distances[from].length) {
            throw new IllegalStateException("Cannot get 'to' " + to + " from distances with size " + distances[from].length);
        }
        return distances[from][to];
    }

    /**
     * Returns the weight for the specific entry (from -&gt; to) in arbitrary units
     * ('costs').
     */
    public double getWeight(int from, int to) {
        if (hasErrors()) {
            throw new IllegalStateException("Cannot return weight (" + from + "," + to + ") if errors occured " + getErrors());
        }

        if (from >= weights.length) {
            throw new IllegalStateException("Cannot get 'from' " + from + " from weights with size " + weights.length);
        } else if (to >= weights[from].length) {
            throw new IllegalStateException("Cannot get 'to' " + to + " from weights with size " + weights[from].length);
        }
        return weights[from][to];
    }

    public String getDebugInfo() {
        return debugInfo;
    }

    public MatrixResponse setDebugInfo(String debugInfo) {
        if (debugInfo != null) {
            this.debugInfo = debugInfo;
        }
        return this;
    }

    /**
     * @return true if one or more error found
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public List<Throwable> getErrors() {
        return errors;
    }

    public MatrixResponse addError(Throwable error) {
        errors.add(error);
        return this;
    }

    public MatrixResponse addErrors(Collection<Throwable> errorList) {
        errors.addAll(errorList);
        return this;
    }

    @Override
    public String toString() {
        String addInfo = "";

        if (times.length > 0) {
            addInfo += ", times: " + times.length + "x" + times[0].length;
        }

        if (distances.length > 0) {
            addInfo += ", distances: " + distances.length + "x" + distances[0].length;
        }

        return "[" + addInfo + "] errors:" + errors.toString();
    }
}
