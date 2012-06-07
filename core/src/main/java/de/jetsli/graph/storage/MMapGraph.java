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

import de.jetsli.graph.util.BitUtil;
import de.jetsli.graph.util.Helper;
import de.jetsli.graph.util.MyIteratorable;
import gnu.trove.map.hash.TIntIntHashMap;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static de.jetsli.graph.util.MyIteratorable.*;
import gnu.trove.map.hash.TIntFloatHashMap;
import java.io.File;
import java.util.logging.Level;

/**
 * A graph represenation which can be stored directly on disc when using the memory mapped
 * constructor.
 *
 * TODO thread safety
 *
 * @author Peter Karich, info@jetsli.de
 */
public class MMapGraph implements Graph, java.io.Closeable {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final int EMPTY_DIST = 0;
    /**
     * Memory layout of one node: lat, lon, 2 * DistEntry(distance, node, flags) +
     * refToNextDistEntryBlock <br/>
     *
     * Note: <ul> <li> If refToNextDistEntryBlock is EMPTY_DIST => no further block referenced.
     * </li> <li>If the distance is EMPTY_DIST (read as int) then the distEntry is assumed to be
     * empty thatswhy we don't support null length edges. This saves us from the ugly
     * preinitialization and we can use node 0 too </li> <li> nextEdgePosition starts at 1 to avoid
     * that hassle. Should we allow this for nodes too < - problematic for existing tests </li>
     */
    private int maxNodes;
    /**
     * how many distEntry should be embedded directly in the node. This saves memory as we don't
     * need pointers to the next distEntry
     */
    private int distEntryEmbedded = 2;
    /**
     * Memory layout of one LinkedDistEntryWithFlags: distance, node, flags
     */
    private int distEntrySize = 4 + 4 + 1;
    /**
     * Storing latitude and longitude directly in the node
     */
    private int nodeCoreSize = 4 + 4;
    private int nodeSize;
    private int currentNodeSize = 0;
    private int maxRecognizedNodeIndex = -1;
    private int nextEdgePosition = 1;
    private RandomAccessFile nodeFile = null;
    private RandomAccessFile edgeFile = null;
    private ByteBuffer nodes;
    private ByteBuffer edges;
    private String fileName;
    private int distEntryFlagsPos = distEntrySize - 1;
    private int bytesDistEntrySize = distEntryEmbedded * distEntrySize + 4;

    /**
     * Creates an in-memory graph suitable for test
     */
    public MMapGraph(int maxNodes) {
        this(null, maxNodes);
    }

    /**
     * Creates a memory-mapped graph
     */
    public MMapGraph(String name, int maxNodes) {
        this.maxNodes = maxNodes;
        this.fileName = name;
    }

    public boolean loadExisting() {
        if (!loadSettings())
            return false;

        logger.info("maxNodes:" + maxNodes + " distEntryEmbedded:" + distEntryEmbedded
                + " distEntrySize:" + distEntrySize + " nodeCoreSize:" + nodeCoreSize
                + " nodeSize:" + nodeSize + " currentNodeSize:" + currentNodeSize
                + " maxRecognizedNodeIndex:" + maxRecognizedNodeIndex + " nextEdgePosition:" + nextEdgePosition
                + " distEntryFlagsPos:" + distEntryFlagsPos + " bytesDistEntrySize:" + bytesDistEntrySize);
        init();
        return true;
    }

    public MMapGraph createNew() {
        try {
            close();
        } catch (IOException ex) {
            logger.error("Couldn't close underlying memory mapped files", ex);
        }

        if (fileName != null)
            Helper.deleteFilesStartingWith(fileName);

        nodeSize = nodeCoreSize + distEntryEmbedded * distEntrySize + 4;
        return init();

    }

    private MMapGraph init() {
        int tmpCapacity = this.maxNodes * nodeSize;

        // the more edges we inline the less memory we need to reserve => " / distEntryEmbedded"
        // if we provide too few memory => BufferUnderflowException
        // and yeah, I know, distEntryEmbedded / distEntryEmbedded == 1 ... just to make it more clear
        int tmpEdge = this.maxNodes / distEntryEmbedded * (distEntryEmbedded * distEntrySize + 4);

        if (fileName == null) {
            nodes = ByteBuffer.allocate(tmpCapacity);
            // ByteOrder.nativeOrder() would make it compatible with CPP but then we
            // would need case dependent util calls to ByteUtil            
            nodes.order(ByteOrder.BIG_ENDIAN);

            edges = ByteBuffer.allocate(tmpEdge);
            edges.order(ByteOrder.BIG_ENDIAN);
        } else {
            try {
                logger.info("Mapping node file (" + (float) tmpCapacity / (1 << 20) + " MB) ...");
                nodeFile = new RandomAccessFile(fileName + "-nodes", "rw");
                nodes = nodeFile.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, tmpCapacity);
                nodes.order(ByteOrder.BIG_ENDIAN);
                logger.info("Mapping edge file (" + (float) tmpEdge / (1 << 20) + " MB)...");
                edgeFile = new RandomAccessFile(fileName + "-egdes", "rw");
                edges = edgeFile.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, tmpEdge);
                edges.order(ByteOrder.BIG_ENDIAN);
            } catch (Exception ex) {
                try {
                    close();
                } catch (Exception ex2) {
                    throw new RuntimeException(ex2.getCause());
                }
                throw new RuntimeException(ex.getCause());
            }
        }
        return this;
    }

    /**
     * Calling this method after init() has no effect
     */
    public void setDistEntryEmbedded(int distEntryEmbedded) {
        this.distEntryEmbedded = distEntryEmbedded;
    }

    @Override
    public void ensureCapacity(int cap) {
        // TODO increase memory allocation or memory mapping
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int getLocations() {
        return Math.max(currentNodeSize, maxRecognizedNodeIndex + 1);
    }

    @Override
    public int addLocation(double lat, double lon) {
        if (currentNodeSize > maxNodes)
            throw new IllegalStateException("Maximum number of node reached. see ensureCapacity");

        nodes.position(currentNodeSize * nodeSize);
        nodes.putFloat((float) lat);
        nodes.putFloat((float) lon);
        int tmp = currentNodeSize;
        currentNodeSize++;
        return tmp;
    }

    @Override
    public double getLatitude(int index) {
        return nodes.getFloat(index * nodeSize);
    }

    @Override
    public double getLongitude(int index) {
        return nodes.getFloat(index * nodeSize + 4);
    }

    @Override
    public void edge(int a, int b, double distance, boolean bothDirections) {
        if (distance <= 0)
            throw new UnsupportedOperationException("negative or zero distances are not supported:"
                    + a + " -> " + b + ": " + distance + ", bothDirections:" + bothDirections);

        maxRecognizedNodeIndex = Math.max(maxRecognizedNodeIndex, Math.max(a, b));
        byte dirFlag = 3;
        if (!bothDirections)
            dirFlag = 1;

        addIfAbsent(a * nodeSize + nodeCoreSize, b, (float) distance, dirFlag);
        if (!bothDirections)
            dirFlag = 2;

        addIfAbsent(b * nodeSize + nodeCoreSize, a, (float) distance, dirFlag);
    }

    @Override
    public MyIteratorable<DistEntry> getEdges(int index) {
        if (index >= maxNodes)
            return DistEntry.EMPTY_ITER;

        nodes.position(index * nodeSize + nodeCoreSize);
        byte[] bytes = new byte[bytesDistEntrySize];
        nodes.get(bytes);
        return new EdgesIteratorable(bytes);
    }

    @Override
    public MyIteratorable<DistEntry> getOutgoing(int index) {
        if (index >= maxNodes)
            return DistEntry.EMPTY_ITER;

        nodes.position(index * nodeSize + nodeCoreSize);
        byte[] bytes = new byte[bytesDistEntrySize];
        nodes.get(bytes);
        return new EdgesIteratorableFlags(bytes, (byte) 1);
    }

    @Override
    public MyIteratorable<DistEntry> getIncoming(int index) {
        if (index >= maxNodes)
            return DistEntry.EMPTY_ITER;

        nodes.position(index * nodeSize + nodeCoreSize);
        byte[] bytes = new byte[bytesDistEntrySize];
        nodes.get(bytes);
        return new EdgesIteratorableFlags(bytes, (byte) 2);
    }

    @Override
    public void removeLocation(int index) {
        MyIteratorable iter = getEdges(index);
        // remove edges

        // move last location to index

        // update node id in edges

        // update locations count
    }

    private class EdgesIteratorableFlags extends EdgesIteratorable {

        byte flags;

        EdgesIteratorableFlags(byte[] bytes, byte flags) {
            super(bytes);
            this.flags = flags;
        }

        @Override
        boolean checkFlags() {
            for (int tmpPos = position;;) {
                if (super.checkFlags())
                    tmpPos = 0;

                int tmp = BitUtil.toInt(bytes, tmpPos);
                if (tmp == EMPTY_DIST)
                    break;

                if ((bytes[tmpPos + distEntryFlagsPos] & flags) != 0)
                    break;

                tmpPos += distEntrySize;
                if (tmpPos >= bytes.length)
                    break;

                position = tmpPos;
            }
            return true;
        }
    }

    private class EdgesIteratorable extends MyIteratorable<DistEntry> {

        byte[] bytes;
        int position = 0;

        EdgesIteratorable(byte[] bytes) {
            this.bytes = bytes;
        }

        boolean checkFlags() {
            assert position <= distEntrySize * distEntryEmbedded;
            if (position == distEntrySize * distEntryEmbedded) {
                int tmp = BitUtil.toInt(bytes, position);
                if (tmp == EMPTY_DIST)
                    return false;

                position = 0;
                edges.position(tmp);
                edges.get(bytes);
                return true;
            }
            return false;
        }

        @Override public boolean hasNext() {
            checkFlags();
            assert position < bytes.length;
            return BitUtil.toInt(bytes, position) > EMPTY_DIST;
        }

        @Override public DistEntry next() {
            if (!hasNext())
                throw new IllegalStateException("No next element");

            float fl = BitUtil.toFloat(bytes, position);
            int integ = BitUtil.toInt(bytes, position += 4);
            LinkedDistEntryWithFlags lde = new LinkedDistEntryWithFlags(
                    integ, fl, bytes[position += 4]);
            position++;
            // TODO lde.prevEntry = 
            return lde;
        }

        @Override public void remove() {
            throw new UnsupportedOperationException("Not supported. We would bi-linked nextEntries or O(n) processing");
        }
    }

    /**
     * if distance entry with location already exists => overwrite distance. if it does not exist =>
     * append
     */
    void addIfAbsent(int nodePointer, int nodeIndex, float distance, byte dirFlag) {
        // move to node a its edges
        nodes.position(nodePointer);
        byte[] byteArray = new byte[distEntryEmbedded * distEntrySize + 4];
        nodes.get(byteArray);
        nodes.position(nodePointer);

        boolean usingNodesBuffer = true;
        // find free position or the identical nodeIndex where we need to update distance and flags        
        int byteArrayPos = 0;
        while (true) {
            // read distance as int and check if it is empty
            int tmp = BitUtil.toInt(byteArray, byteArrayPos);
            if (tmp == EMPTY_DIST) {
                // mark 'NO' next entry => no need as EMPTY_DIST is 0 
                // ByteUtil.fromInt(byteArray, EMPTY_DIST, byteArrayPos + distEntrySize);
                break;
            }

            tmp = BitUtil.toInt(byteArray, byteArrayPos + 4);
            if (tmp == nodeIndex)
                break;

            byteArrayPos += distEntrySize;
            assert byteArrayPos <= distEntrySize * distEntryEmbedded;

            if (byteArrayPos == distEntrySize * distEntryEmbedded) {
                tmp = BitUtil.toInt(byteArray, byteArrayPos);
                if (tmp == EMPTY_DIST) {
                    tmp = getNextFreeEdgeBlock();

                    if (usingNodesBuffer)
                        nodes.putInt(nodes.position() + byteArrayPos, tmp);
                    else
                        edges.putInt(edges.position() + byteArrayPos, tmp);
                }

                byteArrayPos = 0;
                usingNodesBuffer = false;
                edges.position(tmp);
                edges.get(byteArray);
                edges.position(tmp);
            }
        }

        BitUtil.fromFloat(byteArray, distance, byteArrayPos);
        byteArrayPos += 4;
        BitUtil.fromInt(byteArray, nodeIndex, byteArrayPos);
        byteArrayPos += 4;
        byteArray[byteArrayPos] = (byte) (byteArray[byteArrayPos] | dirFlag);

        if (usingNodesBuffer)
            nodes.put(byteArray);
        else
            edges.put(byteArray);
    }

    private int getNextFreeEdgeBlock() {
        int tmp = nextEdgePosition;
        nextEdgePosition += distEntrySize * distEntryEmbedded + 4;
        return tmp;
    }

    @Override
    public Graph clone() {
        if (fileName != null)
            logger.warn("Cloned graph will be in-memory only!");

        MMapGraph graphCloned = new MMapGraph(maxNodes);
        graphCloned.nodes = clone(nodes);
        graphCloned.edges = clone(edges);
        graphCloned.distEntryEmbedded = distEntryEmbedded;
        graphCloned.distEntrySize = distEntrySize;
        graphCloned.nodeCoreSize = nodeCoreSize;
        graphCloned.nodeSize = nodeSize;
        graphCloned.currentNodeSize = currentNodeSize;
        graphCloned.maxRecognizedNodeIndex = maxRecognizedNodeIndex;
        graphCloned.nextEdgePosition = nextEdgePosition;
        graphCloned.distEntryFlagsPos = distEntryFlagsPos;
        graphCloned.bytesDistEntrySize = bytesDistEntrySize;
        return graphCloned;
    }

    public static ByteBuffer clone(ByteBuffer original) {
        ByteBuffer clone = ByteBuffer.allocate(original.capacity());
        original.rewind();
        clone.put(original);
        original.rewind();
        clone.flip();
        return clone;
    }

    public void flush() {
        if (nodes instanceof MappedByteBuffer)
            ((MappedByteBuffer) nodes).force();

        if (edges instanceof MappedByteBuffer)
            ((MappedByteBuffer) edges).force();

        // store settings
        if (fileName != null) {
            try {
                String sFile = fileName + "-settings";

                RandomAccessFile settingsFile = new RandomAccessFile(sFile, "rw");
                settingsFile.writeInt(maxNodes);
                settingsFile.writeInt(distEntryEmbedded);
                settingsFile.writeInt(distEntrySize);
                settingsFile.writeInt(nodeCoreSize);
                settingsFile.writeInt(nodeSize);
                settingsFile.writeInt(currentNodeSize);
                settingsFile.writeInt(maxRecognizedNodeIndex);
                settingsFile.writeInt(nextEdgePosition);
                settingsFile.writeInt(distEntryFlagsPos);
                settingsFile.writeInt(bytesDistEntrySize);
            } catch (Exception ex) {
                logger.error("Problem while reading from settings file", ex);
            }
        }
    }

    public boolean loadSettings() {
        if (fileName == null)
            return false;

        try {
            String sFile = fileName + "-settings";
            if (!new File(sFile).exists())
                return false;

            RandomAccessFile settingsFile = new RandomAccessFile(sFile, "rw");
            maxNodes = settingsFile.readInt();
            distEntryEmbedded = settingsFile.readInt();
            distEntrySize = settingsFile.readInt();
            nodeCoreSize = settingsFile.readInt();
            nodeSize = settingsFile.readInt();
            currentNodeSize = settingsFile.readInt();
            maxRecognizedNodeIndex = settingsFile.readInt();
            nextEdgePosition = settingsFile.readInt();
            distEntryFlagsPos = settingsFile.readInt();
            bytesDistEntrySize = settingsFile.readInt();
            return true;
        } catch (Exception ex) {
            logger.error("Problem while reading from settings file", ex);
            return false;
        }
    }

    @Override public void close() throws IOException {
        flush();
        if (nodes instanceof MappedByteBuffer) {
            clean((MappedByteBuffer) nodes);
            if (nodeFile != null)
                nodeFile.close();
        }
        if (edges instanceof MappedByteBuffer) {
            clean((MappedByteBuffer) edges);
            if (edgeFile != null)
                edgeFile.close();
        }
    }

    private void clean(MappedByteBuffer mapping) {
        Helper.cleanMappedByteBuffer(mapping);
    }

    public void stats() {
        float locs = getLocations();
        int edgesNo = 0;
        int outEdgesNo = 0;
        int inEdgesNo = 0;

        int max = 50;
        TIntIntHashMap edgeStats = new TIntIntHashMap(max);
        TIntIntHashMap edgeOutStats = new TIntIntHashMap(max);
        TIntIntHashMap edgeInStats = new TIntIntHashMap(max);
        TIntFloatHashMap edgeLengthStats = new TIntFloatHashMap(max);
        for (int i = 0; i < max; i++) {
            edgeStats.put(i, 0);
            edgeOutStats.put(i, 0);
            edgeInStats.put(i, 0);
            edgeLengthStats.put(i, 0);
        }

        for (int i = 0; i < locs; i++) {
            float meanDist = 0;
            for (DistEntry de : getEdges(i)) {
                meanDist += de.distance;
            }

            int tmpEdges = count(getEdges(i));
            meanDist = meanDist / tmpEdges;

            int tmpOutEdges = count(getOutgoing(i));
            int tmpInEdges = count(getIncoming(i));
            edgesNo += tmpEdges;
            outEdgesNo += tmpOutEdges;
            inEdgesNo += tmpInEdges;

            edgeStats.increment(tmpEdges);
            edgeOutStats.increment(tmpOutEdges);
            edgeInStats.increment(tmpEdges);

            float tmp = edgeLengthStats.get(tmpEdges);
            if (tmp != edgeLengthStats.getNoEntryValue())
                meanDist += tmp;

            edgeLengthStats.put(tmpEdges, meanDist);
        }

        for (int i = 0; i < max; i++) {
            System.out.print(i + "\t");
        }
        System.out.println("");
        for (int i = 0; i < max; i++) {
            System.out.print(edgeStats.get(i) + "\t");
        }
        System.out.println("");
        for (int i = 0; i < max; i++) {
            System.out.print(edgeOutStats.get(i) + "\t");
        }
        System.out.println("");
        for (int i = 0; i < max; i++) {
            System.out.print(edgeInStats.get(i) + "\t");
        }
        System.out.println("");
        for (int i = 0; i < max; i++) {
            System.out.print(edgeLengthStats.get(i) + "\t");
        }
        System.out.println("\n-----------");
        System.out.println("edges      :" + edgesNo + "\t" + ((float) edgesNo / locs));
        System.out.println("edges - out:" + outEdgesNo + "\t" + ((float) outEdgesNo / locs));
        System.out.println("edges - in :" + inEdgesNo + "\t" + ((float) inEdgesNo / locs));
        System.out.println("currentNodeSize:" + currentNodeSize);
        System.out.println("maxRecognizedNodeIndex:" + maxRecognizedNodeIndex);
        System.out.println("nextEdgePosition:" + nextEdgePosition);
    }
}
