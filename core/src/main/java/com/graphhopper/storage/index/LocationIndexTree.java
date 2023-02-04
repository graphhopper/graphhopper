/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
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

import com.graphhopper.core.util.PointList;
import com.graphhopper.core.util.Helper;
import com.carrotsearch.hppc.IntHashSet;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.shapes.BBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import static com.graphhopper.util.DistancePlaneProjection.DIST_PLANE;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.StopWatch;

/**
 * This class implements a Quadtree to get the closest node or edge from GPS coordinates.
 * The following properties are different to an ordinary implementation:
 * <ol>
 * <li>To reduce overall size it can use 16 instead of just 4 cell if required</li>
 * <li>Still all leafs are at the same depth, otherwise it is too complicated to calculate the Bresenham line for different
 * resolutions, especially if a leaf node could be split into a tree-node and resolution changes.</li>
 * <li>To further reduce size this Quadtree avoids storing the bounding box of every cell and calculates this per request instead.</li>
 * <li>To simplify this querying and avoid a slow down for the most frequent queries ala "lat,lon" it encodes the point
 * into a spatial key {@see SpatialKeyAlgo} and can the use the resulting raw bits as cell index to recurse
 * into the subtrees. E.g. if there are 3 layers with 16, 4 and 4 cells each, then the spatial key has
 * three parts: 4 bits for the cellIndex into the 16 cells, 2 bits for the next layer and 2 bits for the last layer.</li>
 * <li>An array structure (DataAccess) is internally used and stores the offset to the next cell.
 * E.g. in case of 4 cells, the offset is 0,1,2 or 3. Except when the leaf-depth is reached, then the value
 * is the number of node IDs stored in the cell or, if negative, just a single node ID.</li>
 * </ol>
 *
 * @author Peter Karich
 */
public class LocationIndexTree implements LocationIndex {
    private final Directory directory;
    private final Graph graph;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final NodeAccess nodeAccess;
    private int maxRegionSearch = 4;
    private int minResolutionInMeter = 300;
    private boolean initialized = false;

    LineIntIndex lineIntIndex;

    /**
     * If normed distance is smaller than this value the node or edge is 'identical' and the
     * algorithm can stop search.
     */
    private final double equalNormedDelta = DIST_PLANE.calcNormalizedDist(0.1); // 0.1 meters
    private IndexStructureInfo indexStructureInfo;

    /**
     * @param g the graph for which this index should do the lookup based on latitude,longitude.
     */
    public LocationIndexTree(Graph g, Directory dir) {
        this.graph = g;
        this.nodeAccess = g.getNodeAccess();
        this.directory = dir;

        // Clone this defensively -- In case something funny happens and things get added to the Graph after
        // this index is built. Reason is that the expected structure of the index is a function of the bbox, so we
        // need it to be immutable.
        BBox bounds = graph.getBounds().clone();

        // I want to be able to create a location index for the empty graph without error, but for that
        // I need valid bounds so that the initialization logic works.
        if (!bounds.isValid())
            bounds = new BBox(-10.0, 10.0, -10.0, 10.0);

        lineIntIndex = new LineIntIndex(bounds, directory, "location_index");
    }

    public int getMinResolutionInMeter() {
        return minResolutionInMeter;
    }

    /**
     * Minimum width in meter of one tile. Decrease this if you need faster queries, but keep in
     * mind that then queries with different coordinates are more likely to fail.
     */
    public LocationIndexTree setMinResolutionInMeter(int minResolutionInMeter) {
        this.minResolutionInMeter = minResolutionInMeter;
        return this;
    }

    /**
     * Searches also neighbouring tiles until the maximum distance from the query point is reached
     * (minResolutionInMeter*regionAround). Set to 1 to only search one tile. Good if you
     * have strict performance requirements and want the search to terminate early, and you can tolerate
     * that edges that may be in neighboring tiles are not found. Default is 4, which means approximately
     * that a square of three tiles upwards, downwards, leftwards and rightwards from the tile the query tile
     * is in is searched.
     */
    public LocationIndexTree setMaxRegionSearch(int numTiles) {
        if (numTiles < 1)
            throw new IllegalArgumentException("Region of location index must be at least 1 but was " + numTiles);
        this.maxRegionSearch = numTiles;
        return this;
    }


    public LocationIndex setResolution(int minResolutionInMeter) {
        if (minResolutionInMeter <= 0)
            throw new IllegalStateException("Negative precision is not allowed!");

        setMinResolutionInMeter(minResolutionInMeter);
        return this;
    }

    public boolean loadExisting() {
        if (!lineIntIndex.loadExisting())
            return false;

        if (lineIntIndex.getChecksum() != checksum())
            throw new IllegalStateException("location index was opened with incorrect graph: "
                    + lineIntIndex.getChecksum() + " vs. " + checksum());
        minResolutionInMeter = lineIntIndex.getMinResolutionInMeter();
        indexStructureInfo = IndexStructureInfo.create(graph.getBounds(), minResolutionInMeter);
        initialized = true;
        return true;
    }

    public void flush() {
        lineIntIndex.flush();
    }

    public LocationIndex prepareIndex() {
        return prepareIndex(EdgeFilter.ALL_EDGES);
    }

    public LocationIndex prepareIndex(EdgeFilter edgeFilter) {
        if (initialized)
            throw new IllegalStateException("Call prepareIndex only once");

        StopWatch sw = new StopWatch().start();

        // Clone this defensively -- In case something funny happens and things get added to the Graph after
        // this index is built. Reason is that the expected structure of the index is a function of the bbox, so we
        // need it to be immutable.
        BBox bounds = graph.getBounds().clone();

        // I want to be able to create a location index for the empty graph without error, but for that
        // I need valid bounds so that the initialization logic works.
        if (!bounds.isValid())
            bounds = new BBox(-10.0, 10.0, -10.0, 10.0);

        InMemConstructionIndex inMemConstructionIndex = prepareInMemConstructionIndex(bounds, edgeFilter);

        lineIntIndex.setMinResolutionInMeter(minResolutionInMeter);
        lineIntIndex.store(inMemConstructionIndex);
        lineIntIndex.setChecksum(checksum());
        flush();
        logger.info("location index created in " + sw.stop().getSeconds()
                + "s, size:" + Helper.nf(lineIntIndex.getSize())
                + ", leafs:" + Helper.nf(lineIntIndex.getLeafs())
                + ", precision:" + minResolutionInMeter
                + ", depth:" + indexStructureInfo.getEntries().length
                + ", checksum:" + checksum()
                + ", entries:" + Arrays.toString(indexStructureInfo.getEntries())
                + ", entriesPerLeaf:" + (float) lineIntIndex.getSize() / lineIntIndex.getLeafs());

        return this;
    }

    InMemConstructionIndex prepareInMemConstructionIndex(BBox bounds, EdgeFilter edgeFilter) {
        indexStructureInfo = IndexStructureInfo.create(bounds, minResolutionInMeter);
        InMemConstructionIndex inMem = new InMemConstructionIndex(indexStructureInfo);
        AllEdgesIterator allIter = graph.getAllEdges();
        try {
            while (allIter.next()) {
                if (!edgeFilter.accept(allIter))
                    continue;
                int edge = allIter.getEdge();
                int nodeA = allIter.getBaseNode();
                int nodeB = allIter.getAdjNode();
                double lat1 = nodeAccess.getLat(nodeA);
                double lon1 = nodeAccess.getLon(nodeA);
                double lat2;
                double lon2;
                PointList points = allIter.fetchWayGeometry(FetchMode.PILLAR_ONLY);
                int len = points.size();
                for (int i = 0; i < len; i++) {
                    lat2 = points.getLat(i);
                    lon2 = points.getLon(i);
                    inMem.addToAllTilesOnLine(edge, lat1, lon1, lat2, lon2);
                    lat1 = lat2;
                    lon1 = lon2;
                }
                lat2 = nodeAccess.getLat(nodeB);
                lon2 = nodeAccess.getLon(nodeB);
                inMem.addToAllTilesOnLine(edge, lat1, lon1, lat2, lon2);
            }
        } catch (Exception ex1) {
            logger.error("Problem! base:" + allIter.getBaseNode() + ", adj:" + allIter.getAdjNode()
                    + ", edge:" + allIter.getEdge(), ex1);
        }
        return inMem;
    }

    int checksum() {
        return graph.getNodes() ^ graph.getAllEdges().length();
    }

    public void close() {
        lineIntIndex.close();
    }

    public boolean isClosed() {
        return lineIntIndex.isClosed();
    }

    public long getCapacity() {
        return lineIntIndex.getCapacity();
    }

    /**
     * Calculates the distance to the nearest tile border, where the tile border is the rectangular
     * region with dimension 2*paddingTiles + 1 and where the center tile contains the given lat/lon
     * coordinate
     */
    final double calculateRMin(double lat, double lon, int paddingTiles) {
        int x = indexStructureInfo.getKeyAlgo().x(lon);
        int y = indexStructureInfo.getKeyAlgo().y(lat);

        double minLat = graph.getBounds().minLat + (y - paddingTiles) * indexStructureInfo.getDeltaLat();
        double maxLat = graph.getBounds().minLat + (y + paddingTiles + 1) * indexStructureInfo.getDeltaLat();
        double minLon = graph.getBounds().minLon + (x - paddingTiles) * indexStructureInfo.getDeltaLon();
        double maxLon = graph.getBounds().minLon + (x + paddingTiles + 1) * indexStructureInfo.getDeltaLon();

        double dSouthernLat = lat - minLat;
        double dNorthernLat = maxLat - lat;
        double dWesternLon = lon - minLon;
        double dEasternLon = maxLon - lon;

        // convert degree deltas into a radius in meter
        double dMinLat, dMinLon;
        if (dSouthernLat < dNorthernLat) {
            dMinLat = DIST_PLANE.calcDist(lat, lon, minLat, lon);
        } else {
            dMinLat = DIST_PLANE.calcDist(lat, lon, maxLat, lon);
        }

        if (dWesternLon < dEasternLon) {
            dMinLon = DIST_PLANE.calcDist(lat, lon, lat, minLon);
        } else {
            dMinLon = DIST_PLANE.calcDist(lat, lon, lat, maxLon);
        }

        return Math.min(dMinLat, dMinLon);
    }

    @Override
    public Snap findClosest(final double queryLat, final double queryLon, final EdgeFilter edgeFilter) {
        if (isClosed())
            throw new IllegalStateException("You need to create a new LocationIndex instance as it is already closed");

        final Snap closestMatch = new Snap(queryLat, queryLon);
        IntHashSet seenEdges = new IntHashSet();
        for (int iteration = 0; iteration < maxRegionSearch; iteration++) {
            lineIntIndex.findEdgeIdsInNeighborhood(queryLat, queryLon, iteration, edgeId -> {
                EdgeIteratorState edgeIteratorState = graph.getEdgeIteratorStateForKey(edgeId * 2);
                if (seenEdges.add(edgeId) && edgeFilter.accept(edgeIteratorState)) { // TODO: or reverse?
                    traverseEdge(queryLat, queryLon, edgeIteratorState, (node, normedDist, wayIndex, pos) -> {
                        if (normedDist < closestMatch.getQueryDistance()) {
                            closestMatch.setQueryDistance(normedDist);
                            closestMatch.setClosestNode(node);
                            closestMatch.setClosestEdge(edgeIteratorState.detach(false));
                            closestMatch.setWayIndex(wayIndex);
                            closestMatch.setSnappedPosition(pos);
                        }
                    });
                }
            });
            if (closestMatch.isValid()) {
                // Check if we can stop...
                double rMin = calculateRMin(queryLat, queryLon, iteration);
                double minDistance = DIST_PLANE.calcDenormalizedDist(closestMatch.getQueryDistance());
                if (minDistance < rMin) {
                    break; // We can (approximately?) guarantee that no closer edges are anywhere else
                }
            }
        }

        if (closestMatch.isValid()) {
            closestMatch.setQueryDistance(DIST_PLANE.calcDenormalizedDist(closestMatch.getQueryDistance()));
            closestMatch.calcSnappedPoint(DIST_PLANE);
        }
        return closestMatch;
    }

    @Override
    public void query(BBox queryBBox, Visitor function) {
        lineIntIndex.query(queryBBox, function);
    }

    public interface EdgeCheck {
        void check(int node, double normedDist, int wayIndex, Snap.Position pos);
    }

    public void traverseEdge(double queryLat, double queryLon, EdgeIteratorState currEdge, EdgeCheck edgeCheck) {
        int baseNode = currEdge.getBaseNode();
        double baseLat = nodeAccess.getLat(baseNode);
        double baseLon = nodeAccess.getLon(baseNode);
        double baseDist = DIST_PLANE.calcNormalizedDist(queryLat, queryLon, baseLat, baseLon);

        int adjNode = currEdge.getAdjNode();
        double adjLat = nodeAccess.getLat(adjNode);
        double adjLon = nodeAccess.getLon(adjNode);
        double adjDist = DIST_PLANE.calcNormalizedDist(queryLat, queryLon, adjLat, adjLon);

        PointList pointList = currEdge.fetchWayGeometry(FetchMode.PILLAR_AND_ADJ);
        final int len = pointList.size();

        int closestTowerNode;
        double closestDist;
        if (baseDist < adjDist) {
            closestTowerNode = baseNode;
            closestDist = baseDist;
            edgeCheck.check(baseNode, baseDist, 0, Snap.Position.TOWER);
        } else {
            closestTowerNode = adjNode;
            closestDist = adjDist;
            edgeCheck.check(adjNode, adjDist, len, Snap.Position.TOWER);
        }
        if (closestDist <= equalNormedDelta)
            // if a tower node is close to the query point we stop
            return;

        double lastLat = baseLat;
        double lastLon = baseLon;
        for (int i = 0; i < len; i++) {
            double lat = pointList.getLat(i);
            double lon = pointList.getLon(i);
            if (DIST_PLANE.isCrossBoundary(lastLon, lon)) {
                lastLat = lat;
                lastLon = lon;
                continue;
            }

            // +1 because we skipped the base node
            final int indexInFullPointList = i + 1;
            if (DIST_PLANE.validEdgeDistance(queryLat, queryLon, lastLat, lastLon, lat, lon)) {
                closestDist = DIST_PLANE.calcNormalizedEdgeDistance(queryLat, queryLon, lastLat, lastLon, lat, lon);
                edgeCheck.check(closestTowerNode, closestDist, indexInFullPointList - 1, Snap.Position.EDGE);
            } else if (i < len - 1) {
                closestDist = DIST_PLANE.calcNormalizedDist(queryLat, queryLon, lat, lon);
                edgeCheck.check(closestTowerNode, closestDist, indexInFullPointList, Snap.Position.PILLAR);
            } else {
                // we snapped onto the last tower node, but we already handled this before so do nothing
            }
            if (closestDist <= equalNormedDelta)
                return;
            lastLat = lat;
            lastLon = lon;
        }
    }

}
