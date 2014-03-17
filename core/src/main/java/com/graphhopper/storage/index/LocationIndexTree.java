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
package com.graphhopper.storage.index;

import com.graphhopper.coll.GHBitSet;
import com.graphhopper.coll.GHTBitSet;
import com.graphhopper.geohash.SpatialKeyAlgo;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.procedure.TIntProcedure;
import gnu.trove.set.hash.TIntHashSet;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This implementation implements an n-tree to get node ids from GPS location. This replaces
 * Location2IDQuadtree except for cases when you only need rough precision or when you need better
 * support for out-of-bounds queries.
 * <p/>
 * All leafs are at the same depth, otherwise it is quite complicated to calculate the bresenham
 * line for different resolutions, especially if a leaf node could be split into a tree-node and
 * resolution changes.
 * <p/>
 * @author Peter Karich
 */
public class LocationIndexTree implements LocationIndex
{
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final int MAGIC_INT;
    protected static DistanceCalc distCalc = new DistancePlaneProjection();
    private DistanceCalc preciseDistCalc = new DistanceCalcEarth();
    protected final Graph graph;
    final DataAccess dataAccess;
    private int[] entries;
    private byte[] shifts;
    // convert spatial key to index for subentry of current depth
    private long[] bitmasks;
    protected SpatialKeyAlgo keyAlgo;
    private int minResolutionInMeter = 500;
    private double deltaLat;
    private double deltaLon;
    private int initSizeLeafEntries = 4;
    private boolean initialized = false;
    // do not start with 0 as a positive value means leaf and a negative means "entry with subentries"
    static final int START_POINTER = 1;
    private boolean regionSearch = true;
    /**
     * If normed distance is smaller than this value the node or edge is 'identical' and the
     * algorithm can stop search.
     */
    private double equalNormedDelta;

    public LocationIndexTree( Graph g, Directory dir )
    {
        MAGIC_INT = Integer.MAX_VALUE / 22316;
        this.graph = g;
        dataAccess = dir.find("locationIndex");
    }

    public int getMinResolutionInMeter()
    {
        return minResolutionInMeter;
    }

    /**
     * Minimum width in meter of one tile. Decrease this if you need faster queries, but keep in
     * mind that then queries with different coordinates are more likely to fail.
     */
    public LocationIndexTree setMinResolutionInMeter( int minResolutionInMeter )
    {
        this.minResolutionInMeter = minResolutionInMeter;
        return this;
    }

    /**
     * Searches also neighbouring quadtree entries to increase map matching precision.
     */
    public LocationIndexTree setSearchRegion( boolean regionAround )
    {
        this.regionSearch = regionAround;
        return this;
    }

    void prepareAlgo()
    {
        // 0.1 meter should count as 'equal'
        equalNormedDelta = distCalc.calcNormalizedDist(0.1);

        // now calculate the necessary maxDepth d for our current bounds
        // if we assume a minimum resolution like 0.5km for a leaf-tile                
        // n^(depth/2) = toMeter(dLon) / minResolution
        BBox bounds = graph.getBounds();
        if (graph.getNodes() == 0 || !bounds.check())
            throw new IllegalStateException("Bounds of graph are invalid: " + bounds);

        double lat = Math.min(Math.abs(bounds.maxLat), Math.abs(bounds.minLat));
        double maxDistInMeter = Math.max(
                (bounds.maxLat - bounds.minLat) / 360 * DistanceCalcEarth.C,
                (bounds.maxLon - bounds.minLon) / 360 * preciseDistCalc.calcCircumference(lat));
        double tmp = maxDistInMeter / minResolutionInMeter;
        tmp = tmp * tmp;
        TIntArrayList tmpEntries = new TIntArrayList();
        // the last one is always 4 to reduce costs if only a single entry
        tmp /= 4;
        while (tmp > 1)
        {
            int tmpNo;
            if (tmp >= 64)
            {
                tmpNo = 64;
            } else if (tmp >= 16)
            {
                tmpNo = 16;
            } else if (tmp >= 4)
            {
                tmpNo = 4;
            } else
            {
                break;
            }
            tmpEntries.add(tmpNo);
            tmp /= tmpNo;
        }
        tmpEntries.add(4);
        initEntries(tmpEntries.toArray());
        int shiftSum = 0;
        long parts = 1;
        for (int i = 0; i < shifts.length; i++)
        {
            shiftSum += shifts[i];
            parts *= entries[i];
        }
        if (shiftSum > 64)
            throw new IllegalStateException("sum of all shifts does not fit into a long variable");

        keyAlgo = new SpatialKeyAlgo(shiftSum).bounds(bounds);
        parts = Math.round(Math.sqrt(parts));
        deltaLat = (bounds.maxLat - bounds.minLat) / parts;
        deltaLon = (bounds.maxLon - bounds.minLon) / parts;
    }

    private LocationIndexTree initEntries( int[] entries )
    {
        if (entries.length < 1)
        // at least one depth should have been specified
        {
            throw new IllegalStateException("depth needs to be at least 1");
        }
        this.entries = entries;
        int depth = entries.length;
        shifts = new byte[depth];
        bitmasks = new long[depth];
        int lastEntry = entries[0];
        for (int i = 0; i < depth; i++)
        {
            if (lastEntry < entries[i])
            {
                throw new IllegalStateException("entries should decrease or stay but was:"
                        + Arrays.toString(entries));
            }
            lastEntry = entries[i];
            shifts[i] = getShift(entries[i]);
            bitmasks[i] = getBitmask(shifts[i]);
        }
        return this;
    }

    private byte getShift( int entries )
    {
        byte b = (byte) Math.round(Math.log(entries) / Math.log(2));
        if (b <= 0)
            throw new IllegalStateException("invalid shift:" + b);

        return b;
    }

    private long getBitmask( int shift )
    {
        long bm = (1 << shift) - 1;
        if (bm <= 0)
        {
            throw new IllegalStateException("invalid bitmask:" + bm);
        }
        return bm;
    }

    InMemConstructionIndex getPrepareInMemIndex()
    {
        InMemConstructionIndex memIndex = new InMemConstructionIndex(entries[0]);
        memIndex.prepare();
        return memIndex;
    }

    @Override
    public int findID( double lat, double lon )
    {
        QueryResult res = findClosest(lat, lon, EdgeFilter.ALL_EDGES);
        if (res == null)
            return -1;

        return res.getClosestNode();
    }

    @Override
    public boolean loadExisting()
    {
        if (initialized)
            throw new IllegalStateException("Call loadExisting only once");

        if (!dataAccess.loadExisting())
            return false;

        if (dataAccess.getHeader(0) != MAGIC_INT)
            throw new IllegalStateException("incorrect location2id index version, expected:" + MAGIC_INT);

        if (dataAccess.getHeader(1 * 4) != calcChecksum())
            throw new IllegalStateException("location2id index was opened with incorrect graph");

        setMinResolutionInMeter(dataAccess.getHeader(2 * 4));
        prepareAlgo();
        initialized = true;
        return true;
    }

    @Override
    public LocationIndex setResolution( int minResolutionInMeter )
    {
        if (minResolutionInMeter <= 0)
            throw new IllegalStateException("Negative precision is not allowed!");

        setMinResolutionInMeter(minResolutionInMeter);
        return this;
    }

    @Override
    public LocationIndex setApproximation( boolean approx )
    {
        if (approx)
            distCalc = new DistancePlaneProjection();
        else
            distCalc = new DistanceCalcEarth();
        return this;
    }

    @Override
    public LocationIndexTree create( long size )
    {
        throw new UnsupportedOperationException("Not supported. Use prepareIndex instead.");
    }

    @Override
    public void flush()
    {
        dataAccess.setHeader(0, MAGIC_INT);
        dataAccess.setHeader(1 * 4, calcChecksum());
        dataAccess.setHeader(2 * 4, minResolutionInMeter);

        // saving space not necessary: dataAccess.trimTo((lastPointer + 1) * 4);
        dataAccess.flush();
    }

    @Override
    public LocationIndex prepareIndex()
    {
        if (initialized)
            throw new IllegalStateException("Call prepareIndex only once");

        StopWatch sw = new StopWatch().start();
        prepareAlgo();
        // in-memory preparation
        InMemConstructionIndex inMem = getPrepareInMemIndex();

        // compact & store to dataAccess
        dataAccess.create(64 * 1024);
        int lastPointer = inMem.store(inMem.root, START_POINTER);
        flush();
        float entriesPerLeaf = (float) inMem.size / inMem.leafs;
        initialized = true;
        logger.info("location index created in " + sw.stop().getSeconds()
                + "s, size:" + Helper.nf(inMem.size)
                + ", leafs:" + Helper.nf(inMem.leafs)
                + ", precision:" + minResolutionInMeter
                + ", depth:" + entries.length
                + ", entries:" + Arrays.toString(entries)
                + ", entriesPerLeaf:" + entriesPerLeaf);

        return this;
    }

    int calcChecksum()
    {
        // do not include the edges as we could get problem with LevelGraph due to shortcuts
        // ^ graph.getAllEdges().count();
        return graph.getNodes();
    }

    @Override
    public void close()
    {
        dataAccess.close();
    }

    @Override
    public long getCapacity()
    {
        return dataAccess.getCapacity();
    }

    @Override
    public void setSegmentSize( int bytes )
    {
        dataAccess.setSegmentSize(bytes);
    }

    class InMemConstructionIndex
    {
        int size;
        int leafs;
        InMemTreeEntry root;

        public InMemConstructionIndex( int noOfSubEntries )
        {
            root = new InMemTreeEntry(noOfSubEntries);
        }

        void prepare()
        {
            final EdgeIterator allIter = getAllEdges();
            try
            {
                while (allIter.next())
                {
                    int nodeA = allIter.getBaseNode();
                    int nodeB = allIter.getAdjNode();
                    double lat1 = graph.getLatitude(nodeA);
                    double lon1 = graph.getLongitude(nodeA);
                    double lat2;
                    double lon2;
                    PointList points = allIter.fetchWayGeometry(0);
                    int len = points.getSize();
                    for (int i = 0; i < len; i++)
                    {
                        lat2 = points.getLatitude(i);
                        lon2 = points.getLongitude(i);
                        addNode(nodeA, nodeB, lat1, lon1, lat2, lon2);
                        lat1 = lat2;
                        lon1 = lon2;
                    }
                    lat2 = graph.getLatitude(nodeB);
                    lon2 = graph.getLongitude(nodeB);
                    addNode(nodeA, nodeB, lat1, lon1, lat2, lon2);
                }
            } catch (Exception ex)
            {
//                logger.error("Problem!", ex);
                logger.error("Problem! base:" + allIter.getBaseNode() + ", adj:" + allIter.getAdjNode()
                        + ", edge:" + allIter.getEdge(), ex);
            }
        }

        void addNode( final int nodeA, final int nodeB,
                final double lat1, final double lon1,
                final double lat2, final double lon2 )
        {
            PointEmitter pointEmitter = new PointEmitter()
            {
                @Override
                public void set( double lat, double lon )
                {
                    long key = keyAlgo.encode(lat, lon);
                    long keyPart = createReverseKey(key);
                    // no need to feed both nodes as we search neighbors in fillIDs
                    addNode(root, pickBestNode(nodeA, nodeB), 0, keyPart, key);
                }
            };
            BresenhamLine.calcPoints(lat1, lon1, lat2, lon2, pointEmitter,
                    graph.getBounds().minLat, graph.getBounds().minLon,
                    deltaLat, deltaLon);
        }

        void addNode( InMemEntry entry, int nodeId, int depth, long keyPart, long key )
        {
            if (entry.isLeaf())
            {
                InMemLeafEntry leafEntry = (InMemLeafEntry) entry;
                leafEntry.addNode(nodeId);
            } else
            {
                int index = (int) (bitmasks[depth] & keyPart);
                keyPart = keyPart >>> shifts[depth];
                InMemTreeEntry treeEntry = ((InMemTreeEntry) entry);
                InMemEntry subentry = treeEntry.getSubEntry(index);
                depth++;
                if (subentry == null)
                {
                    if (depth == entries.length)
                    {
                        subentry = new InMemLeafEntry(initSizeLeafEntries, key);
                    } else
                    {
                        subentry = new InMemTreeEntry(entries[depth]);
                    }
                    treeEntry.setSubEntry(index, subentry);
                }

                addNode(subentry, nodeId, depth, keyPart, key);
            }
        }

        Collection<InMemEntry> getEntriesOf( int selectDepth )
        {
            List<InMemEntry> list = new ArrayList<InMemEntry>();
            fillLayer(list, selectDepth, 0, ((InMemTreeEntry) root).getSubEntriesForDebug());
            return list;
        }

        void fillLayer( Collection<InMemEntry> list, int selectDepth, int depth, Collection<InMemEntry> entries )
        {
            for (InMemEntry entry : entries)
            {
                if (selectDepth == depth)
                {
                    list.add(entry);
                } else if (entry instanceof InMemTreeEntry)
                {
                    fillLayer(list, selectDepth, depth + 1, ((InMemTreeEntry) entry).getSubEntriesForDebug());
                }
            }
        }

        String print()
        {
            StringBuilder sb = new StringBuilder();
            print(root, sb, 0, 0);
            return sb.toString();
        }

        void print( InMemEntry e, StringBuilder sb, long key, int depth )
        {
            if (e.isLeaf())
            {
                InMemLeafEntry leaf = (InMemLeafEntry) e;
                int bits = keyAlgo.getBits();
                // print reverse keys
                sb.append(BitUtil.BIG.toBitString(BitUtil.BIG.reverse(key, bits), bits)).append("  ");
                TIntArrayList entries = leaf.getResults();
                for (int i = 0; i < entries.size(); i++)
                {
                    sb.append(leaf.get(i)).append(',');
                }
                sb.append('\n');
            } else
            {
                InMemTreeEntry tree = (InMemTreeEntry) e;
                key = key << shifts[depth];
                for (int counter = 0; counter < tree.subEntries.length; counter++)
                {
                    InMemEntry sube = tree.subEntries[counter];
                    if (sube != null)
                    {
                        print(sube, sb, key | counter, depth + 1);
                    }
                }
            }
        }

        // store and freezes tree
        int store( InMemEntry entry, int intIndex )
        {
            long refPointer = (long) intIndex * 4;
            if (entry.isLeaf())
            {
                InMemLeafEntry leaf = ((InMemLeafEntry) entry);
                TIntArrayList entries = leaf.getResults();
                int len = entries.size();
                if (len == 0)
                {
                    return intIndex;
                }
                size += len;
                intIndex++;
                leafs++;
                dataAccess.incCapacity((long) (intIndex + len + 1) * 4);
                if (len == 1)
                {
                    // less disc space for single entries
                    dataAccess.setInt(refPointer, -entries.get(0) - 1);
                } else
                {
                    for (int index = 0; index < len; index++, intIndex++)
                    {
                        dataAccess.setInt((long) intIndex * 4, entries.get(index));
                    }
                    dataAccess.setInt(refPointer, intIndex);
                }
            } else
            {
                InMemTreeEntry treeEntry = ((InMemTreeEntry) entry);
                int len = treeEntry.subEntries.length;
                intIndex += len;
                for (int subCounter = 0; subCounter < len; subCounter++, refPointer += 4)
                {
                    InMemEntry subEntry = treeEntry.subEntries[subCounter];
                    if (subEntry == null)
                    {
                        continue;
                    }
                    dataAccess.incCapacity((long) (intIndex + 1) * 4);
                    int beforeIntIndex = intIndex;
                    intIndex = store(subEntry, beforeIntIndex);
                    if (intIndex == beforeIntIndex)
                    {
                        dataAccess.setInt(refPointer, 0);
                    } else
                    {
                        dataAccess.setInt(refPointer, beforeIntIndex);
                    }
                }
            }
            return intIndex;
        }
    }

    TIntArrayList getEntries()
    {
        return new TIntArrayList(entries);
    }

    // fillIDs according to how they are stored
    void fillIDs( long keyPart, int intIndex, TIntHashSet set, int depth )
    {
        long pointer = (long) intIndex << 2;
        if (depth == entries.length)
        {
            int value = dataAccess.getInt(pointer);
            if (value < 0)
            // single data entries (less disc space)            
            {
                set.add(-(value + 1));
            } else
            {
                long max = (long) value * 4;
                // leaf entry => value is maxPointer
                for (long leafIndex = pointer + 4; leafIndex < max; leafIndex += 4)
                {
                    set.add(dataAccess.getInt(leafIndex));
                }
            }
            return;
        }
        int offset = (int) (bitmasks[depth] & keyPart) << 2;
        int value = dataAccess.getInt(pointer + offset);
        if (value > 0)
        {
            // tree entry => negative value points to subentries
            fillIDs(keyPart >>> shifts[depth], value, set, depth + 1);
        }
    }

    // this method returns the spatial key in reverse order for easier right-shifting
    final long createReverseKey( double lat, double lon )
    {
        return BitUtil.BIG.reverse(keyAlgo.encode(lat, lon), keyAlgo.getBits());
    }

    final long createReverseKey( long key )
    {
        return BitUtil.BIG.reverse(key, keyAlgo.getBits());
    }

    protected TIntHashSet findNetworkEntries( double queryLat, double queryLon )
    {
        TIntHashSet storedNetworkEntryIds = new TIntHashSet();
        if (regionSearch)
        {
            // search all rasters around minResolutionInMeter as we did not fill empty entries
            double maxLat = queryLat + 1.5 * deltaLat;
            double maxLon = queryLon + 1.5 * deltaLon;
            for (double tmpLat = queryLat - deltaLat; tmpLat < maxLat; tmpLat += deltaLat)
            {
                for (double tmpLon = queryLon - deltaLon; tmpLon < maxLon; tmpLon += deltaLon)
                {
                    long keyPart = createReverseKey(tmpLat, tmpLon);
                    // System.out.println(BitUtilLittle.toBitString(key, keyAlgo.bits()));
                    fillIDs(keyPart, START_POINTER, storedNetworkEntryIds, 0);
                }
            }
        } else
        {
            long keyPart = createReverseKey(queryLat, queryLon);
            fillIDs(keyPart, START_POINTER, storedNetworkEntryIds, 0);
        }
        return storedNetworkEntryIds;
    }

    @Override
    public QueryResult findClosest( final double queryLat, final double queryLon,
            final EdgeFilter edgeFilter )
    {
        final TIntHashSet storedNetworkEntryIds = findNetworkEntries(queryLat, queryLon);
        final QueryResult closestMatch = new QueryResult(queryLat, queryLon);
        if (storedNetworkEntryIds.isEmpty())
            return closestMatch;

        // clone storedIds to avoid interference with forEach
        final GHBitSet checkBitset = new GHTBitSet(new TIntHashSet(storedNetworkEntryIds));
        // find nodes from the network entries which are close to 'point'
        final EdgeExplorer explorer = graph.createEdgeExplorer(getEdgeFilter());
        storedNetworkEntryIds.forEach(new TIntProcedure()
        {
            @Override
            public boolean execute( final int networkEntryNodeId )
            {
                new XFirstSearchCheck(queryLat, queryLon, checkBitset, edgeFilter)
                {
                    @Override
                    protected double getQueryDistance()
                    {
                        return closestMatch.getQueryDistance();
                    }

                    @Override
                    protected boolean check( int node, double normedDist, int wayIndex, EdgeIteratorState edge, QueryResult.Position pos )
                    {
                        if (normedDist < closestMatch.getQueryDistance())
                        {
                            closestMatch.setQueryDistance(normedDist);
                            closestMatch.setClosestNode(node);
                            closestMatch.setClosestEdge(edge.detach(false));
                            closestMatch.setWayIndex(wayIndex);
                            closestMatch.setSnappedPosition(pos);
                            return true;
                        }
                        return false;
                    }
                }.start(explorer, networkEntryNodeId, false);
                return true;
            }
        });

        if (closestMatch.isValid())
        {
            // denormalize distance            
            closestMatch.setQueryDistance(distCalc.calcDenormalizedDist(closestMatch.getQueryDistance()));
            closestMatch.calcSnappedPoint(distCalc);
        }

        return closestMatch;
    }

    /**
     * Make it possible to collect nearby location also for other purposes.
     */
    protected abstract class XFirstSearchCheck extends XFirstSearch
    {
        boolean goFurther = true;
        double currNormedDist;
        double currLat;
        double currLon;
        int currNode;
        final double queryLat;
        final double queryLon;
        final GHBitSet checkBitset;
        final EdgeFilter edgeFilter;

        public XFirstSearchCheck( double queryLat, double queryLon, GHBitSet checkBitset, EdgeFilter edgeFilter )
        {
            this.queryLat = queryLat;
            this.queryLon = queryLon;
            this.checkBitset = checkBitset;
            this.edgeFilter = edgeFilter;
        }

        @Override
        protected GHBitSet createBitSet()
        {
            return checkBitset;
        }

        @Override
        protected boolean goFurther( int baseNode )
        {
            currNode = baseNode;
            currLat = graph.getLatitude(baseNode);
            currLon = graph.getLongitude(baseNode);
            currNormedDist = distCalc.calcNormalizedDist(queryLat, queryLon, currLat, currLon);
            return goFurther;
        }

        @Override
        protected boolean checkAdjacent( EdgeIteratorState currEdge )
        {
            goFurther = false;
            if (!edgeFilter.accept(currEdge))
            {
                // only limit the adjNode to a certain radius as currNode could be the wrong side of a valid edge
                // goFurther = currDist < minResolution2InMeterNormed;
                return true;
            }

            int tmpClosestNode = currNode;
            if (check(tmpClosestNode, currNormedDist, 0, currEdge, QueryResult.Position.TOWER))
            {
                if (currNormedDist <= equalNormedDelta)
                    return false;
            }

            int adjNode = currEdge.getAdjNode();
            double adjLat = graph.getLatitude(adjNode);
            double adjLon = graph.getLongitude(adjNode);
            double adjDist = distCalc.calcNormalizedDist(adjLat, adjLon, queryLat, queryLon);
            // if there are wayPoints this is only an approximation
            if (adjDist < currNormedDist)
                tmpClosestNode = adjNode;

            double tmpLat = currLat;
            double tmpLon = currLon;
            double tmpNormedDist;
            PointList pointList = currEdge.fetchWayGeometry(2);
            int len = pointList.getSize();
            for (int pointIndex = 0; pointIndex < len; pointIndex++)
            {
                double wayLat = pointList.getLatitude(pointIndex);
                double wayLon = pointList.getLongitude(pointIndex);
                QueryResult.Position pos = QueryResult.Position.EDGE;
                if (distCalc.validEdgeDistance(queryLat, queryLon, tmpLat, tmpLon, wayLat, wayLon))
                {
                    tmpNormedDist = distCalc.calcNormalizedEdgeDistance(queryLat, queryLon,
                            tmpLat, tmpLon, wayLat, wayLon);
                    check(tmpClosestNode, tmpNormedDist, pointIndex, currEdge, pos);
                } else if (pointIndex + 1 == len)
                {
                    tmpNormedDist = adjDist;
                    pos = QueryResult.Position.TOWER;
                } else
                {
                    tmpNormedDist = distCalc.calcNormalizedDist(queryLat, queryLon, wayLat, wayLon);
                    pos = QueryResult.Position.PILLAR;
                }
                check(tmpClosestNode, tmpNormedDist, pointIndex + 1, currEdge, pos);

                if (tmpNormedDist <= equalNormedDelta)
                    return false;

                tmpLat = wayLat;
                tmpLon = wayLon;
            }
            return getQueryDistance() > equalNormedDelta;
        }

        protected abstract double getQueryDistance();

        protected abstract boolean check( int node, double normedDist, int wayIndex, EdgeIteratorState iter, QueryResult.Position pos );
    }

    protected int pickBestNode( int nodeA, int nodeB )
    {
        // For normal graph the node does not matter because if nodeA is conntected to nodeB
        // then nodeB is also connect to nodeA, but for a LevelGraph this does not apply.
        return nodeA;
    }

    protected EdgeFilter getEdgeFilter()
    {
        return EdgeFilter.ALL_EDGES;
    }

    protected AllEdgesIterator getAllEdges()
    {
        return graph.getAllEdges();
    }

    // make entries static as otherwise we get an additional reference to this class (memory waste)
    static interface InMemEntry
    {
        boolean isLeaf();
    }

    static class InMemLeafEntry extends SortedIntSet implements InMemEntry
    {
        private long key;

        public InMemLeafEntry( int count, long key )
        {
            super(count);
            this.key = key;
        }

        public boolean addNode( int nodeId )
        {
            return addOnce(nodeId);
        }

        @Override
        public final boolean isLeaf()
        {
            return true;
        }

        @Override
        public String toString()
        {
            return "LEAF " + key + " " + super.toString();
        }

        TIntArrayList getResults()
        {
            return this;
        }
    }

    // Space efficient sorted integer set. Suited for only a few entries.
    static class SortedIntSet extends TIntArrayList
    {
        public SortedIntSet()
        {
        }

        public SortedIntSet( int capacity )
        {
            super(capacity);
        }

        /**
         * Allow adding a value only once
         */
        public boolean addOnce( int value )
        {
            int foundIndex = binarySearch(value);
            if (foundIndex >= 0)
            {
                return false;
            }
            foundIndex = -foundIndex - 1;
            insert(foundIndex, value);
            return true;
        }
    }

    static class InMemTreeEntry implements InMemEntry
    {
        InMemEntry[] subEntries;

        public InMemTreeEntry( int subEntryNo )
        {
            subEntries = new InMemEntry[subEntryNo];
        }

        public InMemEntry getSubEntry( int index )
        {
            return subEntries[index];
        }

        public void setSubEntry( int index, InMemEntry subEntry )
        {
            this.subEntries[index] = subEntry;
        }

        public Collection<InMemEntry> getSubEntriesForDebug()
        {
            List<InMemEntry> list = new ArrayList<InMemEntry>();
            for (InMemEntry e : subEntries)
            {
                if (e != null)
                {
                    list.add(e);
                }
            }
            return list;
        }

        @Override
        public final boolean isLeaf()
        {
            return false;
        }

        @Override
        public String toString()
        {
            return "TREE";
        }
    }
}
