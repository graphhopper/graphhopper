/*
 *  Licensed to Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  Peter Karich licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except 
 *  in compliance with the License. You may obtain a copy of the 
 *  License at
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
import com.graphhopper.util.GraphUtility;
import com.graphhopper.util.Helper;
import static com.graphhopper.util.Helper.*;
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
 * This class parses an OSM xml file and creates a graph from it. See run.sh on
 * how to use it from command line.
 *
 * @author Peter Karich,
 */
public class OSMReader {

    public static void main(String[] strs) throws Exception {
        CmdArgs args = CmdArgs.read(strs);
        OSMReader reader = osm2Graph(args);
        Graph g = reader.graph();
        RoutingAlgorithmSpecialAreaTests tests = new RoutingAlgorithmSpecialAreaTests(reader);
        if (args.getBool("osmreader.test", false))
            tests.start();

        if (args.getBool("osmreader.runshortestpath", false)) {
            RoutingAlgorithm algo = Helper.createAlgoFromString(g, args.get("osmreader.algo", "dijkstra"));
            int iters = args.getInt("osmreader.algoIterations", 50);
            //warmup
            tests.runShortestPathPerf(iters / 10, algo);
            tests.runShortestPathPerf(iters, algo);
        }
    }
    private static Logger logger = LoggerFactory.getLogger(OSMReader.class);
    private long locations;
    private long skippedLocations;
    private long edgeCount;
    private GraphStorage graphStorage;
    private OSMReaderHelper helper;
    private long expectedNodes;
    private TLongArrayList tmpLocs = new TLongArrayList(10);
    private Map<String, Object> properties = new HashMap<String, Object>();
    private AcceptStreet acceptStreets = new AcceptStreet(true, false, false, false);
    private AlgorithmPreparation prepare;
    private Location2IDQuadtree index;
    private int indexCapacity = -1;
    private boolean sortGraph = false;

    /**
     * Opens or creates a graph. The specified args need a property 'graph' (a
     * folder) and if no such folder exist it'll create a graph from the
     * provided osm file (property 'osm'). A property 'size' is used to
     * preinstantiate a datastructure/graph to avoid over-memory allocation or
     * reallocation (default is 5mio)
     */
    public static OSMReader osm2Graph(final CmdArgs args) throws IOException {
        if (!args.get("config", "").isEmpty()) {
            CmdArgs tmp = CmdArgs.readFromConfig(args.get("config", ""));
            // overwrite command line configuration
            args.merge(tmp);
        }
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
            boolean removeZipped = args.getBool("osmreader.graph.removeZipped", true);
            Helper.unzip(compressed.getAbsolutePath(), graphLocation, removeZipped);
        }

        long size = args.getLong("osmreader.size", 10 * 1000);
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

        String chShortcuts = args.get("osmreader.chShortcuts", "no");
        boolean levelGraph = "true".equals(chShortcuts)
                || "fastest".equals(chShortcuts) || "shortest".equals(chShortcuts);
        if (levelGraph)
            // necessary for simple or CH shortcuts
            storage = new LevelGraphStorage(dir);
        else
            storage = new GraphStorage(dir);
        return osm2Graph(new OSMReader(storage, size), args);
    }

    /**
     * Initializes the specified osmReader with arguments from the args object.
     */
    public static OSMReader osm2Graph(OSMReader osmReader, CmdArgs args) throws IOException {
        osmReader.indexCapacity(args.getInt("osmreader.locationIndexCapacity", -1));
        String type = args.get("osmreader.type", "CAR");
        osmReader.acceptStreet(new AcceptStreet(type.contains("CAR"),
                type.contains("PUBLIC_TRANSPORT"),
                type.contains("BIKE"), type.contains("FOOT")));
        final String algoStr = args.get("osmreader.algo", "astar");
        osmReader.defaultAlgoPrepare(Helper.createAlgoPrepare(algoStr));
        osmReader.sort(args.getBool("osmreader.sortGraph", false));
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
        logger.info("graph " + osmReader.graph().toString());
        return osmReader;
    }

    public OSMReader(String storageLocation, long expectedSize) {
        this(new GraphStorage(new RAMDirectory(storageLocation, true)), expectedSize);
    }

    public OSMReader(GraphStorage storage, long expectedNodes) {
        this.graphStorage = storage;
        this.expectedNodes = expectedNodes;
        this.helper = createDoubleParseHelper();
        logger.info("using " + helper.getStorageInfo(storage) + ", memory:" + Helper.getMemInfo());
    }

    boolean loadExisting() {
        if (!graphStorage.loadExisting())
            return false;

        // init
        location2IDIndex();
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

    void osm2Graph(File osmXmlFile) throws IOException {
        helper.preProcess(createInputStream(osmXmlFile));
        writeOsm2Graph(createInputStream(osmXmlFile));
        cleanUp();
        optimize();
        flush();
    }

    void optimize() {
        logger.info("optimizing ... (" + Helper.getMemInfo() + ")");
        graphStorage.optimize();
        logger.info("finished optimize (" + Helper.getMemInfo() + ")");

        // move this into the GraphStorage.optimize method?
        if (sortGraph) {
            logger.info("sorting ... (" + Helper.getMemInfo() + ")");
            GraphStorage newGraph = GraphUtility.newStorage(graphStorage);
            GraphUtility.sortDFS(graphStorage, newGraph);
            graphStorage = newGraph;
        }

        logger.info("calling prepare.doWork ... (" + Helper.getMemInfo() + ")");
        if (prepare == null)
            defaultAlgoPrepare(Helper.createAlgoPrepare("astar"));
        else
            prepare.doWork();
    }

    private void cleanUp() {
        helper.cleanup();
        int prev = graphStorage.nodes();
        PrepareRoutingSubnetworks preparation = new PrepareRoutingSubnetworks(graphStorage);
        logger.info("start finding subnetworks, " + Helper.getMemInfo());
        preparation.doWork();
        int n = graphStorage.nodes();
        logger.info("nodes " + n + ", there were " + preparation.subNetworks()
                + " sub-networks. removed them => " + (prev - n)
                + " less nodes. Remaining subnetworks:" + preparation.findSubnetworks().size());
    }

    void flush() {
        logger.info("flushing graph with " + graphStorage.nodes() + " nodes ... (" + Helper.getMemInfo() + ")");
        graphStorage.flush();

        if (indexCapacity < 0)
            indexCapacity = Helper.calcIndexSize(graphStorage.bounds());
        logger.info("initializing and flushing location index with " + indexCapacity + " bounds:" + graphStorage.bounds());
        location2IDIndex().prepareIndex(indexCapacity);
        index.flush();
    }

    /**
     * Creates the edges and nodes files from the specified inputstream (osm xml
     * file).
     */
    void writeOsm2Graph(InputStream is) {
        if (is == null)
            throw new IllegalStateException("Stream cannot be empty");

        // detected nodes means inclusive pillar nodes where we don't need to reserver space for
        int tmp = (int) (helper.expectedNodes() / 50);
        if (tmp < 0 || helper.expectedNodes() == 0)
            throw new IllegalStateException("Expected nodes not in bounds: " + nf(helper.expectedNodes()));

        logger.info("creating graph with expected nodes:" + nf(helper.expectedNodes()));
        graphStorage.createNew(tmp);
        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLStreamReader sReader = null;
        long wayStart = -1;
        StopWatch sw = new StopWatch();
        try {
            sReader = factory.createXMLStreamReader(is, "UTF-8");
            long counter = 1;
            for (int event = sReader.next(); event != XMLStreamConstants.END_DOCUMENT;
                    event = sReader.next(), counter++) {

                switch (event) {
                    case XMLStreamConstants.START_ELEMENT:
                        if ("node".equals(sReader.getLocalName())) {
                            processNode(sReader);
                            if (counter % 10000000 == 0) {
                                logger.info(nf(counter) + ", locs:" + nf(locations)
                                        + " (" + skippedLocations + "), edges:" + nf(edgeCount)
                                        + " " + Helper.getMemInfo());
                            }
                        } else if ("way".equals(sReader.getLocalName())) {
                            if (wayStart < 0) {
                                helper.startWayProcessing();
                                logger.info(nf(counter) + ", now parsing ways");
                                wayStart = counter;
                                sw.start();
                            }
                            processHighway(sReader);
                            if (counter - wayStart == 10000 && sw.stop().getSeconds() > 1) {
                                logger.warn("Something is wrong! Processing ways takes too long! "
                                        + sw.getSeconds() + "sec for only " + (counter - wayStart) + " docs");
                            }
                            // hmmh a bit hacky: counter does +=2 until the next loop
                            if ((counter / 2) % 1000000 == 0) {
                                logger.info(nf(counter) + ", locs:" + nf(locations)
                                        + " (" + skippedLocations + "), edges:" + nf(edgeCount)
                                        + " " + Helper.getMemInfo());
                            }
                        }

                        break;
                }
            }
            // logger.info("storage nodes:" + storage.nodes() + " vs. graph nodes:" + storage.getGraph().nodes());
        } catch (XMLStreamException ex) {
            throw new RuntimeException("Couldn't process file", ex);
        } finally {
            Helper7.close(sReader);
        }
    }

    private void processNode(XMLStreamReader sReader) throws XMLStreamException {
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

    boolean isInBounds(double lat, double lon) {
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
                        tmpLocs.add(Long.parseLong(ref));
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

    boolean isHighway(XMLStreamReader sReader) throws XMLStreamException {
        return parseWay(tmpLocs, properties, sReader);
    }

    private void processHighway(XMLStreamReader sReader) throws XMLStreamException {
        if (isHighway(sReader) && tmpLocs.size() > 1) {
            int flags = acceptStreets.toFlags(properties);
            int successfullAdded = helper.addEdge(tmpLocs, flags);
            edgeCount += successfullAdded;
        }
    }

    /**
     * @return the initialized graph. Invalid if called before osm2Graph.
     */
    public Graph graph() {
        return graphStorage;
    }

    /**
     * Specify the type of the path calculation (car, bike, ...).
     */
    public OSMReader acceptStreet(AcceptStreet acceptStr) {
        this.acceptStreets = acceptStr;
        return this;
    }

    public AlgorithmPreparation preparation() {
        return prepare;
    }

    /**
     * Specifies if shortcuts should be introduced (contraction hierarchies) to
     * improve query speed.
     *
     * @param chShortcuts fastest, shortest or false
     */
    public OSMReader setCHShortcuts(String chShortcuts) {
        if (chShortcuts.isEmpty() || "no".equals(chShortcuts) || "false".equals(chShortcuts))
            return this;
        if ("true".equals(chShortcuts) || "fastest".equals(chShortcuts)) {
            prepare = new PrepareContractionHierarchies().type(FastestCarCalc.DEFAULT);
        } else if ("shortest".equals(chShortcuts)) {
            prepare = new PrepareContractionHierarchies();
        } else
            throw new IllegalArgumentException("Value " + chShortcuts + " not valid for configuring "
                    + "contraction hierarchies algorithm preparation");
        prepare.graph(graphStorage);
        return this;
    }

    private OSMReader defaultAlgoPrepare(AlgorithmPreparation defaultPrepare) {
        prepare = defaultPrepare;
        prepare.graph(graphStorage);
        return this;
    }

    OSMReader setDoubleParse(boolean doubleParse) {
        if (doubleParse) {
            helper = createDoubleParseHelper();
        } else
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

    OSMReaderHelper helper() {
        return helper;
    }

    public Location2IDIndex location2IDIndex() {
        if (index == null)
            index = new Location2IDQuadtree(graphStorage, graphStorage.directory());
        return index;
    }

    /**
     * Changes the default (and automatically calculated) index size of the
     * location2id index to the specified value.
     */
    public OSMReader indexCapacity(int val) {
        indexCapacity = val;
        return this;
    }

    /**
     * Sets if the graph should be sorted to improve query speed. Often not
     * appropriated if graph is huge as sorting is done via copying into a new
     * instance.
     */
    public OSMReader sort(boolean bool) {
        sortGraph = bool;
        return this;
    }
}
