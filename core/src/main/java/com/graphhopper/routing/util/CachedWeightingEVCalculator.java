package com.graphhopper.routing.util;

import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.*;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;

import java.util.*;

public class CachedWeightingEVCalculator {

    private static final int SIZE = 254 * 100;

    public static Result createMap(Graph graph, List<EncodedValue> list) {
        Map<Result.Key, Integer> frequencyCacheMap = new LinkedHashMap<>(SIZE + 1, .75F, true) {
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
            List<Object> objectList = createLists(iter, list);
            frequencyCacheMap.compute(new Result.Key(objectList), (k, v) -> v == null ? 1 : v + 1);
            // bwd
            objectList = createLists(iter.detach(true), list);
            frequencyCacheMap.compute(new Result.Key(objectList), (k, v) -> v == null ? 1 : v + 1);
        }

        List<Map.Entry<Result.Key, Integer>> sorted = new ArrayList<>(frequencyCacheMap.entrySet());
        sorted.sort((o1, o2) -> (o2.getValue()).compareTo(o1.getValue()));

        long count = 0;
        for (Map.Entry<Result.Key, Integer> entry : sorted) {
            if (print)
                System.out.println(entry.getValue() + " => " + entry.getKey().objects);
            count += entry.getValue();
        }
        if (print) {
            System.out.println("edges: " + edges);
            System.out.println("count: " + count);
            System.out.println("unique values: " + frequencyCacheMap.size());
        }

        frequencyCacheMap = null;

        Map<Result.Key, Integer> keyToIndexMap = new HashMap<>();

        // copy most frequently used 254 elements into resultList
        List<List<Object>> resultList = new ArrayList<>();
        int index = 1; // index == 0 is reserved for non-existing value
        for (Map.Entry<Result.Key, Integer> entry : sorted) {
            keyToIndexMap.put(entry.getKey(), index++);
            List<Object> tmpMap = new ArrayList<>(list.size());
            for (int i = 0; i < list.size(); i++) {
                tmpMap.add(entry.getKey().objects.get(i));
            }
            resultList.add(tmpMap);

            if(index > 254) break;
        }

        Map<String, Integer> varToIndex = new HashMap<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            varToIndex.put(list.get(i).getName(), i);
        }

        DataAccess dataAccess = new RAMDirectory().create("weights");
        dataAccess.ensureCapacity(iter.length() * 2L + 1);

        iter = graph.getAllEdges();
        while (iter.next()) {
            // fwd
            List<Object> objectList = createLists(iter, list);
            index = keyToIndexMap.get(new Result.Key(objectList));
            dataAccess.setByte(GHUtility.createEdgeKey(iter.getEdge(), false), (byte) index);

            // bwd
            objectList = createLists(iter.detach(true), list);
            index = keyToIndexMap.get(new Result.Key(objectList));
            dataAccess.setByte(GHUtility.createEdgeKey(iter.getEdge(), true), (byte) index);
        }

        Result result = new Result();
        result.edgeKeysCount = graph.getEdges() * 2;
        result.dataAccess = dataAccess;
        result.list = resultList;
        result.varToIndex = varToIndex;
        return result;
    }

    static List<Object> createLists(EdgeIteratorState edge, List<EncodedValue> list) {
        List<Object> objects = new ArrayList<>();
        for (EncodedValue encodedValue : list) {
            if (encodedValue instanceof EnumEncodedValue ev) {
                objects.add(edge.get(ev));
            } else if (encodedValue instanceof StringEncodedValue ev) {
                objects.add(edge.get(ev));
            } else if (encodedValue instanceof DecimalEncodedValue ev) {
                objects.add(edge.get(ev));
            } else if (encodedValue instanceof BooleanEncodedValue ev) {
                objects.add(edge.get(ev));
            } else if (encodedValue instanceof IntEncodedValue ev) {
                objects.add(edge.get(ev));
            } else {
                throw new IllegalArgumentException("unknown EncodedValue " + encodedValue.getName());
            }
        }
        return objects;
    }

    public static class Result {
        private List<List<Object>> list;
        private DataAccess dataAccess;
        private Map<String, Integer> varToIndex;
        private int edgeKeysCount;

        public CacheFunction createCacheFunction() {
            return new CacheFunction() {
                @Override
                public int getIndex(String var) {
                    return varToIndex.get(var);
                }

                @Override
                public List<Object> calc(int edgeKey) {
                    if(edgeKey >= edgeKeysCount) return null; // TODO NOW proper handling of query graph?
                    int idx = dataAccess.getByte(edgeKey) & 0xFF;
                    if (idx == 0) return null;
                    return list.get(idx - 1);
                }
            };
        }

        record Key(List<Object> objects) {
            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                return Objects.equals(objects, ((Key) o).objects);
            }
        }
    }
}
