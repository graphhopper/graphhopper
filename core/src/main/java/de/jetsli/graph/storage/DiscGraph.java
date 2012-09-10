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
package de.jetsli.graph.storage;

import de.jetsli.graph.routing.util.CarStreetType;
import de.jetsli.graph.util.EdgeIterator;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * @author Peter Karich
 */
public class DiscGraph implements SaveableGraph {

    private static int EMPTY_LINK = 0;
    private static final float DIST_UNIT = 10000f;
    protected static final int LEN_NODEA_ID = 1;
    protected static final int LEN_NODEB_ID = 1;
    protected static final int LEN_LINKA = 1;
    protected static final int LEN_LINKB = 1;
    protected static final int LEN_FLAGS = 1;
    protected static final int LEN_DIST = 1;
    private static final int LEN_EDGE = LEN_NODEA_ID + LEN_NODEB_ID + LEN_LINKA + LEN_LINKB
            + LEN_FLAGS + LEN_DIST;
    private String storageLocation;
    private RandomAccessFile metaData;
    private int tmpSize = 0;
    private int nextEdgePointer = 0;
    // nodes
    private RandomAccessFile latsFile;
    private RandomAccessFile lonsFile;
    private RandomAccessFile refToEdgesFile;
    // edges
    private RandomAccessFile edgesFile;

    public DiscGraph(String storageDir, int nodes) {
        this(storageDir, nodes, nodes * 2);
    }

    public DiscGraph(String storageDir, int nodes, int edges) {
        storageLocation = storageDir;
        try {
            // TODO load existing
            new File(storageDir).mkdirs();
            latsFile = new RandomAccessFile(new File(storageDir, "lats"), "rw");
            latsFile.setLength(0);
            lonsFile = new RandomAccessFile(new File(storageDir, "lons"), "rw");
            lonsFile.setLength(0);
            refToEdgesFile = new RandomAccessFile(new File(storageDir, "refs"), "rw");
            refToEdgesFile.setLength(0);
            edgesFile = new RandomAccessFile(new File(storageDir, "edges"), "rw");
            edgesFile.setLength(0);
            ensureNodeCap(nodePos(nodes));
            ensureEdgePointer(edges);

            metaData = new RandomAccessFile(new File(storageDir, "metadata"), "rw");
            metaData.setLength(8);
            clearFile(metaData, 0, 8);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public int getNodes() {
        return tmpSize;
    }

    @Override
    public void setNode(int index, double lat, double lon) {
        try {
            ensureNodeIndex(index);
            latsFile.seek(nodePos(index));
            latsFile.writeFloat((float) lat);
            lonsFile.seek(nodePos(index));
            lonsFile.writeFloat((float) lon);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private long nodePos(int index) {
        // index to byte pointer of integer values
        return (long) index * 4;
    }

    private long edgePos(int index) {
        // index to byte pointer of integer values
        return (long) index * 4;
    }

    @Override
    public double getLatitude(int index) {
        try {
            latsFile.seek(nodePos(index));
            return latsFile.readFloat();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public double getLongitude(int index) {
        try {
            lonsFile.seek(nodePos(index));
            return lonsFile.readFloat();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void edge(int fromNodeId, int toNodeId, double distance, int flags) {
        try {
            ensureNodeIndex(fromNodeId);
            ensureNodeIndex(toNodeId);
            int newEdgePointer = nextEdgePointer();
            connectNewEdge(fromNodeId, newEdgePointer);
            connectNewEdge(toNodeId, newEdgePointer);
            writeEdge(newEdgePointer, fromNodeId, toNodeId, EMPTY_LINK, EMPTY_LINK, flags, distance);
        } catch (IOException ex) {
            throw new RuntimeException("couldn't create edge " + fromNodeId + "->" + toNodeId + " with " + distance, ex);
        }
    }

    private int nextEdgePointer() throws IOException {
        nextEdgePointer += LEN_EDGE;
        if (nextEdgePointer < 0)
            throw new IllegalStateException("too many edges. new edge pointer would be negative.");

        return nextEdgePointer;
    }

    int getRefToEdges(int index) throws IOException {
        refToEdgesFile.seek(nodePos(index));
        refToEdgesFile.length();
        return refToEdgesFile.readInt();
    }

    protected void connectNewEdge(int nodeId, int newOrExistingEdgePointer) throws IOException {
        int edgePointer = getRefToEdges(nodeId);
        if (edgePointer > 0) {
            // append edge and overwrite EMPTY_LINK
            int lastEdgePointer = getLastEdgePointer(nodeId, edgePointer);
            saveToEdgeArea(lastEdgePointer, newOrExistingEdgePointer);
        } else {
            refToEdgesFile.seek(nodePos(nodeId));
            refToEdgesFile.writeInt(newOrExistingEdgePointer);
        }
    }

    // writes distance, flags, nodeThis, *nodeOther* and nextEdgePointer
    protected int writeEdge(int edgePointer, int nodeThis, int nodeOther,
            int nextEdgePointer, int nextEdgeOtherPointer, int flags, double dist) throws IOException {
        ensureEdgePointer(edgePointer);
        if (nodeThis > nodeOther) {
            int tmp = nodeThis;
            nodeThis = nodeOther;
            nodeOther = tmp;

            tmp = nextEdgePointer;
            nextEdgePointer = nextEdgeOtherPointer;
            nextEdgeOtherPointer = tmp;

            flags = CarStreetType.swapDirection(flags);
        }

        writeA(edgePointer, nodeThis);
        writeB(edgePointer, nodeOther);
        edgePointer += LEN_NODEA_ID + LEN_NODEB_ID;

        saveToEdgeArea(edgePointer, nextEdgePointer);
        edgePointer += LEN_LINKA;

        saveToEdgeArea(edgePointer, nextEdgeOtherPointer);
        edgePointer += LEN_LINKB;

        saveToEdgeArea(edgePointer, flags);
        edgePointer += LEN_FLAGS;

        saveToEdgeArea(edgePointer, (int) (dist * DIST_UNIT));
        return edgePointer + LEN_DIST;
    }

    private void ensureEdgePointer(int pointer) throws IOException {
        long newLen = edgePos(pointer + LEN_EDGE);
        if (newLen >= edgesFile.length()) {
            edgesFile.setLength(newLen);
            clearFile(edgesFile, edgesFile.length(), newLen);
        }
    }

    protected long ensureNodeIndex(int index) throws IOException {
        if (index < tmpSize)
            return -1;

        tmpSize = index + 1;
        if (nodePos(tmpSize) <= latsFile.length())
            return -1;
        long cap = Math.max(10, Math.round(nodePos(tmpSize) * 1.5));
        return ensureNodeCap(cap);
    }

    protected long ensureNodeCap(long cap) throws IOException {
        refToEdgesFile.setLength(cap);
        clearFile(refToEdgesFile, refToEdgesFile.length(), cap);        

        latsFile.setLength(cap);
        clearFile(latsFile, latsFile.length(), cap);        

        lonsFile.setLength(cap);
        clearFile(lonsFile, lonsFile.length(), cap);        
        return cap;
    }

    void writeA(int edgePointer, int node) throws IOException {
        saveToEdgeArea(edgePointer, node);
    }

    void writeB(int edgePointer, int node) throws IOException {
        saveToEdgeArea(edgePointer + LEN_NODEA_ID, node);
    }

    @Override
    public void edge(int a, int b, double distance, boolean bothDirections) {
        edge(a, b, distance, CarStreetType.flagsDefault(bothDirections));
    }

    private int getLastEdgePointer(int nodeThis, int edgePointer) throws IOException {
        int lastLink = -1;
        int i = 0;
        int otherNode;
        for (; i < 1000; i++) {
            otherNode = getOtherNode(nodeThis, edgePointer);
            lastLink = getLinkPosInEdgeArea(nodeThis, otherNode, edgePointer);
            edgePointer = getFromEdgeArea(lastLink);
            if (edgePointer == EMPTY_LINK)
                break;
        }

        if (i >= 1000)
            throw new IllegalStateException("endless loop? edge count is probably not higher than " + i);
        return lastLink;
    }

    protected int getLinkPosInEdgeArea(int nodeThis, int nodeOther, int edgePointer) {
        if (nodeThis <= nodeOther)
            // get link to next a
            return edgePointer + LEN_NODEA_ID + LEN_NODEB_ID;

        // b
        return edgePointer + LEN_NODEA_ID + LEN_NODEB_ID + LEN_LINKA;
    }

    private int getOtherNode(int nodeThis, int edgePointer) throws IOException {
        int nodeA = getFromEdgeArea(edgePointer);
        if (nodeA == nodeThis)
            // return b
            return getFromEdgeArea(edgePointer + LEN_NODEA_ID);
        // return a
        return nodeA;
    }

    protected int getFromEdgeArea(int pointer) throws IOException {
        edgesFile.seek(edgePos(pointer));
        return edgesFile.readInt();
    }

    protected void saveToEdgeArea(int pointer, int data) throws IOException {
        edgesFile.seek(edgePos(pointer));
        edgesFile.writeInt(data);
    }

    @Override
    public void close() {
        flush();
        try {
            latsFile.close();
            lonsFile.close();
            refToEdgesFile.close();
            edgesFile.close();
            metaData.close();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void flush() {
        try {
            latsFile.getChannel().force(true);
            lonsFile.getChannel().force(true);
            refToEdgesFile.getChannel().force(true);
            edgesFile.getChannel().force(true);

            metaDataFlush();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void metaDataFlush() throws IOException {
        metaData.seek(0);
        metaData.writeInt(nextEdgePointer);
        metaData.writeInt(tmpSize);
        metaData.getChannel().force(true);
    }

    @Override
    public EdgeIterator getEdges(int nodeId) {
        return new EdgeIterable(nodeId, true, true);
    }

    @Override
    public EdgeIterator getIncoming(int nodeId) {
        return new EdgeIterable(nodeId, true, false);
    }

    @Override
    public EdgeIterator getOutgoing(int nodeId) {
        return new EdgeIterable(nodeId, false, true);
    }

    protected class EdgeIterable implements EdgeIterator {

        int pointer;
        boolean in;
        boolean out;
        boolean foundNext;
        // edge properties        
        int flags;
        double distance;
        int nodeId;
        final int fromNode;
        int nextEdgePointer;

        public EdgeIterable(int node, boolean in, boolean out) {
            this.fromNode = node;
            try {
                this.nextEdgePointer = getRefToEdges(node);
            } catch (IOException ex) {
                throw new RuntimeException("problem while creating edgeiterable from node " + node, ex);
            }
            this.in = in;
            this.out = out;
        }

        void readNext() throws IOException {
            // readLock.lock();
            pointer = nextEdgePointer;
            nodeId = getOtherNode(fromNode, pointer);
            if (fromNode != getOtherNode(nodeId, pointer))
                throw new IllegalStateException("requested node " + fromNode + " not stored in edge. "
                        + "was:" + nodeId + "," + getOtherNode(nodeId, pointer));

            // position to next edge
            nextEdgePointer = getFromEdgeArea(getLinkPosInEdgeArea(fromNode, nodeId, pointer));
            flags = getFlags(pointer);

            // switch direction flags if necessary
            if (fromNode > nodeId)
                flags = CarStreetType.swapDirection(flags);

            if (!in && !CarStreetType.isForward(flags) || !out && !CarStreetType.isBackward(flags)) {
                // skip this edge as it does not fit to defined filter
            } else {
                distance = getDist(pointer);
                foundNext = true;
            }
        }

        int edgePointer() {
            return pointer;
        }

        int nextEdgePointer() {
            return nextEdgePointer;
        }

        @Override public boolean next() {
            try {
                int i = 0;
                foundNext = false;
                for (; i < 1000; i++) {
                    if (nextEdgePointer == EMPTY_LINK)
                        break;
                    readNext();
                    if (foundNext)
                        break;
                }
                if (i > 1000)
                    throw new IllegalStateException("something went wrong: no end of edge-list found");
                return foundNext;
            } catch (IOException ex) {
                throw new RuntimeException("problem while calling next", ex);
            }
        }

        @Override public int node() {
            return nodeId;
        }

        @Override public double distance() {
            return distance;
        }

        @Override public int flags() {
            return flags;
        }

        @Override public int fromNode() {
            return fromNode;
        }
    }

    private float intToDist(int integ) {
        return integ / DIST_UNIT;
    }

    private int getFlags(int pointer) throws IOException {
        return getFromEdgeArea(pointer + LEN_NODEA_ID + LEN_NODEB_ID + LEN_LINKA + LEN_LINKB);
    }

    private float getDist(int pointer) throws IOException {
        return intToDist(getFromEdgeArea(pointer + LEN_NODEA_ID + LEN_NODEB_ID + LEN_LINKA + LEN_LINKB + LEN_FLAGS));
    }

    @Override
    public void markNodeDeleted(int index) {
    }

    @Override
    public boolean isDeleted(int index) {
        return false;
    }

    @Override
    public void optimize() {
    }

    @Override
    public String toString() {
        return "disc graph at " + storageLocation;
    }

    protected DiscGraph creatThis(String storage, int nodes, int edges) {
        return new DiscGraph(storage, nodes, edges);
    }

    @Override
    public Graph clone() {
        // readLock.lock();
        DiscGraph clonedGraph = creatThis(storageLocation + "-clone", tmpSize, 2 * tmpSize);
        try {
            flush();
            copyRAF(latsFile, clonedGraph.latsFile);
            copyRAF(lonsFile, clonedGraph.lonsFile);
            copyRAF(refToEdgesFile, clonedGraph.refToEdgesFile);
            copyRAF(edgesFile, clonedGraph.edgesFile);
            copyRAF(metaData, clonedGraph.metaData);
            clonedGraph.tmpSize = tmpSize;
            clonedGraph.nextEdgePointer = nextEdgePointer;
            return clonedGraph;
        } catch (IOException ex) {
            throw new RuntimeException("couldn't clone graph", ex);
        }
    }

    private static void copyRAF(RandomAccessFile rafFrom, RandomAccessFile rafTo) throws IOException {
        rafFrom.seek(0);
        rafTo.seek(0);
        rafTo.setLength(rafFrom.length());
        byte[] buffer = new byte[65536];
        int numRead;
        while ((numRead = rafFrom.read(buffer)) != -1) {
            rafTo.write(buffer, 0, numRead);
        }
    }
    private static byte[] EMPTY = new byte[1024 * 8];

    private static void clearFile(RandomAccessFile file, long from, long to) throws IOException {
        file.seek(from);
        long diff = to - from;
        int len = EMPTY.length;
        int rest = (int) (diff % len);
        diff -= rest;
        int i = 0;
        for (; i < diff; i += len) {
            file.write(EMPTY, i, len);
        }

        if (rest > 0)
            file.write(EMPTY, i, rest);
    }
}
