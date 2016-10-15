package com.graphhopper.util;

import com.google.common.base.Functions;

import java.io.Serializable;
import java.util.*;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

/**
 * Hannah Bast and Sabine Storandt: Frequency-Based Search for Public Transit
 *
 * @author Michael Zilske
 */
public class HeuristicCAPCompressor {

    public static List<ArithmeticProgression> compress(List<Integer> s) {

        Map<Integer, List<Integer>> operatingDays = s.stream().collect(groupingBy(t -> t / (24 * 60 * 60),
                mapping(t -> t % (24 * 60 * 60), toList())));
        int maxOperatingDay = operatingDays.keySet().stream().mapToInt(i -> i).max().getAsInt();
        List<ArithmeticProgression> allAps = new ArrayList<>();
        for (Map.Entry<Integer, List<Integer>> e : operatingDays.entrySet()) {
            List<ArithmeticProgression> aps = compressSingleDay(e.getValue());
            aps.forEach(ap -> {
                ap.validOnDay = new BitSet(maxOperatingDay);
                ap.validOnDay.set(e.getKey());
            });
            allAps.addAll(aps);
        }
        return allAps;
    }

    private static List<ArithmeticProgression> compressSingleDay(List<Integer> s) {
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
        BitSet validOnDay;

        public double distanceToNextValue(double earliestStartTime) {
            int day = ((int) earliestStartTime) / (24 * 60 * 60);
            if (validOnDay.get(day)) {
                return evaluateAP(earliestStartTime % (24 * 60 * 60));
            } else {
                return Double.POSITIVE_INFINITY;
            }
        }

        private double evaluateAP(double earliestStartTime) {
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
