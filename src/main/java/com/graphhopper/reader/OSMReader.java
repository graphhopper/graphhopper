/*
 *  Copyright 2012 Peter Karich 
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
package com.graphhopper.reader;

import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.util.AcceptStreet;
import com.graphhopper.routing.util.AlgorithmPreparation;
import com.graphhopper.routing.util.FastestCarCalc;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.util.PrepareTowerNodesShortcuts;
import com.graphhopper.routing.util.PrepareRoutingSubnetworks;
import com.graphhopper.routing.util.RoutingAlgorithmSpecialAreaTests;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.MMapDirectory;
import com.graphhopper.storage.LevelGraphStorage;
import com.graphhopper.storage.Location2IDIndex;
import com.graphhopper.storage.Location2IDQuadtree;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.GraphUtility;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Helper7;
import com.graphhopper.util.StopWatch;
import gnu.trove.list.array.TLongArrayList;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class parses an OSM xml file and creates a graph from it. See run.sh on how to use it from
 * command line.
 *
 * @author Peter Karich,
 */
public class OSMReader {

    public static void main(String[] strs) throws Exception {
        CmdArgs args = CmdArgs.read(strs);
        if (!args.get("config", "").isEmpty()) {
            args = CmdArgs.readFromConfig(args.get("config", ""));
            // overwrite from command line
            args.merge(CmdArgs.read(strs));
        }

        OSMReader reader = osm2Graph(args);
        Graph g = reader.getGraph();
        // only possible for smaller graphs as we need to have two graphs + an array laying around
        // g = GraphUtility.sortDFS(g, new RAMDirectory());
        RoutingAlgorithmSpecialAreaTests tests = new RoutingAlgorithmSpecialAreaTests(reader);
        if (args.getBool("osmreader.test", false)) {
            tests.start();
        }

        if (args.getBool("osmreader.runshortestpath", false)) {
            RoutingAlgorithm algo = Helper.createAlgoFromString(g, args.get("osmreader.algo", "dijkstra"));
            int iters = args.getInt("osmreader.algoIterations", 50);
            //warmup
            tests.runShortestPathPerf(iters / 10, algo);
            tests.runShortestPathPerf(iters, algo);
        }
    }
    private static Logger logger = LoggerFactory.getLogger(OSMReader.class);
    private int locations;
    private int skippedLocations;
    private int nextEdgeIndex;
    private int skippedEdges;
    private GraphStorage graphStorage;
    private OSMReaderHelper helper;
    private int expectedNodes;
    private TLongArrayList tmpLocs = new TLongArrayList(10);
    private Map<String, Object> properties = new HashMap<String, Object>();
    private DistanceCalc callback = new DistanceCalc();
    private AcceptStreet acceptStreets = new AcceptStreet(true, false, false, false);
    private AlgorithmPreparation prepare;
    private Location2IDQuadtree index;
    private int indexCapacity = 2000;
    private boolean sortGraph = false;

    /**
     * Opens or creates a graph. The specified args need a property 'graph' (a folder) and if no
     * such folder exist it'll create a graph from the provided osm file (property 'osm'). A
     * property 'size' is used to preinstantiate a datastructure/graph to avoid over-memory
     * allocation or reallocation (default is 5mio)
     */
    public static OSMReader osm2Graph(final CmdArgs args) throws IOException {
        String graphLocation = args.get("osmreader.graph-location", "");
        if (Helper.isEmpty(graphLocation)) {
            String strOsm = args.get("osmreader.osm", "");
            if (Helper.isEmpty(strOsm))
                graphLocation = "graph-gh";
            else
                graphLocation = Helper.pruneFileEnd(strOsm) + "-gh";
        }

        File compressed = new File(graphLocation + ".gh");
        if (compressed.exists() && !compressed.isDirectory()) {
            boolean deleteZipped = args.getBool("osmreader.graph.removeZipped", true);
            Helper.unzip(compressed.getAbsolutePath(), graphLocation, deleteZipped);
        }

        int size = (int) args.getLong("osmreader.size", 10 * 1000);
        GraphStorage storage;
        String dataAccess = args.get("osmreader.dataaccess", "inmemory+save");
        Directory dir;
        if ("mmap".equalsIgnoreCase(dataAccess)) {
            dir = new MMapDirectory(graphLocation);
        } else {
            if ("inmemory+save".equalsIgnoreCase(dataAccess))
                dir = new RAMDirectory(graphLocation, true);
            else
                dir = new RAMDirectory(graphLocation, false);
        }

        if (args.getBool("osmreader.levelgraph", false))
            storage = new LevelGraphStorage(dir);
        else
            // necessary for simple or CH shortcuts
            storage = new GraphStorage(dir);
        return osm2Graph(new OSMReader(storage, size), args);
    }

    public static OSMReader osm2Graph(OSMReader osmReader, CmdArgs args) throws IOException {
        osmReader.setIndexCapacity(args.getInt("osmreader.locationIndexCapacity", 2000));
        String type = args.get("osmreader.type", "CAR");
        osmReader.setAcceptStreet(new AcceptStreet(type.contains("CAR"),
                type.contains("PUBLIC_TRANSPORT"),
                type.contains("BIKE"), type.contains("FOOT")));
        final String algoStr = args.get("osmreader.algo", "astar");
        osmReader.setDefaultAlgoPrepare(Helper.createAlgoPrepare(algoStr));
        osmReader.setSort(args.getBool("osmreader.sortGraph", false));
        osmReader.setTowerNodeShortcuts(args.getBool("osmreader.towerNodesShortcuts", false));
        osmReader.setCHShortcuts(args.get("osmreader.chShortcuts", "no"));
        if (!osmReader.loadExisting()) {
            String strOsm = args.get("osmreader.osm", "");
            if (Helper.isEmpty(strOsm))
                throw new IllegalArgumentException("Graph not found and no OSM xml provided.");

            File osmXmlFile = new File(strOsm);
            if (!osmXmlFile.exists())
                throw new IllegalStateException("Your specified OSM file does not exist:" + strOsm);
            logger.info("start creating graph from " + osmXmlFile);
            osmReader.osm2Graph(osmXmlFile);
        }
        logger.info("graph " + osmReader.getGraph().toString());
        return osmReader;
    }

    public OSMReader(String storageLocation, int size) {
        this(new GraphStorage(new RAMDirectory(storageLocation, true)), size);
    }

    public OSMReader(GraphStorage storage, int expectedNodes) {
        this.graphStorage = storage;
        this.expectedNodes = expectedNodes;
        this.helper = createDoubleParseHelper();
        logger.info("using " + helper.getStorageInfo(storage) + ", memory:" + Helper.getMemInfo());
    }

    public boolean loadExisting() {
        if (!graphStorage.loadExisting())
            return false;

        // init
        getLocation2IDIndex();
        // load index afterwards
        if (!index.loadExisting())
            throw new IllegalStateException("couldn't load location index");
        return true;
    }

    private InputStream createInputStream(File file) throws IOException {
        FileInputStream fi = new FileInputStream(file);
        if (file.getAbsolutePath().endsWith(".gz"))
            return new GZIPInputStream(fi);
        else if (file.getAbsolutePath().endsWith(".zip"))
            return new ZipInputStream(fi);

        return fi;
    }

    public AlgorithmPreparation getPreparation() {
        return prepare;
    }

    public void osm2Graph(File osmXmlFile) throws IOException {
        helper.preProcess(createInputStream(osmXmlFile));
        writeOsm2Graph(createInputStream(osmXmlFile));
        cleanUp();
        optimize();
        flush();
    }

    public void optimize() {
        logger.info("optimizing ... (" + Helper.getMemInfo() + ")");
        if (prepare == null)
            setDefaultAlgoPrepare(Helper.createAlgoPrepare("astar"));
        else
            prepare.doWork();
        graphStorage.optimize();
        // move this into the GraphStorage.optimize method?
        if (sortGraph) {
            logger.info("sorting ... (" + Helper.getMemInfo() + ")");
            GraphStorage newGraph = GraphUtility.newStorage(graphStorage);
            GraphUtility.sortDFS(graphStorage, newGraph);
            graphStorage = newGraph;
        }
    }

    public void cleanUp() {
        helper.cleanup();
        int prev = graphStorage.getNodes();
        PrepareRoutingSubnetworks preparation = new PrepareRoutingSubnetworks(graphStorage);
        logger.info("start finding subnetworks, " + Helper.getMemInfo());
        preparation.doWork();
        int n = graphStorage.getNodes();
        logger.info("nodes " + n + ", there were " + preparation.getSubNetworks()
                + " sub-networks. removed them => " + (prev - n)
                + " less nodes. Remaining subnetworks:" + preparation.findSubnetworks().size());
    }

    public void flush() {
        logger.info("flushing graph with " + graphStorage.getNodes() + " nodes ... (" + Helper.getMemInfo() + ")");
        graphStorage.flush();

        logger.info("now initializing and flushing index");
        getLocation2IDIndex().prepareIndex(indexCapacity);
        index.flush();
    }

    /**
     * Creates the edges and nodes files from the specified inputstream (osm xml file).
     */
    public void writeOsm2Graph(InputStream is) {
        if (is == null)
            throw new IllegalStateException("Stream cannot be empty");

        logger.info("creating graph with expected nodes:" + helper.getExpectedNodes());
        graphStorage.createNew(helper.getExpectedNodes());
        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLStreamReader sReader = null;
        int wayStart = -1;
        StopWatch sw = new StopWatch();
        try {
            sReader = factory.createXMLStreamReader(is, "UTF-8");
            int counter = 1;
            for (int event = sReader.next(); event != XMLStreamConstants.END_DOCUMENT;
                    event = sReader.next(), counter++) {

                switch (event) {
                    case XMLStreamConstants.START_ELEMENT:
                        if ("node".equals(sReader.getLocalName())) {
                            processNode(sReader);
                            if (counter % 10000000 == 0) {
                                logger.info(counter + ", locs:" + locations + " (" + skippedLocations + "), edges:" + nextEdgeIndex
                                        + " (" + skippedEdges + "), " + Helper.getMemInfo());
                            }
                        } else if ("way".equals(sReader.getLocalName())) {
                            if (wayStart < 0) {
                                helper.startWayProcessing();
                                logger.info("parsing ways");
                                wayStart = counter;
                                sw.start();
                            }
                            processHighway(sReader);
                            if (counter - wayStart == 10000 && sw.stop().getSeconds() > 1) {
                                logger.warn("Something is wrong! Processing ways takes too long! "
                                        + sw.getSeconds() + "sec for only " + (counter - wayStart) + " docs");
                            }
                            // counter does +=2 until the next loop
                            if ((counter / 2) % 1000000 == 0) {
                                logger.info(counter + ", locs:" + locations + " (" + skippedLocations + "), edges:" + nextEdgeIndex
                                        + " (" + skippedEdges + "), " + Helper.getMemInfo());
                            }
                        }

                        break;
                }
            }
            // logger.info("storage nodes:" + storage.getNodes() + " vs. graph nodes:" + storage.getGraph().getNodes());
        } catch (XMLStreamException ex) {
            throw new RuntimeException("Couldn't process file", ex);
        } finally {
            Helper7.close(sReader);
        }
    }

    public void processNode(XMLStreamReader sReader) throws XMLStreamException {
        long osmId;
        try {
            osmId = Long.parseLong(sReader.getAttributeValue(null, "id"));
        } catch (Exception ex) {
            logger.error("cannot get id from xml node:" + sReader.getAttributeValue(null, "id"), ex);
            return;
        }

        double lat = -1;
        double lon = -1;
        try {
            lat = Double.parseDouble(sReader.getAttributeValue(null, "lat"));
            lon = Double.parseDouble(sReader.getAttributeValue(null, "lon"));
            if (isInBounds(lat, lon)) {
                helper.addNode(osmId, lat, lon);
                locations++;
            } else {
                skippedLocations++;
            }
        } catch (Exception ex) {
            throw new RuntimeException("cannot handle lon/lat of node " + osmId + ": " + lat + "," + lon, ex);
        }
    }

    public boolean isInBounds(double lat, double lon) {
        return true;
    }

    boolean parseWay(TLongArrayList tmpLocs, Map<String, Object> properties, XMLStreamReader sReader)
            throws XMLStreamException {

        boolean handled = false;
        tmpLocs.clear();
        properties.clear();
        for (int tmpE = sReader.nextTag(); tmpE != XMLStreamConstants.END_ELEMENT;
                tmpE = sReader.nextTag()) {
            if (tmpE == XMLStreamConstants.START_ELEMENT) {
                if ("nd".equals(sReader.getLocalName())) {
                    String ref = sReader.getAttributeValue(null, "ref");
                    try {
                        tmpLocs.add(Integer.parseInt(ref));
                    } catch (Exception ex) {
                        logger.error("cannot get ref from way. ref:" + ref, ex);
                    }
                } else if ("tag".equals(sReader.getLocalName())) {
                    String key = sReader.getAttributeValue(null, "k");
                    if (key != null && !key.isEmpty()) {
                        String val = sReader.getAttributeValue(null, "v");
                        if ("highway".equals(key)) {
                            handled = acceptStreets.handleWay(properties, val);
//                            if ("proposed".equals(val) || "preproposed".equals(val)
//                                    || "platform".equals(val) || "raceway".equals(val)
//                                    || "bus_stop".equals(val) || "bridleway".equals(val)
//                                    || "construction".equals(val) || "no".equals(val) || "centre_line".equals(val))
//                                // ignore
//                                val = val;
//                            else
//                                logger.warn("unknown highway type:" + val);
                        } else if ("oneway".equals(key)) {
                            if ("yes".equals(val) || "true".equals(val) || "1".equals(val))
                                properties.put("oneway", "yes");
                        } else if ("junction".equals(key)) {
                            // abzweigung
                            if ("roundabout".equals(val))
                                properties.put("oneway", "yes");
                        }
                    }
                }

                sReader.next();
            }
        }
        return handled;
    }

    public boolean isHighway(XMLStreamReader sReader) throws XMLStreamException {
        return parseWay(tmpLocs, properties, sReader);
    }

    public void processHighway(XMLStreamReader sReader) throws XMLStreamException {
        if (isHighway(sReader) && tmpLocs.size() > 1) {
            int l = tmpLocs.size();
            long prevOsmId = tmpLocs.get(0);
            int flags = acceptStreets.toFlags(properties);
            for (int index = 1; index < l; index++) {
                long currOsmId = tmpLocs.get(index);
                boolean ret = helper.addEdge(prevOsmId, currOsmId, flags, callback);
                if (ret)
                    nextEdgeIndex++;
                else
                    skippedEdges++;
                prevOsmId = currOsmId;
            }
        }
    }

    public Graph getGraph() {
        return graphStorage;
    }

    public OSMReader setAcceptStreet(AcceptStreet acceptStr) {
        this.acceptStreets = acceptStr;
        return this;
    }

    public OSMReader setCHShortcuts(String chShortcuts) {
        if ("true".equals(chShortcuts) || "fastest".equals(chShortcuts)) {
            prepare = new PrepareContractionHierarchies().setType(FastestCarCalc.DEFAULT);
        } else if ("shortest".equals(chShortcuts)) {
            prepare = new PrepareContractionHierarchies();
        }
        prepare.setGraph(graphStorage);
        return this;
    }

    /**
     * @param bool if yes then shortcuts will be introduced to skip all nodes with only 2 edges.
     */
    public OSMReader setTowerNodeShortcuts(boolean bool) {
        if (bool)
            prepare = new PrepareTowerNodesShortcuts();
        return this;
    }

    private void setDefaultAlgoPrepare(AlgorithmPreparation defaultPrepare) {
        prepare = defaultPrepare;
        prepare.setGraph(graphStorage);
    }

    OSMReader setDoubleParse(boolean dp) {
        if (dp)
            helper = createDoubleParseHelper();
        else
            helper = new OSMReaderHelperSingleParse(graphStorage, expectedNodes);
        return this;
    }

    OSMReaderHelper createDoubleParseHelper() {
        return new OSMReaderHelperDoubleParse(graphStorage, expectedNodes) {
            @Override
            boolean parseWay(TLongArrayList tmpLocs, Map<String, Object> properties,
                    XMLStreamReader sReader) throws XMLStreamException {
                return OSMReader.this.parseWay(tmpLocs, properties, sReader);
            }
        };
    }

    OSMReaderHelper getHelper() {
        return helper;
    }

    public Location2IDIndex getLocation2IDIndex() {
        if (index == null)
            index = new Location2IDQuadtree(graphStorage, graphStorage.getDirectory());
        return index;
    }

    public OSMReader setIndexCapacity(int cap) {
        indexCapacity = cap;
        return this;
    }

    public OSMReader setSort(boolean bool) {
        sortGraph = bool;
        return this;
    }
}
