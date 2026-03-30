package pl.cezarysanecki.solver.api;

import java.util.Comparator;

/**
 * Defines algebraic operations on weights.
 * <p>
 * Mathematically: an ordered monoid with an absorbing element (infinity).
 * <p>
 * Requirements:
 * <ul>
 *   <li>zero() is the identity element of add: add(zero(), w) == w</li>
 *   <li>infinity() is the absorbing element: add(infinity(), w) == infinity()</li>
 *   <li>add is associative: add(add(a, b), c) == add(a, add(b, c))</li>
 *   <li>the ordering is total: for any a, b: a &lt;= b or b &lt;= a</li>
 *   <li>weights are non-negative: compare(w, zero()) &gt;= 0 for every w</li>
 * </ul>
 *
 * @param <W> weight type
 */
public interface WeightAlgebra<W> extends Comparator<W> {

    /** Identity element of addition — zero cost. */
    W zero();

    /** Absorbing element — unreachability. */
    W infinity();

    /** Sum of two weights (associative, commutative). */
    W add(W a, W b);

    /** Does the given weight represent infinity (unreachability)? */
    default boolean isInfinite(W weight) {
        return compare(weight, infinity()) >= 0;
    }

    /** Is a &lt; b? Convenience method. */
    default boolean isLessThan(W a, W b) {
        return compare(a, b) < 0;
    }

    /** Is a &lt;= b? Convenience method. */
    default boolean isLessOrEqual(W a, W b) {
        return compare(a, b) <= 0;
    }
}
