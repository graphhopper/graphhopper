/*
 *  Copyright 2012 Peter Karich info@jetsli.de
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package de.jetsli.graph.util;

import de.jetsli.graph.storage.EdgeWithFlags;
import gnu.trove.list.array.TIntArrayList;
import java.io.IOException;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps the raw data of edges - e.g. a linked list implemented via an int array.
 *
 * @author Peter Karich
 */
public class EdgesWrapper {

    private static final int LEN_DIST = 1;
    private static final int LEN_NODEID = 1;
    private static final int LEN_FLAGS = 1;
    private static final int LEN_LINK = 1;
    private static final int LEN_EDGE = LEN_FLAGS + LEN_DIST + LEN_NODEID + LEN_LINK;
    private Logger logger = LoggerFactory.getLogger(getClass());
    private int[] refToEdges;
    // TODO use a bitset to memorize fragmented edges-area!
    private int[] edgesArea;
    private float factor;
    private int edgeLength;
    private int nextEdgePointer;

    public EdgesWrapper() {
        // TODO remove this
        edgeLength = LEN_EDGE;
    }

    public void initNodes(int cap) {
        refToEdges = new int[cap];
    }

    public void ensureNodes(int cap) {
        refToEdges = Arrays.copyOf(refToEdges, cap);
    }

    public void initEdges(int noOfEdges) {
        int cap = noOfEdges * edgeLength;
        edgesArea = new int[cap];
        Arrays.fill(edgesArea, -1);
        edgesArea = new int[cap];
    }

    // TODO lock this?
    public void ensureEdges(int noOfEdges) {
        ensureEdgePointer(noOfEdges * LEN_EDGE);
    }

    // Use ONLY within a writer lock area
    private void ensureEdgePointer(int pointer) {
        if (pointer + LEN_EDGE < edgesArea.length)
            return;

        pointer = Math.max(10 * LEN_EDGE, Math.round(pointer * factor));
        logger.info("ensure edges to " + (float) 4 * pointer / (1 << 20) + " MB");
        int oldLen = edgesArea.length;
        int newLen = pointer;
        edgesArea = Arrays.copyOf(edgesArea, newLen);
        Arrays.fill(edgesArea, oldLen, newLen, -1);
    }

    public void setEdgeLength(int edgeLength) {
        this.edgeLength = edgeLength;
    }

    public void setFactor(float f) {
        factor = f;
    }

    public int getLink(int edgePointer) {
        return edgePointer + edgeLength - LEN_LINK;
    }

    public void writeEdge(int edgePointer, int flags, float dist, int toNodeId, int nextEdgePointer) {
        ensureEdgePointer(edgePointer);

        edgesArea[edgePointer] = flags;
        edgePointer += LEN_FLAGS;

        edgesArea[edgePointer] = Float.floatToIntBits(dist);
        edgePointer += LEN_DIST;

        edgesArea[edgePointer] = toNodeId;
        edgePointer += LEN_NODEID;

        edgesArea[edgePointer] = nextEdgePointer;
        // edgePointer += LEN_LINK;
    }

    private int nextEdgePointer() {
        nextEdgePointer += LEN_EDGE;
        return nextEdgePointer;
    }

    public void add(int fromNodeId, int toNodeId, float dist, byte flags) {
        int edgePointer = refToEdges[fromNodeId];
        int newPos = nextEdgePointer();
        if (edgePointer > 0) {
            TIntArrayList list = readAllEdges(edgePointer);
            // TODO sort by priority but include the latest entry too!
            // Collections.sort(list, listPrioSorter);
            // int len = list.size();
            // for (int i = 0; i < len; i++) {
            //    int pointer = list.get(i);
            //    copyEdge();
            // }
            if (list.isEmpty())
                throw new IllegalStateException("list cannot be empty for positive edgePointer " + edgePointer + " node:" + fromNodeId);

            int linkPointer = getLink(list.get(list.size() - 1));
            edgesArea[linkPointer] = newPos;
        } else
            refToEdges[fromNodeId] = newPos;

        writeEdge(newPos, flags, dist, toNodeId, -1);
    }

    private TIntArrayList readAllEdges(int edgePointer) {
        TIntArrayList list = new TIntArrayList(5);
        int i = 0;
        for (; i < 1000; i++) {
            list.add(edgePointer);
            edgePointer = edgesArea[getLink(edgePointer)];
            if (edgePointer < 0)
                break;
        }
        if (i >= 1000)
            throw new IllegalStateException("endless loop? edge count is probably not higher than " + i);
        return list;
    }

    @Override
    public Object clone() {
        EdgesWrapper w = new EdgesWrapper();
        w.initEdges(edgesArea.length);
        w.initNodes(refToEdges.length);
        System.arraycopy(edgesArea, 0, w.edgesArea, 0, edgesArea.length);
        System.arraycopy(refToEdges, 0, w.refToEdges, 0, refToEdges.length);
        return w;
    }

    public void save(String storageLocation) throws IOException {
        Helper.writeInts(storageLocation + "/edges", edgesArea);
        Helper.writeInts(storageLocation + "/refs", refToEdges);
    }

    public void read(String storageLocation) throws IOException {
        edgesArea = Helper.readInts(storageLocation + "/edges");
        refToEdges = Helper.readInts(storageLocation + "/refs");
    }

    private class EdgeIterable extends MyIteratorable<EdgeWithFlags> {

        private int pointer;
        private boolean in;
        private boolean out;
        private EdgeWithFlags next;

        public EdgeIterable(int node, boolean in, boolean out) {
            this.pointer = refToEdges[node];
            this.in = in;
            this.out = out;
            next();
        }

        @Override public boolean hasNext() {
            return next != null;
        }

        EdgeWithFlags readNext() {
            if (pointer <= 0)
                return null;

            // readLock.lock();  
            int origPointer = pointer;
            // skip node priority for now
            // int priority = priorities[pointer];            

            byte flags = (byte) edgesArea[pointer];
            if (!in && (flags & 1) == 0 || !out && (flags & 2) == 0) {
                pointer = edgesArea[getLink(origPointer)];
                return null;
            }
            pointer += LEN_FLAGS;

            float dist = Float.intBitsToFloat(edgesArea[pointer]);
            pointer += LEN_DIST;

            int nodeId = edgesArea[pointer];
            pointer += LEN_NODEID;
            // next edge
            pointer = edgesArea[pointer];
            return new EdgeWithFlags(nodeId, (double) dist, flags);
        }

        @Override public EdgeWithFlags next() {
            EdgeWithFlags tmp = next;
            int i = 0;
            next = null;
            for (; i < 1000; i++) {
                next = readNext();
                if (next != null || pointer < 0)
                    break;
            }
            if (i > 1000)
                throw new IllegalStateException("something went wrong: no end of edge-list found");

            return tmp;
        }

        @Override public void remove() {
            // markDeleted(firstPointer);
            throw new IllegalStateException("not implemented yet");
        }
    }

    public MyIteratorable<EdgeWithFlags> createEdgeIterable(int nodeId, boolean in, boolean out) {
        return new EdgeIterable(nodeId, in, out);
    }

    public int size() {
        return edgesArea.length / edgeLength;
    }
}
