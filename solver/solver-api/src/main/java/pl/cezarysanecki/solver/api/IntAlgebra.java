package pl.cezarysanecki.solver.api;

/**
 * Weight algebra for Integer — lightweight integer weights.
 */
public enum IntAlgebra implements WeightAlgebra<Integer> {
    INSTANCE;

    @Override
    public Integer zero() {
        return 0;
    }

    @Override
    public Integer infinity() {
        return Integer.MAX_VALUE;
    }

    @Override
    public Integer add(Integer a, Integer b) {
        if (a == Integer.MAX_VALUE || b == Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return a + b;
    }

    @Override
    public int compare(Integer a, Integer b) {
        return Integer.compare(a, b);
    }
}
