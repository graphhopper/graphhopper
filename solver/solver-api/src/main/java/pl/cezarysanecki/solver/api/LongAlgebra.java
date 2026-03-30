package pl.cezarysanecki.solver.api;

/**
 * Weight algebra for Long — integer weights (e.g. milliseconds).
 */
public enum LongAlgebra implements WeightAlgebra<Long> {
    INSTANCE;

    @Override
    public Long zero() {
        return 0L;
    }

    @Override
    public Long infinity() {
        return Long.MAX_VALUE;
    }

    @Override
    public Long add(Long a, Long b) {
        // overflow protection
        if (a == Long.MAX_VALUE || b == Long.MAX_VALUE) return Long.MAX_VALUE;
        return a + b;
    }

    @Override
    public int compare(Long a, Long b) {
        return Long.compare(a, b);
    }
}
