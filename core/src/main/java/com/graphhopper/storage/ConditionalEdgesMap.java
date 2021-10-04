package com.graphhopper.storage;

import com.graphhopper.search.ConditionalIndex;
import com.graphhopper.util.EdgeIteratorState;

import java.util.*;

/**
 * @author Andrzej Oles
 */
public class ConditionalEdgesMap implements Storable<ConditionalEdgesMap> {
    private static final int EF_EDGE_BYTES = 4;
    private static final int EF_CONDITION_BYTES = 4;
    protected final int EF_EDGE, EF_CONDITION;

    protected DataAccess edges;
    protected int edgeEntryIndex = 0;
    protected int edgeEntryBytes;
    protected int edgesCount;

    private static final long START_POINTER = 0;
    private long bytePointer = START_POINTER;

    Map<Integer, Integer> values = new HashMap<>();

    String name;
    ConditionalIndex conditionalIndex;


    public ConditionalEdgesMap(String name, ConditionalIndex conditionalIndex, DataAccess edges) {
        this.name = name;
        this.conditionalIndex = conditionalIndex;

        EF_EDGE = nextBlockEntryIndex(EF_EDGE_BYTES);
        EF_CONDITION = nextBlockEntryIndex(EF_CONDITION_BYTES);

        edgesCount = 0;
        this.edges = edges;
    }

    protected final int nextBlockEntryIndex(int size) {
        int res = edgeEntryIndex;
        edgeEntryIndex += size;
        return res;
    }

    public int entries() {
        return edgesCount;
    }


    /**
     * Set the pointer to the conditional index.
     * @param createdEdges    The internal id of the edge in the graph
     * @param value  The index pointing to the conditionals
     */
    public void addEdges(List<EdgeIteratorState> createdEdges, String value) {
        int conditionalRef = (int) conditionalIndex.put(value);
        if (conditionalRef < 0)
            throw new IllegalStateException("Too many conditionals are stored, currently limited to int pointer");

        for (EdgeIteratorState edgeIteratorState : createdEdges) {
            int edge = edgeIteratorState.getEdge();

            edgesCount++;

            edges.ensureCapacity(bytePointer + EF_EDGE_BYTES + EF_CONDITION_BYTES);

            edges.setInt(bytePointer, edge);
            bytePointer += EF_EDGE_BYTES;
            edges.setInt(bytePointer, conditionalRef);
            bytePointer += EF_CONDITION_BYTES;

            values.put(edge, conditionalRef);
        }

    }

    /**
     * Get the pointer to the conditional index.
     * @param edgeId    The internal graph id of the edger
     * @return The index pointing to the conditionals
     */
    public String getValue(int edgeId) {
        Integer index = values.get(edgeId);

        return (index == null) ? "" : conditionalIndex.get((long) index);
    }

    @Deprecated // TODO ORS (minor): remove after upgrade
    public void init(Graph graph, Directory dir) {
        if (edgesCount > 0)
            throw new AssertionError("The conditional restrictions storage must be initialized only once.");

        this.edges = dir.find(name);
    }

    @Override
    public ConditionalEdgesMap create(long byteCount) {
        if (edgesCount > 0)
            throw new AssertionError("The conditional restrictions storage must be initialized only once.");
        edges.create(byteCount * edgeEntryBytes);
        return this;
    }

    @Override
    public boolean loadExisting() {
        if (!edges.loadExisting())
            throw new IllegalStateException("Unable to load storage '" + name + "'. Corrupt file or directory?" );

        edgeEntryBytes = edges.getHeader(0);
        edgesCount = edges.getHeader(4);

        for (bytePointer = START_POINTER; bytePointer < edgesCount * (EF_EDGE_BYTES + EF_CONDITION_BYTES);) {
            int edge = edges.getInt(bytePointer);
            bytePointer += EF_EDGE_BYTES;
            int condition = edges.getInt(bytePointer);
            bytePointer += EF_CONDITION_BYTES;

            values.put(edge, condition);
        }

        return true;
    }

    @Override
    public void flush() {
        edges.setHeader(0, edgeEntryBytes);
        edges.setHeader(1 * 4, edgesCount);
        edges.flush();
    }

    @Override
    public void close() {
        edges.close();
    }

    @Override
    public long getCapacity() {
        return edges.getCapacity();
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean isClosed() {
        return false;
    }

    public void printStoredValues() {
        Set<Integer> uniqueValues = new HashSet<>(values.values());

        Iterator<Integer> value = uniqueValues.iterator();

        while (value.hasNext()) {
            System.out.println(conditionalIndex.get((long) value.next()));
        }
    }

};


