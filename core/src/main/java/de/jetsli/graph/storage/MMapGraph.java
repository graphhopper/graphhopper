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
import java.io.*;

/**
 * A graph represenation which can be stored directly on disc when using the memory mapped
 * constructor.
 *
 * TODO rewrite with:
 *
 * 1. better method encapsulation
 *
 * 2. allow edge distances of 0
 *
 * 3. store size in one variable do not use two: maxRecognizedNodeIndex and currentNodeSize
 *
 * 4. instead edge iterator => use collection. we'll have only a small number of edges => iterator
 * makes no sense and won't grab the information in one bulk operation
 *
 * (read+write thread safety)
 *
 * @author Peter Karich, info@jetsli.de
 */
public class MMapGraph implements Graph {

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
    private int edgeEmbedded = 2;
    /**
     * Memory layout of one LinkedDistEntryWithFlags: distance, node, flags
     */
    private int edgeSize = 4 + 4 + 1;
    /**
     * Storing latitude and longitude directly in the node
     */
    private int nodeCoreSize = 4 + 4;
    private int nodeSize;
    private int currentNodeSize = 0;
    private int maxRecognizedNodeIndex = -1;
    private int nextEdgePosition = 1;
    private double increaseFactor = 1.3;
    private RandomAccessFile nodeFile = null;
    private RandomAccessFile edgeFile = null;
    private ByteBuffer nodes;
    private ByteBuffer edges;
    private File dirName;
    private boolean saveOnFlushOnly = false;
    private int edgeFlagsPos = edgeSize - 1;
    private int bytesEdgeSize = edgeEmbedded * edgeSize + 4;

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
        // increase size a bit to avoid capacity increase only for the last nodes if user specified
        // the exact maxNode number
        this.maxNodes = maxNodes + 10;
        if (name != null)
            this.dirName = new File(name);
    }

    public boolean loadExisting() {
        if (!loadSettings())
            return false;

        logger.info("load existing graph with maxNodes:" + maxNodes + " currentNodeSize:" + currentNodeSize
                + " maxRecognizedNodeIndex:" + maxRecognizedNodeIndex + " edgeEmbedded:" + edgeEmbedded
                + " edgeSize:" + edgeSize + " nodeCoreSize:" + nodeCoreSize
                + " nodeSize:" + nodeSize + " nextEdgePosition:" + nextEdgePosition
                + " edgeFlagsPos:" + edgeFlagsPos + " bytesEdgeSize:" + bytesEdgeSize);
        ensureCapacity(maxNodes);
        return true;
    }

    int getNodesCapacity() {
        return maxNodes;
    }

    public MMapGraph createNew() {
        return createNew(false);
    }

    public MMapGraph createNew(boolean saveOnFlushOnly) {
        if (nodes != null)
            throw new IllegalStateException("You cannot use one instance multiple times");

        if (dirName != null) {
            Helper.deleteDir(dirName);
            dirName.mkdirs();
        }

        this.saveOnFlushOnly = saveOnFlushOnly;
        nodeSize = nodeCoreSize + bytesEdgeSize;
        ensureCapacity(maxNodes);
        return this;
    }

    /**
     * Calling this method after init() has no effect
     */
    public void setDistEntryEmbedded(int edgeEmbeddedSize) {
        this.edgeEmbedded = edgeEmbeddedSize;
    }

    @Override
    public void ensureCapacity(int nodes) {
        int newEdgeNo = calculateEdges(nodes);
        String str = "node file with " + (float) (nodes * nodeSize) / (1 << 20) + " MB and "
                + "edge file with " + (float) (newEdgeNo * bytesEdgeSize) / (1 << 20) + " MB";

        try {
            ensureNodesCapacity(nodes);
            ensureEdgesCapacity(newEdgeNo);
            logger.info("Mapped " + str);
        } catch (IOException ex) {
            close();
            throw new RuntimeException("Failed to map " + str, ex);
        }
    }

    protected boolean ensureNodesCapacity(int newNumberOfNodes) throws IOException {
        int newBytes = newNumberOfNodes * nodeSize;
        if (nodes != null) {
            if (newBytes < nodes.capacity())
                return false;
            newNumberOfNodes = (int) Math.max(newNumberOfNodes, increaseFactor * nodes.capacity() / nodeSize);
            newBytes = newNumberOfNodes * nodeSize;
        }
        maxNodes = newNumberOfNodes;
        if (dirName != null && !saveOnFlushOnly) {
            if (nodeFile == null)
                nodeFile = new RandomAccessFile(getNodesFile(), "rw");
            else {
                // necessary? clean((MappedByteBuffer) nodes);
                nodeFile.setLength(newBytes);
                logger.info("remap node file to " + (float) newBytes / nodeSize);
            }
            nodes = nodeFile.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, newBytes);
        } else
            nodes = copy(nodes, allocate(newBytes));

        nodes.order(ByteOrder.BIG_ENDIAN);
        return true;
    }

    static ByteBuffer allocate(int capacity) {
        // direct memory is a bit unpredictably for OSM import on a normal wordstation (<3GB)
        // return ByteBuffer.allocateDirect(capacity);
        return ByteBuffer.allocate(capacity);
    }

    /**
     * @return the minimum number of edges to be used in edge buffer
     */
    int calculateEdges(int maxNodes) {
        // the more edges we inline the less memory we need to reserve => " / edgeEmbedded"
        // if we provide too few memory => BufferUnderflowException will be thrown without calling ensureEdgesCapacity
        return Math.max(nextEdgePosition / bytesEdgeSize + 2, maxNodes / edgeEmbedded / 4);
    }

    protected boolean ensureEdgesCapacity(int newNumberOfEdges) throws IOException {
        int newBytes = newNumberOfEdges * bytesEdgeSize;
        if (edges != null) {
            if (newBytes < edges.capacity())
                return false;
            newBytes = (int) Math.max(newBytes, 1.3 * edges.capacity());
        }
        if (dirName != null && !saveOnFlushOnly) {
            if (edgeFile == null)
                edgeFile = new RandomAccessFile(getEdgesFile(), "rw");
            else {
                // necessary? clean((MappedByteBuffer) edges);
                edgeFile.setLength(newBytes);
                // logger.info("remapped edges to " + newBytes);
            }
            edges = edgeFile.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, newBytes);
        } else
            edges = copy(edges, allocate(newBytes));

        edges.order(ByteOrder.BIG_ENDIAN);
        return true;
    }

    ByteBuffer getEdges() {
        return edges;
    }

    private File getSettingsFile() {
        if (dirName == null)
            throw new IllegalStateException("dirName was null although required to store data");
        return new File(dirName, "settings");
    }

    private File getNodesFile() {
        if (dirName == null)
            throw new IllegalStateException("dirName was null although required to store data");
        return new File(dirName, "nodes");
    }

    private File getEdgesFile() {
        if (dirName == null)
            throw new IllegalStateException("dirName was null although required to store data");
        return new File(dirName, "egdes");
    }

    @Override
    public int getLocations() {
        return Math.max(currentNodeSize, maxRecognizedNodeIndex + 1);
    }

    @Override
    public int addLocation(double lat, double lon) {
        if (currentNodeSize + 1 >= maxNodes) {
            try {
                ensureNodesCapacity(currentNodeSize + 1);
            } catch (IOException ex) {
                throw new RuntimeException("Couldn't expand nodes from " + currentNodeSize, ex);
            }
        }

        nodes.position(currentNodeSize * nodeSize);
        nodes.putFloat((float) lat);
        nodes.putFloat((float) lon);
        int tmp = currentNodeSize;
        currentNodeSize++;
        return tmp;
    }

    @Override
    public final double getLatitude(int index) {
        float fl = nodes.getFloat(index * nodeSize);
        return (double) fl;
    }

    @Override
    public final double getLongitude(int index) {
        float fl = nodes.getFloat(index * nodeSize + 4);
        return (double) fl;
    }

    @Override
    public void edge(int a, int b, double distance, boolean bothDirections) {
        if (distance <= 0)
            throw new UnsupportedOperationException("negative or zero distances are not supported:"
                    + a + " -> " + b + ": " + distance + ", bothDirections:" + bothDirections);

        try {
            ensureEdgesCapacity(nextEdgePosition / bytesEdgeSize + 2);
        } catch (IOException ex) {
            throw new RuntimeException("Cannot ensure edge capacity!? edges capacity:" + edges.capacity()
                    + " vs. " + nextEdgePosition, ex);
        }

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
            throw new IllegalStateException("Cannot accept indices higher then maxNode");

        nodes.position(index * nodeSize + nodeCoreSize);
        byte[] bytes = new byte[bytesEdgeSize];
        nodes.get(bytes);
        return new EdgesIteratorable(bytes);
    }

    @Override
    public MyIteratorable<DistEntry> getOutgoing(int index) {
        if (index >= maxNodes)
            throw new IllegalStateException("Cannot accept indices higher then maxNode");

        nodes.position(index * nodeSize + nodeCoreSize);
        byte[] bytes = new byte[bytesEdgeSize];
        nodes.get(bytes);
        return new EdgesIteratorableFlags(bytes, (byte) 1);
    }

    @Override
    public MyIteratorable<DistEntry> getIncoming(int index) {
        if (index >= maxNodes)
            throw new IllegalStateException("Cannot accept indices higher then maxNode");

        nodes.position(index * nodeSize + nodeCoreSize);
        byte[] bytes = new byte[bytesEdgeSize];
        nodes.get(bytes);
        return new EdgesIteratorableFlags(bytes, (byte) 2);
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

                if ((bytes[tmpPos + edgeFlagsPos] & flags) != 0)
                    break;

                tmpPos += edgeSize;
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
            assert position <= edgeSize * edgeEmbedded;
            if (position == edgeSize * edgeEmbedded) {
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
        // move to the node its edges
        nodes.position(nodePointer);
        byte[] byteArray = new byte[bytesEdgeSize];
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

            byteArrayPos += edgeSize;
            assert byteArrayPos <= edgeSize * edgeEmbedded;

            if (byteArrayPos == edgeSize * edgeEmbedded) {
                tmp = BitUtil.toInt(byteArray, byteArrayPos);
                if (tmp < 0)
                    throw new IllegalStateException("Pointer to edges was negative!?");

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

    protected int getNextFreeEdgeBlock() {
        int tmp = nextEdgePosition;
        nextEdgePosition += bytesEdgeSize;
        return tmp;
    }

    @Override
    public Graph clone() {
        if (dirName != null) {
            // TODO with saveOnFlush we can easily clone the graph in-memory and flush to disc
            logger.error("Cloned graph will be in-memory only!");
        }

        MMapGraph graphCloned = new MMapGraph(maxNodes);
        graphCloned.nodes = clone(nodes);
        graphCloned.edges = clone(edges);
        graphCloned.edgeEmbedded = edgeEmbedded;
        graphCloned.edgeSize = edgeSize;
        graphCloned.nodeCoreSize = nodeCoreSize;
        graphCloned.nodeSize = nodeSize;
        graphCloned.currentNodeSize = currentNodeSize;
        graphCloned.maxRecognizedNodeIndex = maxRecognizedNodeIndex;
        graphCloned.nextEdgePosition = nextEdgePosition;
        graphCloned.edgeFlagsPos = edgeFlagsPos;
        graphCloned.bytesEdgeSize = bytesEdgeSize;
        return graphCloned;
    }

    public static ByteBuffer clone(ByteBuffer original) {
        return copy(original, allocate(original.capacity()));
    }

    public static ByteBuffer copy(ByteBuffer original, ByteBuffer copy) {
        if (original == null)
            return copy;

        original.rewind();
        copy.put(original);
        return copy;
    }

    /**
     * In case of saveOnFlushOnly we write the bytebuffer to disc. If it is direct its simple, if
     * not we need to copy chunks
     */
    void writeToDisc(File dirName, ByteBuffer buf) {
        FileChannel channel;
        try {
            channel = new RandomAccessFile(dirName, "rw").getChannel();
        } catch (FileNotFoundException ex) {
            throw new RuntimeException("Cannot find " + dirName, ex);
        }

        try {
            // make sure we copy from 0 to capacity (== limit)!
            buf.rewind();
            if (buf.isDirect()) {
                while (buf.hasRemaining()) {
                    channel.write(buf);
                }
            } else {
                // if ByteBuffer is not direct we should use a temporary buffer
                // otherwise we'll get memory a big problem https://gist.github.com/2944942
                int c = buf.capacity();
                int BULK = 256 * 1024;
                int i = BULK;
                while (true) {
                    if (i >= c) {
                        buf.limit(c);
                        channel.write(buf);
                        break;
                    }

                    buf.limit(i);
                    channel.write(buf);
                    i += BULK;
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException("Couldn't write data", ex);
        } finally {
            Helper.close(channel);
        }
    }

    static boolean isFileMapped(ByteBuffer bb) {
        if (bb instanceof MappedByteBuffer) {
            try {
                ((MappedByteBuffer) bb).isLoaded();
                return true;
            } catch (UnsupportedOperationException ex) {
            }
        }
        return false;
    }

    public void flush() {
        if (dirName != null) {
            if (saveOnFlushOnly) {
                writeToDisc(getNodesFile(), nodes);
                writeToDisc(getEdgesFile(), edges);
            } else {
                if (nodes != null)
                    ((MappedByteBuffer) nodes).force();
                if (edges != null)
                    ((MappedByteBuffer) edges).force();
            }

            // store settings
            try {
                RandomAccessFile settingsFile = new RandomAccessFile(getSettingsFile(), "rw");
                settingsFile.writeInt(maxNodes);
                settingsFile.writeInt(currentNodeSize);
                settingsFile.writeInt(maxRecognizedNodeIndex);

                settingsFile.writeInt(nextEdgePosition);

                settingsFile.writeInt(edgeEmbedded);
                settingsFile.writeInt(edgeSize);
                settingsFile.writeInt(nodeCoreSize);
                settingsFile.writeInt(nodeSize);

                settingsFile.writeInt(edgeFlagsPos);
                settingsFile.writeInt(bytesEdgeSize);
            } catch (Exception ex) {
                logger.error("Problem while reading from settings file", ex);
            }
        }
    }

    public boolean loadSettings() {
        if (dirName == null)
            return false;

        try {
            File sFile = getSettingsFile();
            if (!sFile.exists())
                return false;

            RandomAccessFile settingsFile = new RandomAccessFile(sFile, "r");
            maxNodes = settingsFile.readInt();
            currentNodeSize = settingsFile.readInt();
            maxRecognizedNodeIndex = settingsFile.readInt();

            nextEdgePosition = settingsFile.readInt();

            edgeEmbedded = settingsFile.readInt();
            edgeSize = settingsFile.readInt();
            nodeCoreSize = settingsFile.readInt();
            nodeSize = settingsFile.readInt();

            edgeFlagsPos = settingsFile.readInt();
            bytesEdgeSize = settingsFile.readInt();
            saveOnFlushOnly = false;
            return true;
        } catch (Exception ex) {
            logger.error("Problem while reading from settings file", ex);
            return false;
        }
    }

    public void close() {
        if (dirName != null) {
            flush();
            if (nodes instanceof MappedByteBuffer)
                Helper.cleanMappedByteBuffer((MappedByteBuffer) nodes);
            if (nodeFile != null)
                Helper.close(nodeFile);

            if (edges instanceof MappedByteBuffer)
                Helper.cleanMappedByteBuffer((MappedByteBuffer) edges);
            if (edgeFile != null)
                Helper.close(edgeFile);
        }
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
