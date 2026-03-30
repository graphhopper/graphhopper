package pl.cezarysanecki.solver.api;

/**
 * Weight algebra for Double — the most common case.
 */
public enum DoubleAlgebra implements WeightAlgebra<Double> {
    INSTANCE;

    @Override
    public Double zero() {
        return 0.0;
    }

    @Override
    public Double infinity() {
        return Double.POSITIVE_INFINITY;
    }

    @Override
    public Double add(Double a, Double b) {
        return a + b;
    }

    @Override
    public int compare(Double a, Double b) {
        return Double.compare(a, b);
    }
}
