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

import de.jetsli.graph.util.CalcDistance;
import de.jetsli.graph.dijkstra.DijkstraBidirection;
import de.jetsli.graph.dijkstra.DijkstraPath;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.storage.Location2IDIndex;
import de.jetsli.graph.storage.Location2IDQuadtree;
import de.jetsli.graph.util.Helper;
import de.jetsli.graph.util.StopWatch;
import gnu.trove.list.array.TIntArrayList;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.Map.Entry;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * $ ulimit -v unlimited $ export JAVA_OPTS="-Xmx1000m" mvn -o -e exec:java
 * -Dexec.mainClass="de.jetsli.graph.dijkstra.OSM" -Dexec.args="/media/SAMSUNG/germany.osm"
 *
 * @author Peter Karich, info@jetsli.de
 */
public class OSMReaderRouting {

    public static void main(String[] args) throws Exception {
        new OSMReaderRouting("/tmp/mmap2-graph", 50 * 1000 * 1000) {

            @Override
            public boolean isInBounds(double lat, double lon) {
                // regardless of bounds it takes ~7min (nodes) and 5min (edges) for MMyGraphStorage and other fast storages
                return true;

                // ~germany
                // 90  mio nodes, but only 33 mio involved in routing
                // 100 mio edges
                // return true;
                //
                // ~bavaria
                // ? mio nodes
                // ? mio edges
//                return lat > 47.1 && lat < 50.5 && lon > 9 && lon < 13.5;

                //
                // ~bayreuth+bamberg+erlangen+nueremberg
                // 2.7 mio nodes
                // 2.9 mio edges
                // mean edges/node: 2.0752556
                // number of edges per node:
                // 1        2       3       4       5       6       7       8
                // 52206    35005   2232056 267471  32157   1345    164     25
//                return lat > 49.3 && lat < 50 && lon > 10.8 && lon < 11.6;
            }
        }.read(args);
    }
    private int maxLocs;
    private Logger logger = LoggerFactory.getLogger(getClass());
    private int locations = 0;
    private int skippedLocations = 0;
    private int nextEdgeIndex = 0;
    private int skippedEdges = 0;
    private Storage storage;
    private TIntArrayList tmpLocs = new TIntArrayList(10);
    private CalcDistance callback = new CalcDistance();

    public static Graph defaultRead(String osmFile, String mmapFile) throws FileNotFoundException {
        OSMReaderRouting osm = new OSMReaderRouting(mmapFile, 5 * 1000 * 1000);
        if (!osm.loadExisting())
            osm.createNewFromOSM(new File(osmFile));

        return osm.readGraph();
    }

    public void read(String[] args) throws Exception {
        // get osm file via wget -O muenchen.osm "http://api.openstreetmap.org/api/0.6/map?bbox=11.54,48.14,11.543,48.145"
        // or if that does not work for you get them here
        // http://download.geofabrik.de/osm/europe/germany/bayern/
        if (args.length < 1)
            throw new IllegalStateException(".osm file missing");

        // I'm having the osm on an external usb drive serving the osm async to our binary file
        File osmFile = new File(args[0]);
        if (!osmFile.exists())
            throw new IllegalStateException("File does not exist:" + args[0]);

        if (!loadExisting()) {
            logger.info("Start creating graph from " + osmFile);
            createNewFromOSM(osmFile);
        }

        // 2 time:0.615 from (231131)50.12328715186286, 10.751714243483567 to (698045)50.25819959971576, 10.62887384412318
        // 2012-06-14 09:11:37,514 [main] WARN  de.jetsli.graph.reader.OSMReaderRouting$1 - nothing found for 2 !?

//        boolean dijkstraSearchTest = storage instanceof MMyGraphStorage;
        boolean dijkstraSearchTest = true;
        if (dijkstraSearchTest) {
            Graph g = ((MMapGraphStorage) storage).getGraph();
            Location2IDIndex index = new Location2IDQuadtree(g).prepareIndex(20000);

            double minLat = 49.484186, minLon = 8.974228;
            double maxLat = 50.541363, maxLon = 10.880356;
            Random rand = new Random(123);
            StopWatch sw = new StopWatch();
            for (int i = 0; i < 1000; i++) {
                double fromLat = rand.nextDouble() * (maxLat - minLat) + minLat;
                double fromLon = rand.nextDouble() * (maxLon - minLon) + minLon;
                int from = index.findID(fromLat, fromLon);
                double toLat = rand.nextDouble() * (maxLat - minLat) + minLat;
                double toLon = rand.nextDouble() * (maxLon - minLon) + minLon;
                int to = index.findID(toLat, toLon);
//                logger.info(i + " " + sw + " from (" + from + ")" + fromLat + ", " + fromLon + " to (" + to + ")" + toLat + ", " + toLon);
                if (from == to) {
                    logger.warn("skipping i " + i + " from==to " + from);
                    continue;
                }

                sw.start();
                DijkstraPath p = new DijkstraBidirection(g).calcShortestPath(from, to);
                sw.stop();
                if (p == null) {
                    logger.warn("nothing found for " + i + " !? "
                            + "from " + g.getLatitude(from) + ", " + g.getLongitude(from)
                            + " to " + g.getLatitude(to) + ", " + g.getLongitude(to));
                    continue;
                }
                if (i % 100 == 0)
                    logger.info(i + " " + sw.getSeconds() / (i + 1) + " path:" + p.locations() + " " + p.toString());
            }
        }
        close();
    }

    public OSMReaderRouting(String file, int size) {
        maxLocs = size;
//            storage = new LuceneStorage().init();
//            storage = new Neo4JStorage(file).init();        
        storage = new MMapGraphStorage(file, maxLocs);
    }

    /**
     * @return the number of already existing nodes
     */
    public boolean loadExisting() {
        logger.info("starting with " + Helper.getBeanMemInfo());
        return storage.loadExisting();
    }

    /**
     * Preprocessing of OSM file to select nodes which are used for highways. This allows a more
     * compact graph data structure.
     */
    public void acceptHighwaysOnly(InputStream is) {
        if (is == null)
            throw new IllegalStateException("Stream cannot be empty");

        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLStreamReader sReader = null;
        try {
            sReader = factory.createXMLStreamReader(is, "UTF-8");
            int counter = 0;
            for (int event = sReader.next(); event != XMLStreamConstants.END_DOCUMENT;
                    event = sReader.next()) {
                if (++counter % 5000000 == 0) {
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
            try {
                if (sReader != null)
                    sReader.close();
            } catch (XMLStreamException ex) {
                throw new RuntimeException("Couldn't close file", ex);
            }
        }
    }

    /**
     * Writes two files from the inputstream which is the standard osm xml file.
     */
    public void writeOsm2Binary(InputStream is) {
        if (is == null)
            throw new IllegalStateException("Stream cannot be empty");
        logger.info("now init storage with " + storage.getNodes() + " nodes");
        storage.createNew();
        logger.info("now starting 2nd parsing");
        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLStreamReader sReader = null;
        try {
            sReader = factory.createXMLStreamReader(is, "UTF-8");
            int counter = 0;
            for (int event = sReader.next(); event != XMLStreamConstants.END_DOCUMENT;
                    event = sReader.next()) {

                switch (event) {
                    case XMLStreamConstants.START_ELEMENT:
                        if ("node".equals(sReader.getLocalName())) {
                            processNode(sReader);
                            if (++counter % 5000000 == 0) {
                                logger.info(counter + ", locs:" + locations + " (" + skippedLocations + "), edges:" + nextEdgeIndex
                                        + " (" + skippedEdges + "), " + Helper.getMemInfo());
                            }
                        } else if ("way".equals(sReader.getLocalName())) {
                            processHighway(sReader);
                            if (++counter % 100000 == 0) {
                                logger.info(counter + ", locs:" + locations + " (" + skippedLocations + "), edges:" + nextEdgeIndex
                                        + " (" + skippedEdges + "), " + Helper.getMemInfo());
                            }
                        }

                        break;
                }
            }
            storage.flush();
        } catch (XMLStreamException ex) {
            throw new RuntimeException("Couldn't process file", ex);
        } finally {
            try {
                if (sReader != null)
                    sReader.close();
            } catch (XMLStreamException ex) {
                throw new RuntimeException("Couldn't close file", ex);
            }
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

        try {
            double lat = Double.parseDouble(sReader.getAttributeValue(null, "lat"));
            double lon = Double.parseDouble(sReader.getAttributeValue(null, "lon"));
            if (isInBounds(lat, lon)) {
                if (hasHighways) {
                    // quadTree.put(lat, lon, osmId);
                    storage.addNode(osmId, lat, lon);
                    locations++;
                }
            } else {
                // remove the osmId, to force the method storage.addEdge returns false in processHighway !!
                if (hasHighways)
                    storage.setHasHighways(osmId, false);

                skippedLocations++;
            }
        } catch (Exception ex) {
            logger.error("cannot handle lon/lat of node " + osmId + ": "
                    + sReader.getAttributeValue(null, "lon") + " " + sReader.getAttributeValue(null, "lat"), ex);
        }
    }

    public boolean isInBounds(double lat, double lon) {
        return true;
    }

    boolean isHighway(XMLStreamReader sReader) throws XMLStreamException {
        tmpLocs.clear();
        boolean isHighway = false;
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
                } //                else if ("tag".equals(sReader.getLocalName())
                //                        && "name".equals(sReader.getAttributeValue(null, "k")))
                //                    roadName = sReader.getAttributeValue(null, "v");
                else if ("tag".equals(sReader.getLocalName())) {
                    String someKey = sReader.getAttributeValue(null, "k");
                    if (someKey != null && !someKey.isEmpty()) {
                        if ("highway".equals(someKey))
                            isHighway = true;
                    }
                }

                sReader.next();
            }
        } // for
        return isHighway;
    }

    public void processHighway(XMLStreamReader sReader) throws XMLStreamException {
        boolean isHighway = isHighway(sReader);
        if (isHighway && tmpLocs.size() > 1) {
            int prevOsmId = tmpLocs.get(0);
            int l = tmpLocs.size();
            for (int index = 1; index < l; index++) {
                int currOsmId = tmpLocs.get(index);
                boolean ret = storage.addEdge(prevOsmId, currOsmId, true, callback);
                if (ret)
                    nextEdgeIndex++;
                else
                    skippedEdges++;
                prevOsmId = currOsmId;
            }
        }
    }

    public OSMReaderRouting setMaxLocations(int maxLocs) {
        this.maxLocs = maxLocs;
        return this;
    }

    public Graph readGraph() {
        if (storage instanceof MMapGraphStorage)
            return ((MMapGraphStorage) storage).getGraph();

        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void close() {
        try {
            storage.close();
        } catch (Exception ex) {
            throw new RuntimeException("Cannot close storage", ex);
        }
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

    public void createNewFromOSM(File osmFile) throws FileNotFoundException {
        acceptHighwaysOnly(new FileInputStream(osmFile));
        writeOsm2Binary(new FileInputStream(osmFile));
    }

    // used from http://wiki.openstreetmap.org/wiki/OpenRouteService#Used_OSM_Tags_for_Routing
    // http://wiki.openstreetmap.org/wiki/Map_Features#Highway
    public static class CarSpeed {

        int defaultSpeed = 50;
        // autobahn
        int motorway = 110;
        int motorway_link = 90;
        // bundesstraße
        int trunk = 90;
        int trunk_link;
        // linking bigger town
        int primary = 70;
        int primary_link = 60;
        // linking smaller towns + villages
        int secondary = 60;
        int secondary_link = 50;
        // streets without middle line separation (too small)
        int tertiary = 55;
        int tertiary_link = 45;
        int unclassified = 50;
        int residential = 40;
        // spielstraße
        int living_street = 10;
        int service = 30;
    }
}
