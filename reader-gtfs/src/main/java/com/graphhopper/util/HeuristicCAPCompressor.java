package com.graphhopper.util;

import java.io.Serializable;
import java.util.*;

/**
 * Hannah Bast and Sabine Storandt: Frequency-Based Search for Public Transit
 *
 * @author Michael Zilske
 */
public class HeuristicCAPCompressor {

    public static List<ArithmeticProgression> compress(List<Integer> s) {
        List<ArithmeticProgression> result = new ArrayList<>();
        BitSet done = new BitSet(s.size());
        for (int i=0; i < s.size(); i=done.nextClearBit(i+1)) {
            ArithmeticProgression bestAP = new ArithmeticProgression();
            bestAP.a = s.get(i);
            bestAP.b = s.get(i);
            bestAP.p = 1;
            bestAP.coverSize = 1;
            for (int j=i+1; done.previousClearBit(s.size()-1) - j + 1 > bestAP.coverSize && (s.get(done.previousClearBit(s.size()-1)) - s.get(i)) / (s.get(j) - s.get(i)) + 1 > bestAP.coverSize ; j++) {
                int p = s.get(j) - s.get(i);
                ArithmeticProgression ap = getCoverSize(s, i, j, p, done);
                if (ap.coverSize > bestAP.coverSize) {
                    bestAP = ap;
                }
            }
            for (int k=i; k < s.size() && s.get(k) <= bestAP.b; k++) {
                if ( (s.get(k) - s.get(i)) % bestAP.p == 0) {
                    done.set(k);
                }
            }
            result.add(bestAP);
        }
        return result;
    }

    public static List<Integer> decompress(List<ArithmeticProgression> a) {
        SortedSet<Integer> result = new TreeSet<>();
        for (ArithmeticProgression arithmeticProgression : a) {
            for (int i=arithmeticProgression.a; i<=arithmeticProgression.b; i+=arithmeticProgression.p) {
                result.add(i);
            }
        }
        return new ArrayList<>(result);
    }

    private static ArithmeticProgression getCoverSize(List<Integer> s, int i, int j, int p, BitSet done) {
        ArithmeticProgression ap = new ArithmeticProgression();
        ap.a = s.get(i);
        ap.b = s.get(i);
        ap.p = p;
        int f = 1;
        ap.coverSize = 1;
        for (int k = j; k < s.size(); k++) {
            if ( (s.get(k) - s.get(i)) % p == 0) {
                if ( (s.get(k) - s.get(i)) / p == f) {
                    f++;
                    if (!done.get(k)) {
                        ap.coverSize++;
                    }
                    ap.b = s.get(i) + p * (f - 1);
                } else {
                    return ap;
                }
            }
        }
        return ap;
    }

    public static class ArithmeticProgression implements Serializable {
        int a, b;
        int p;
        int coverSize;

        public double distanceToNextValue(double earliestStartTime) {
            if (earliestStartTime < a) {
                return (a - earliestStartTime);
            } else if (earliestStartTime <= b) {
                return (a + Math.ceil( (earliestStartTime - a) / p) * p - earliestStartTime);
            } else {
                return Double.POSITIVE_INFINITY;
            }
        }
    }
}
