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
package com.graphhopper.reader;

import com.graphhopper.coll.GHLongIntBTree;
import com.graphhopper.coll.LongIntMap;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.Helper;
import static com.graphhopper.util.Helper.*;
import com.graphhopper.util.StopWatch;
import java.io.*;
import javax.xml.stream.XMLStreamException;

import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class parses an OSM xml file and creates a graph from it. See run.sh on how to use it from
 * command line.
 * <p/>
 * @author Peter Karich
 */
public class OSMReader
{
    private static Logger logger = LoggerFactory.getLogger(OSMReader.class);
    private long locations;
    private long skippedLocations;
    private GraphStorage graphStorage;
    private OSMReaderHelper helper;
    private EncodingManager encodingManager = null;
    private int workerThreads = -1;
    private LongIntMap osmNodeIdToBarrierMap;
    private boolean enableInstructions = true;

    public OSMReader( GraphStorage storage, long expectedCap )
    {
        this.graphStorage = storage;
        helper = new OSMReaderHelper(graphStorage, expectedCap);
        osmNodeIdToBarrierMap = new GHLongIntBTree(200);
    }

    public OSMReader setWorkerThreads( int numOfWorkers )
    {
        this.workerThreads = numOfWorkers;
        return this;
    }

    public void doOSM2Graph( File osmFile ) throws IOException
    {
        if (encodingManager == null)
        {
            throw new IllegalStateException("Encoding manager not set.");
        }

        StopWatch sw1 = new StopWatch().start();
        preProcess(osmFile);

        sw1.stop();
        StopWatch sw2 = new StopWatch().start();
        writeOsm2Graph(osmFile);
        sw2.stop();

        logger.info("time(pass1): " + (int) sw1.getSeconds() + " pass2: " + (int) sw2.getSeconds()
                + " total:" + ((int) (sw1.getSeconds() + sw2.getSeconds())));
    }

    /**
     * Preprocessing of OSM file to select nodes which are used for highways. This allows a more
     * compact graph data structure.
     */
    public void preProcess( File osmFile )
    {
        try
        {
            OSMInputFile in = new OSMInputFile(osmFile).setWorkerThreads(workerThreads).open();

            long tmpCounter = 1;

            OSMElement item;
            while ((item = in.getNext()) != null)
            {
                if (item.isType(OSMElement.WAY))
                {
                    final OSMWay way = (OSMWay) item;
                    boolean valid = filterWay(way);
                    if (valid)
                    {
                        TLongList wayNodes = way.getNodes();
                        int s = wayNodes.size();
                        for (int index = 0; index < s; index++)
                        {
                            helper.prepareHighwayNode(wayNodes.get(index));
                        }

                        if (++tmpCounter % 500000 == 0)
                        {
                            logger.info(nf(tmpCounter) + " (preprocess), osmIdMap:"
                                    + nf(helper.getNodeMap().getSize()) + " (" + helper.getNodeMap().getMemoryUsage() + "MB) "
                                    + Helper.getMemInfo());
                        }
                    }
                }
            }
            in.close();
        } catch (Exception ex)
        {
            throw new RuntimeException("Problem while parsing file", ex);
        }
    }

    /**
     * Filter ways but do not analyze properties wayNodes will be filled with participating node
     * ids.
     * <p/>
     * @return true the current xml entry is a way entry and has nodes
     */
    boolean filterWay( OSMWay item ) throws XMLStreamException
    {

        // ignore broken geometry
        if (item.getNodes().size() < 2)
        {
            return false;
        }
        // ignore multipolygon geometry
        if (!item.hasTags())
        {
            return false;
        }

        return encodingManager.accept(item) > 0;
    }

    /**
     * Creates the edges and nodes files from the specified osm file.
     */
    private void writeOsm2Graph( File osmFile )
    {

        int tmp = (int) Math.max(helper.getFoundNodes() / 50, 100);
        logger.info("creating graph. Found nodes (pillar+tower):" + nf(helper.getFoundNodes()) + ", " + Helper.getMemInfo());
        graphStorage.create(tmp);
        long wayStart = -1;
        long counter = 1;
        try
        {
            OSMInputFile in = new OSMInputFile(osmFile).setWorkerThreads(workerThreads).open();
            LongIntMap nodeFilter = helper.getNodeMap();

            OSMElement item;
            while ((item = in.getNext()) != null)
            {
                switch (item.getType())
                {
                    case OSMElement.NODE:
                        if (nodeFilter.get(item.getId()) != -1)
                        {
                            processNode((OSMNode) item);
                        }
                        break;

                    case OSMElement.WAY:
                        if (wayStart < 0)
                        {
                            logger.info(nf(counter) + ", now parsing ways");
                            wayStart = counter;
                        }
                        processWay((OSMWay) item);
                        break;
                }
                if (++counter % 5000000 == 0)
                {
                    logger.info(nf(counter) + ", locs:" + nf(locations)
                            + " (" + skippedLocations + ") " + Helper.getMemInfo());
                }
            }
            in.close();
            // logger.info("storage nodes:" + storage.nodes() + " vs. graph nodes:" + storage.getGraph().nodes());
        } catch (Exception ex)
        {
            throw new RuntimeException("Couldn't process file " + osmFile, ex);
        }
        helper.finishedReading();
        if (graphStorage.getNodes() == 0)
        {
            throw new IllegalStateException("osm must not be empty. read " + counter + " lines and " + locations + " locations");
        }
    }

    /**
     * Process properties, encode flags and create edges for the way
     * <p/>
     * @param way
     * @throws XMLStreamException
     */
    public void processWay( OSMWay way ) throws XMLStreamException
    {
        if (way.getNodes().size() < 2)
        {
            return;
        }
        // ignore multipolygon geometry
        if (!way.hasTags())
        {
            return;
        }

        int includeWay = encodingManager.accept(way);
        if (includeWay == 0)
        {
            return;
        }

        int flags = encodingManager.encodeTags(includeWay, way);
        if (flags == 0)
        {
            return;
        }

        TLongList osmNodeIds = way.getNodes();
        List<EdgeIterator> createdEdges = new ArrayList<EdgeIterator>();
        // look for barriers along the way
        final int size = osmNodeIds.size();
        int lastBarrier = -1;
        for (int i = 0; i < size; i++)
        {
            final long nodeId = osmNodeIds.get(i);
            int barrierFlags = osmNodeIdToBarrierMap.get(nodeId);
            // barrier was spotted and way is otherwise passable for that mode of travel
            if (barrierFlags > 0 && (barrierFlags & flags) > 0)
            {
                // remove barrier to avoid duplicates
                osmNodeIdToBarrierMap.put(nodeId, 0);

                // create shadow node copy for zero length edge
                long newNodeId = helper.addBarrierNode(nodeId);

                if (i > 0)
                {
                    // start at beginning of array if there was no previous barrier
                    if (lastBarrier < 0)
                    {
                        lastBarrier = 0;
                    }
                    // add way up to barrier shadow node
                    long transfer[] = osmNodeIds.toArray(lastBarrier, i - lastBarrier + 1);
                    transfer[ transfer.length - 1] = newNodeId;
                    TLongList partIds = new TLongArrayList(transfer);
                    createdEdges.addAll(helper.addOSMWay(partIds, flags));

                    // create zero length edge for barrier
                    createdEdges.addAll(helper.addBarrierEdge(newNodeId, nodeId, flags, barrierFlags));
                } else
                {
                    // run edge from real first node to shadow node
                    createdEdges.addAll(helper.addBarrierEdge(nodeId, newNodeId, flags, barrierFlags));

                    // exchange first node for created barrier node
                    osmNodeIds.set(0, newNodeId);
                }
                // remember barrier for processing the way behind it
                lastBarrier = i;
            }
        }

        // just add remainder of way to graph if barrier was not the last node
        if (lastBarrier >= 0)
        {
            if (lastBarrier < size - 1)
            {
                long transfer[] = osmNodeIds.toArray(lastBarrier, size - lastBarrier);
                TLongList partNodeIds = new TLongArrayList(transfer);
                createdEdges.addAll(helper.addOSMWay(partNodeIds, flags));
            }
        } else
        {
            // no barriers - simply add the whole way
            createdEdges.addAll(helper.addOSMWay(way.getNodes(), flags));
        }

        if (enableInstructions)
        {

            // http://wiki.openstreetmap.org/wiki/Key:name
            String name = fixWayName(way.getTag("name"));
            // http://wiki.openstreetmap.org/wiki/Key:ref
            String refName = fixWayName(way.getTag("ref"));
            if (!Helper.isEmpty(refName))
            {
                if (Helper.isEmpty(name))
                {
                    name = refName;
                } else
                {
                    name += ", " + refName;
                }
            }

            for (EdgeIterator iter : createdEdges)
            {
                iter.setName(name);
            }
        }
    }

    static String fixWayName( String str )
    {
        if (str == null)
        {
            return "";
        }
        return str.replaceAll(";[ ]*", ", ");
    }

    private void processNode( OSMNode node ) throws XMLStreamException
    {

        if (isInBounds(node))
        {
            helper.addNode(node);

            // analyze node tags for barriers
            if (node.hasTags())
            {
                final int barrierFlags = encodingManager.analyzeNode(node);
                if (barrierFlags != 0)
                {
                    osmNodeIdToBarrierMap.put(node.getId(), barrierFlags);
                }
            }

            locations++;
        } else
        {
            skippedLocations++;
        }
    }

    /**
     * Filter method, override in subclass
     */
    protected boolean isInBounds( OSMNode node )
    {
        return true;
    }

    public GraphStorage getGraph()
    {
        return graphStorage;
    }

    /**
     * Specify the type of the path calculation (car, bike, ...).
     */
    public OSMReader setEncodingManager( EncodingManager acceptWay )
    {
        this.encodingManager = acceptWay;
        return this;
    }

    OSMReaderHelper getHelper()
    {
        return helper;
    }

    public OSMReader setWayPointMaxDistance( double maxDist )
    {
        helper.setWayPointMaxDistance(maxDist);
        return this;
    }

    public OSMReader setEnableInstructions( boolean enableInstructions )
    {
        this.enableInstructions = enableInstructions;
        return this;
    }
}
