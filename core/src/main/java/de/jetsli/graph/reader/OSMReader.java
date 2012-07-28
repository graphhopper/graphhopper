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
package de.jetsli.graph.reader;

import de.jetsli.graph.storage.Storage;
import de.jetsli.graph.util.CalcDistance;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.storage.MemoryGraphSafeStorage;
import de.jetsli.graph.util.*;
import gnu.trove.list.array.TIntArrayList;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.Map.Entry;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * See run-ui.sh
 *
 * @author Peter Karich, info@jetsli.de
 */
public class OSMReader {

    public static void main(String[] strs) throws Exception {
        CmdArgs args = Helper.readCmdArgs(strs);
        int size = args.getInt("size", 5 * 1000 * 1000);
        String graphFolder = args.get("graph", "graph-storage");
        if (Helper.isEmpty(graphFolder))
            throw new IllegalArgumentException("Please specify a folder where to store the graph");

        OSMReader osmReader = new OSMReader(graphFolder, size) {
            @Override public boolean isInBounds(double lat, double lon) {
                // regardless of bounds it takes ~7min (nodes) and 5min (edges) for MMyGraphStorage and other fast storages
                // ~germany
                // 90  mio nodes, but only 33 mio involved in routing
                // 100 mio edges
                return true;

                // ~bayreuth+bamberg+erlangen+nueremberg
                // 2.7 mio nodes
                // 2.9 mio edges
//                return lat > 49.3 && lat < 50 && lon > 10.8 && lon < 11.6;
            }
        };
        osm2Graph(osmReader, args);
        RoutingAlgorithmIntegrationTests tests = new RoutingAlgorithmIntegrationTests(osmReader.getGraph());
        if (args.getBool("test", false)) {
            tests.start();
        } else if (args.getBool("shortestpath", false)) {
            String algo = args.get("algo", "dijkstra");
            //warmup
            tests.runShortestPathPerf(50, algo);
            tests.runShortestPathPerf(500, algo);
        }
    }
    private int expectedLocs;
    private static Logger logger = LoggerFactory.getLogger(OSMReader.class);
    private int locations = 0;
    private int skippedLocations = 0;
    private int nextEdgeIndex = 0;
    private int skippedEdges = 0;
    private Storage storage;
    private TIntArrayList tmpLocs = new TIntArrayList(10);
    private Map<String, Object> properties = new HashMap<String, Object>();
    private CalcDistance callback = new CalcDistance();

    /**
     * Opens or creates a graph. The specified args need a property 'graph' (a folder) and if no
     * such folder exist it'll create a graph from the provided osm file (property 'osm'). A
     * property 'size' is used to preinstantiate a datastructure/graph to avoid over-memory
     * allocation or reallocation (default is 5mio)
     */
    public static Graph osm2Graph(CmdArgs args) throws IOException {
        String storageFolder = args.get("graph", "graph-storage");
        if (Helper.isEmpty(storageFolder))
            throw new IllegalArgumentException("Please specify a folder where to store the graph");

        int size = (int) args.getLong("size", 5 * 1000 * 1000);
        return osm2Graph(new OSMReader(storageFolder, size), args);
    }

    public static Graph osm2Graph(OSMReader osmReader, CmdArgs args) throws IOException {
        if (!osmReader.loadExisting()) {
            String strOsm = args.get("osm", "");
            if (Helper.isEmpty(strOsm))
                throw new IllegalArgumentException("Graph not found - please specify the OSM xml file (.osm) to create the graph");

            File osmXmlFile = new File(strOsm);
            if (!osmXmlFile.exists())
                throw new IllegalStateException("Your specified OSM file does not exist:" + strOsm);
            logger.info("size for osm2id-map is " + osmReader.getMaxLocs() + " - start creating graph from " + osmXmlFile);
            osmReader.osm2Graph(osmXmlFile);
        }

        return osmReader.getGraph();
    }

    private int getMaxLocs() {
        return expectedLocs;
    }

    public OSMReader(String storageLocation, int size) {
        storage = createStorage(storageLocation, expectedLocs = size);
        logger.info("using " + storage.getClass().getSimpleName());
    }

    protected Storage createStorage(String storageLocation, int size) {
//        return new MMapGraphStorage(storageLocation, size);
//        return new MemoryGraphStorage(size);
        return new MemoryGraphSafeStorage(storageLocation, size);
    }

    public boolean loadExisting() {
        logger.info("starting with " + Helper.getBeanMemInfo());
        return storage.loadExisting();
    }

    private InputStream createInputStream(File file) throws IOException {
        FileInputStream fi = new FileInputStream(file);
        if (file.getAbsolutePath().endsWith(".gz"))
            return new GZIPInputStream(fi);
        else if (file.getAbsolutePath().endsWith(".zip"))
            return new ZipInputStream(fi);

        return fi;
    }

    public void osm2Graph(File osmXmlFile) throws IOException {
        // TODO instead of creating two separate input streams,
        // could we use PushbackInputStream(new FileInputStream(osmFile)); ???

        preprocessAcceptHighwaysOnly(createInputStream(osmXmlFile));
        writeOsm2Graph(createInputStream(osmXmlFile));
        cleanUp();
        flush();
    }

    public void cleanUp() {
        Graph g = storage.getGraph();
        int prev = g.getNodes();
        PrepareRouting preparation = new PrepareRouting(g);
        logger.info("start finding subnetworks");
        int subnetworks = preparation.doWork();
        int n = g.getNodes();
        logger.info("nodes " + n + ", there were " + subnetworks + " subnetworks. removed them => " + (prev - n)
                + " less nodes. Remaining subnetworks:" + preparation.findSubnetworks().size());
    }

    public void flush() {
        logger.info("flushing ...");
        storage.flush();
    }

    /**
     * Preprocessing of OSM file to select nodes which are used for highways. This allows a more
     * compact graph data structure.
     */
    public void preprocessAcceptHighwaysOnly(InputStream osmXml) {
        if (osmXml == null)
            throw new IllegalStateException("Stream cannot be empty");

        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLStreamReader sReader = null;
        try {
            sReader = factory.createXMLStreamReader(osmXml, "UTF-8");
            int counter = 0;
            for (int event = sReader.next(); event != XMLStreamConstants.END_DOCUMENT;
                    event = sReader.next()) {
                if (++counter % 10000000 == 0) {
                    logger.info(counter + ", locs:" + locations + " (" + skippedLocations + "), edges:" + nextEdgeIndex
                            + " (" + skippedEdges + "), " + Helper.getMemInfo());
                }
                switch (event) {
                    case XMLStreamConstants.START_ELEMENT:
                        if ("way".equals(sReader.getLocalName())) {
                            boolean isHighway = isHighway(sReader);
                            if (isHighway && tmpLocs.size() > 1) {
                                int s = tmpLocs.size();
                                for (int index = 0; index < s; index++) {
                                    storage.setHasHighways(tmpLocs.get(index), true);
                                }
                            }
                        }
                        break;
                }
            }
        } catch (XMLStreamException ex) {
            throw new RuntimeException("Problem while parsing file", ex);
        } finally {
            Helper.close(sReader);
        }
    }

    /**
     * Creates the edges and nodes files from the specified inputstream (osm xml file).
     */
    public void writeOsm2Graph(InputStream is) {
        if (is == null)
            throw new IllegalStateException("Stream cannot be empty");
        logger.info("init storage with " + storage.getNodes() + " nodes");
        storage.createNew();
        logger.info("starting 2nd parsing");
        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLStreamReader sReader = null;
        int wayStart = -1;
        StopWatch sw = new StopWatch();
        try {
            sReader = factory.createXMLStreamReader(is, "UTF-8");
            int counter = 0;
            for (int event = sReader.next(); event != XMLStreamConstants.END_DOCUMENT;
                    event = sReader.next()) {

                switch (event) {
                    case XMLStreamConstants.START_ELEMENT:
                        if ("node".equals(sReader.getLocalName())) {
                            processNode(sReader);
                            if (++counter % 10000000 == 0) {
                                logger.info(counter + ", locs:" + locations + " (" + skippedLocations + "), edges:" + nextEdgeIndex
                                        + " (" + skippedEdges + "), " + Helper.getMemInfo());
                            }
                        } else if ("way".equals(sReader.getLocalName())) {
                            if (wayStart < 0) {
                                logger.info("parsing ways");
                                wayStart = counter;
                                sw.start();
                            }
                            processHighway(sReader);
                            if (counter - wayStart == 10000 && sw.stop().getSeconds() > 1) {
                                logger.warn("Something is wrong! Processing ways takes too long! "
                                        + sw.getSeconds() + "sec for only " + (counter - wayStart) + " docs");
                            }
                            if (counter++ % 1000000 == 0) {
                                logger.info(counter + ", locs:" + locations + " (" + skippedLocations + "), edges:" + nextEdgeIndex
                                        + " (" + skippedEdges + "), " + Helper.getMemInfo());
                            }
                        }

                        break;
                }
            }
            logger.info("storage nodes:" + storage.getNodes() + " vs. graph nodes:" + storage.getGraph().getNodes());
        } catch (XMLStreamException ex) {
            throw new RuntimeException("Couldn't process file", ex);
        } finally {
            Helper.close(sReader);
        }
    }

    public void processNode(XMLStreamReader sReader) throws XMLStreamException {
        int osmId;
        boolean hasHighways;
        try {
            osmId = Integer.parseInt(sReader.getAttributeValue(null, "id"));
            hasHighways = storage.hasHighways(osmId);
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
                if (hasHighways) {
                    storage.addNode(osmId, lat, lon);
                    locations++;
                }
            } else {
                // remove the osmId if not in bounds to avoid trouble when processing the highway
                if (hasHighways)
                    storage.setHasHighways(osmId, false);

                skippedLocations++;
            }
        } catch (Exception ex) {
            throw new RuntimeException("cannot handle lon/lat of node " + osmId + ": " + lat + "," + lon, ex);
        }
    }

    public boolean isInBounds(double lat, double lon) {
        return true;
    }

    void parseWay(TIntArrayList tmpLocs, Map<String, Object> properties, XMLStreamReader sReader)
            throws XMLStreamException {

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
                            Integer integ = CarFlags.CAR_SPEED.get(val);
                            if (integ != null)
                                properties.put("car", integ);
                            else if ("footway".equals(val) || "path".equals(val) || "steps".equals(val)
                                    || "pedestrian".equals(val))
                                properties.put("foot", "yes");
                            // bridleway = reitweg
                            else if ("bridleway".equals(val) || "construction".equals(val))
                                val = val;
                            else
                                logger.warn("unknown highway type:" + val);
                        } else if ("cycleway".equals(key)) {
                            // add bike support later
                            // http://wiki.openstreetmap.org/wiki/Cycleway
                            // http://wiki.openstreetmap.org/wiki/Map_Features#Cycleway
                            // https://github.com/Tristramg/osm4routing/blob/master/parameters.cc
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
    }

    public boolean isHighway(XMLStreamReader sReader) throws XMLStreamException {
        parseWay(tmpLocs, properties, sReader);
        return properties.get("car") != null;
    }

    public void processHighway(XMLStreamReader sReader) throws XMLStreamException {
        if (isHighway(sReader) && tmpLocs.size() > 1) {
            int prevOsmId = tmpLocs.get(0);
            int l = tmpLocs.size();
            int flags = CarFlags.create(properties);
            for (int index = 1; index < l; index++) {
                int currOsmId = tmpLocs.get(index);
                boolean ret = storage.addEdge(prevOsmId, currOsmId, flags, callback);
                if (ret)
                    nextEdgeIndex++;
                else
                    skippedEdges++;
                prevOsmId = currOsmId;
            }
        }
    }

    public Graph getGraph() {
        return storage.getGraph();
    }

    private void stats() {
        logger.info("Stats");

//        printSorted(countMap.entrySet());
//        printSorted(highwayMap.entrySet());
        storage.stats();
    }

    private void printSorted(Set<Entry<String, Integer>> entrySet) {
        List<Entry<String, Integer>> list = new ArrayList<Entry<String, Integer>>(entrySet);
        Collections.sort(list, new Comparator<Entry<String, Integer>>() {
            @Override
            public int compare(Entry<String, Integer> o1, Entry<String, Integer> o2) {
                return o1.getValue() - o2.getValue();
            }
        });
        for (Entry<String, Integer> e : list) {
            logger.info(e.getKey() + "\t -> " + e.getValue());
        }
        logger.info("---");
    }
}
