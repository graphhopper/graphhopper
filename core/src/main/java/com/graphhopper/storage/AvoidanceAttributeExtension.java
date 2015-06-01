/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in 
 *  compliance with the License. You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.storage;

import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;

/**
 * Holds avoidance attribute table for each edge. The additional field of an edge will be used to point
 * towards an entry within an edge attribute table to identify additional edge attributes.
 * <p>
 * @author Stuart Adam
 * @author Peter Karich
 */
public class AvoidanceAttributeExtension implements GraphExtension
{
    /* pointer for no cost entry */
    private final int NO_TURN_ENTRY = -1;
    private final long EMPTY_FLAGS = 0L;

  

    private DataAccess avoidanceFlags;
    private int avoidanceFlagEntryIndex = -4;
    private int avoidanceAttributeFlagBytes;
    private int attributeCount;

    private GraphStorage graph;

    public AvoidanceAttributeExtension()
    {
        avoidanceAttributeFlagBytes = avoidanceFlagEntryIndex + 4;
        attributeCount = 0;
    }

    @Override
    public void init( GraphStorage graph )
    {
        if (attributeCount > 0)
            throw new AssertionError("The avoidance attribute storage must be initialized only once.");

        this.graph = graph;
        this.avoidanceFlags = this.graph.getDirectory().find("avoidance_flags");
    }

    private int nextAttributeFlagIndex()
    {
        avoidanceFlagEntryIndex += 4;
        return avoidanceFlagEntryIndex;
    }

    @Override
    public void setSegmentSize( int bytes )
    {
        avoidanceFlags.setSegmentSize(bytes);
    }

    @Override
    public AvoidanceAttributeExtension create( long initBytes )
    {
        avoidanceFlags.create((long) initBytes * avoidanceAttributeFlagBytes);
        return this;
    }

    @Override
    public void flush()
    {
        avoidanceFlags.setHeader(0, avoidanceAttributeFlagBytes);
        avoidanceFlags.setHeader(1 * 4, attributeCount);
        avoidanceFlags.flush();
    }

    @Override
    public void close()
    {
        avoidanceFlags.close();
    }

    @Override
    public long getCapacity()
    {
        return avoidanceFlags.getCapacity();
    }

    @Override
    public boolean loadExisting()
    {
        if (!avoidanceFlags.loadExisting())
            return false;

        avoidanceAttributeFlagBytes = avoidanceFlags.getHeader(0);
        attributeCount = avoidanceFlags.getHeader(4);
        return true;
    }

    /**
     * This method adds a new entry which is an edge attribute flags.
     * Check if there is actually a value to set and if so configure and set the appropriate storage.
     */
    public void addEdgeInfo( int edge, int adjNode, long attributeFlag )
    {
        
        if (attributeFlag == EMPTY_FLAGS)
            return;

        EdgeIteratorState edgeProps = graph.getEdgeProps(edge, adjNode);
		int previousEntryIndex = edgeProps.getAdditionalField();
        if (previousEntryIndex == NO_TURN_ENTRY)
        {
        	int newEntryIndex = createNewEntry(edgeProps);
            setAttributeEntry(attributeFlag, newEntryIndex);
        } else
        {
        	setAttributeEntry(attributeFlag, previousEntryIndex);
        }
    }

    /**
     * Ensure sufficient space for new attribute entry
     * set edge attribute-pointer to this new entry
     * @param edgeProps edgeReference for this attribute set.
     * @return
     */
	private int createNewEntry(EdgeIteratorState edgeProps) {
		int newEntryIndex = nextAttributeFlagIndex();
		ensureAttributeIndex(newEntryIndex);
		edgeProps.setAdditionalField(newEntryIndex);
		return newEntryIndex;
	}

	private void setAttributeEntry(long attributeFlag, int newEntryIndex) {
		avoidanceFlags.setInt(newEntryIndex, (int)attributeFlag);
	}
    
    public long getAvoidanceFlags(long extensionPointer) {
    	if (extensionPointer > NO_TURN_ENTRY) {
        	return avoidanceFlags.getInt(extensionPointer);
        }
        return EMPTY_FLAGS;
	}

    private long nextCostFlags( int edgeId, int adjNode)
    {
    	EdgeIteratorState edgeProps = graph.getEdgeProps(edgeId, adjNode);
        int extensionPointer = edgeProps.getAdditionalField();
        return getAvoidanceFlags(extensionPointer);
    }

    private void ensureAttributeIndex( int nodeIndex )
    {
        avoidanceFlags.ensureCapacity(((long) nodeIndex + 4) * avoidanceAttributeFlagBytes);
    }

    @Override
    public boolean isRequireNodeField()
    {
        return false;
    }

    @Override
    /**
     * We require the additional field in the graph to point to the first entry in the edge table
     */
    public boolean isRequireEdgeField()
    {
        return true;
    }

    @Override
    /**
     * Avoidances are properties of edges so no node entries
     */
    public int getDefaultNodeFieldValue()
    {
    	throw new UnsupportedOperationException("Not supported by this storage");
    }

    @Override
    public int getDefaultEdgeFieldValue()
    {
    	return NO_TURN_ENTRY;
    }

    @Override
    public GraphExtension copyTo( GraphExtension clonedStorage )
    {
        if (!(clonedStorage instanceof AvoidanceAttributeExtension))
        {
            throw new IllegalStateException("the extended storage to clone must be the same");
        }

        AvoidanceAttributeExtension clonedTC = (AvoidanceAttributeExtension) clonedStorage;

        avoidanceFlags.copyTo(clonedTC.avoidanceFlags);
        clonedTC.attributeCount = attributeCount;

        return clonedStorage;
    }

    @Override
    public boolean isClosed()
    {
        return avoidanceFlags.isClosed();
    }

    @Override
    public String toString()
    {
        return "avoidance";
    }

}
