package com.graphhopper.storage;

import com.graphhopper.routing.util.*;
import com.graphhopper.search.ConditionalIndex;

import java.util.*;

/**
 * @author Andrzej Oles
 */
public class ConditionalEdges implements Storable<ConditionalEdges> {
    Map<Integer, Integer> values = new HashMap<>();
    private final Map<String, ConditionalEdgesMap> conditionalEdgesMaps = new LinkedHashMap<>();

    ConditionalIndex conditionalIndex;
    EncodingManager encodingManager;

    private String encoderName;

    public static final String ACCESS = "conditional_access";
    public static final String SPEED = "conditional_speed";

    public ConditionalEdges(EncodingManager encodingManager, String encoderName, Directory dir) {
        this.encodingManager = encodingManager;
        this.encoderName = encoderName;
        if (this.conditionalIndex != null || !conditionalEdgesMaps.isEmpty())
            throw new AssertionError("The conditional restrictions storage must be initialized only once.");

        this.conditionalIndex = new ConditionalIndex(dir, this.encoderName);

        for (FlagEncoder encoder : this.encodingManager.fetchEdgeEncoders()) {
            String name = this.encodingManager.getKey(encoder, this.encoderName);
            if (this.encodingManager.hasEncodedValue(name)) {
                String mapName = this.encoderName + "_" + encoder.toString();
                ConditionalEdgesMap conditionalEdgesMap = new ConditionalEdgesMap(mapName, conditionalIndex, dir.find(mapName));
                conditionalEdgesMaps.put(encoder.toString(), conditionalEdgesMap);
            }
        }
    }

    public ConditionalEdgesMap getConditionalEdgesMap(String encoder) {
        return conditionalEdgesMaps.get(encoder);
    }

    @Override
    public ConditionalEdges create(long byteCount) {
        conditionalIndex.create(byteCount);
        for (ConditionalEdgesMap conditionalEdgesMap: conditionalEdgesMaps.values())
            conditionalEdgesMap.create(byteCount);
        return this;
    }

    @Override
    public boolean loadExisting() {
        if (!conditionalIndex.loadExisting())
            throw new IllegalStateException("Cannot load 'conditionals'. corrupt file or directory? ");
        for (ConditionalEdgesMap conditionalEdgesMap: conditionalEdgesMaps.values())
            if (!conditionalEdgesMap.loadExisting())
                throw new IllegalStateException("Cannot load 'conditional_edges_map'. corrupt file or directory? ");
        return true;
    }

    @Override
    public void flush() {
        conditionalIndex.flush();
        for (ConditionalEdgesMap conditionalEdgesMap: conditionalEdgesMaps.values())
            conditionalEdgesMap.flush();
    }

    @Override
    public void close() {
        conditionalIndex.close();
        for (ConditionalEdgesMap conditionalEdgesMap: conditionalEdgesMaps.values())
            conditionalEdgesMap.close();
    }

    @Override
    public long getCapacity() {
        long capacity = conditionalIndex.getCapacity();
        for (ConditionalEdgesMap conditionalEdgesMap: conditionalEdgesMaps.values())
            capacity += conditionalEdgesMap.getCapacity();
        return capacity;
    }

    @Override
    public String toString() {
        return "conditional_edges";
    }

    @Override
    public boolean isClosed() {
        return false;
    }

};


