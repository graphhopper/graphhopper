package com.graphhopper.routing.util;

import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.GHUtility;

import java.util.*;


public class CacheWeightCalculator {

    private static final int SIZE = 254 * 100;

    public static Result createMap(Graph graph, Weighting weighting) {
        Map<Double, Integer> frequencyCacheMap = new LinkedHashMap<>(SIZE + 1, .75F, true) {
            public boolean removeEldestEntry(Map.Entry entry) {
                return size() > SIZE;
            }
        };
        boolean print = false;
        AllEdgesIterator iter = graph.getAllEdges();
        long edges = 0;
        while (iter.next()) {
            edges++;
            // fwd
            double weight = weighting.calcEdgeWeight(iter, false);
            frequencyCacheMap.compute(weight, (k, v) -> v == null ? 1 : v + 1);
            // bwd
            weight = weighting.calcEdgeWeight(iter, true);
            frequencyCacheMap.compute(weight, (k, v) -> v == null ? 1 : v + 1);
        }

        List<Map.Entry<Double, Integer>> sorted = new ArrayList<>(frequencyCacheMap.entrySet());
        sorted.sort((o1, o2) -> (o2.getValue()).compareTo(o1.getValue()));

        long count = 0;
        for (Map.Entry<Double, Integer> entry : sorted) {
            if (print)
                System.out.println(entry.getValue() + " => " + entry.getKey());
            count += entry.getValue();
        }
        if (print) {
            System.out.println("edges: " + edges);
            System.out.println("count: " + count);
            System.out.println("unique values: " + frequencyCacheMap.size());
        }

        frequencyCacheMap = null;

        Map<Double, Integer> keyToIndexMap = new HashMap<>();

        // copy most frequently used 254 elements into resultList
        List<Double> resultList = new ArrayList<>();
        int index = 1; // index == 0 is reserved for non-existing value
        for (Map.Entry<Double, Integer> entry : sorted) {
            keyToIndexMap.put(entry.getKey(), index++);
            resultList.add(entry.getKey());

            if (index > 254) break;
        }

        DataAccess dataAccess = new RAMDirectory().create("weights");
        dataAccess.ensureCapacity(iter.length() * 2L + 1);

        iter = graph.getAllEdges();
        Integer idx;
        while (iter.next()) {
            // fwd
            double weight = weighting.calcEdgeWeight(iter, false);
            idx = keyToIndexMap.get(weight);
            if (idx != null)
                dataAccess.setByte(GHUtility.createEdgeKey(iter.getEdge(), false), (byte) (int) idx);

            // bwd
            weight = weighting.calcEdgeWeight(iter, true);
            idx = keyToIndexMap.get(weight);
            if (idx != null)
                dataAccess.setByte(GHUtility.createEdgeKey(iter.getEdge(), true), (byte) (int) idx);
        }

        Result result = new Result();
        result.edgeKeysCount = graph.getEdges() * 2;
        result.dataAccess = dataAccess;
        result.list = resultList;
        return result;
    }

    public static class Result {
        private List<Double> list;
        private DataAccess dataAccess;
        private int edgeKeysCount;

        public CacheFunction createCacheFunction() {
            return edgeKey -> {
                if (edgeKey >= edgeKeysCount)
                    return null; // TODO NOW proper handling of query graph?
                int idx = dataAccess.getByte(edgeKey) & 0xFF;
                if (idx == 0) return null;
                return list.get(idx - 1);
            };
        }
    }
}
